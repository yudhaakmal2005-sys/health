package id.sehati.app.domain.rules

import java.security.SecureRandom
import java.time.LocalDate
import java.time.Period
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** SEHATI ID bukan pengganti NIK untuk keperluan administratif resmi. */
object SehatiId {
    private val regex = Regex("^HM-\\d{6}$")
    fun format(n: Int): String = "HM-" + n.toString().padStart(6, '0')
    fun isValid(id: String) = regex.matches(id.trim().uppercase())
    fun normalize(raw: String): String = raw.trim().uppercase().let {
        val digits = it.removePrefix("HM-").removePrefix("HM")
        if (digits.isNotEmpty() && digits.all(Char::isDigit) && digits.length <= 6) format(digits.toInt()) else it
    }
    fun number(id: String): Int? = if (isValid(id)) id.substring(3).toInt() else null
}

/** Isi QR: hanya identifier + token opak. Tanpa NIK, alamat, atau data kesehatan. */
object QrPayload {
    private const val PREFIX = "sehati://citizen/"
    data class Parsed(val sehatiId: String, val token: String)

    fun build(id: String, token: String) = "$PREFIX$id/$token"

    fun parse(raw: String): Parsed? {
        val text = raw.trim()
        if (!text.startsWith(PREFIX)) return null
        val parts = text.removePrefix(PREFIX).split('/')
        if (parts.size != 2) return null
        val id = parts[0].uppercase()
        val token = parts[1]
        if (!SehatiId.isValid(id) || token.length < 16 || !token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
        return Parsed(id, token)
    }

    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(18).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object PasswordHasher {
    private const val ITER = 120_000
    data class Hash(val salt: String, val hash: String, val iterations: Int = ITER)

    fun hash(password: CharArray, random: SecureRandom = SecureRandom(), iterations: Int = ITER): Hash {
        val salt = ByteArray(16).also(random::nextBytes)
        return Hash(Base64.getEncoder().encodeToString(salt), derive(password, salt, iterations), iterations)
    }

    fun verify(password: CharArray, stored: Hash): Boolean {
        val salt = Base64.getDecoder().decode(stored.salt)
        val candidate = derive(password, salt, stored.iterations)
        return java.security.MessageDigest.isEqual(candidate.toByteArray(), stored.hash.toByteArray())
    }

    private fun derive(pw: CharArray, salt: ByteArray, iter: Int): String {
        val spec = PBEKeySpec(pw, salt, iter, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(key)
    }

    fun passwordIssue(p: String): String? = when {
        p.length < 6 -> "Kata sandi minimal 6 karakter."
        else -> null
    }
}

object AgeCalc {
    fun age(birthIso: String, today: LocalDate = LocalDate.now()): Int = runCatching {
        Period.between(LocalDate.parse(birthIso), today).years.coerceAtLeast(0)
    }.getOrDefault(0)
}
