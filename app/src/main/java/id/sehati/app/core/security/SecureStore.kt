package id.sehati.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Penyimpanan rahasia kecil (token sesi, kunci DB) yang dienkripsi Android Keystore. */
interface SecureStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class InMemorySecureStore : SecureStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class KeystoreSecureStore(context: Context) : SecureStore {
    private val prefs = context.getSharedPreferences("sehati_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun putString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(key, packed).apply()
    }

    override fun getString(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return runCatching {
            val (iv, data) = packed.split(':')
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) { prefs.edit().remove(key).apply() }

    private companion object {
        const val ALIAS = "sehati_master_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

/** Kunci enkripsi database (SQLCipher): acak 256-bit, disimpan terenkripsi Keystore. */
class DatabaseKeyProvider(private val store: SecureStore) {
    fun passphrase(): ByteArray {
        store.getString(KEY)?.let { return it.toByteArray(Charsets.UTF_8) }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = raw.joinToString("") { "%02x".format(it) }
        store.putString(KEY, hex)
        return hex.toByteArray(Charsets.UTF_8)
    }
    private companion object { const val KEY = "db_passphrase" }
}
