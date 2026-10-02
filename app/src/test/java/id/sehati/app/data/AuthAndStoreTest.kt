package id.sehati.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.core.security.InMemorySecureStore
import id.sehati.app.data.repository.AuthResult
import id.sehati.app.data.repository.Registration
import id.sehati.app.data.session.SessionManager
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.model.Sex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthAndStoreTest {
    private lateinit var env: TestEnv
    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    private fun reg(pw: String = "rahasia1") = Registration("Tariska", "1992-03-01", Sex.FEMALE, "Desa Uji", "01", "02", "081234567890", pw, listOf("Lebih aktif"), true, false, false)

    @Test fun registerCreatesWargaSessionAndSequentialId() = blocking {
        val r = env.auth.register(reg())
        assertIs<AuthResult.Success>(r)
        assertEquals("HM-000001", r.user.sehatiId); assertEquals(Role.WARGA, r.session.role)
        assertEquals("HM-000002", (env.auth.register(reg().copy(phone = "081234567891")) as AuthResult.Success).user.sehatiId)
    }

    @Test fun registrationCannotCreateAdmin() = blocking {
        val r = env.auth.register(reg()) as AuthResult.Success
        assertEquals("WARGA", r.user.role)
    }

    @Test fun weakPasswordRejected() = blocking { assertIs<AuthResult.Failure>(env.auth.register(reg("123"))) }

    @Test fun loginWithIdOrPhoneAndWrongPasswordFails() = blocking {
        env.auth.register(reg()); env.auth.logout()
        assertIs<AuthResult.Success>(env.auth.login("HM-000001", "rahasia1"))
        assertIs<AuthResult.Success>(env.auth.login("081234567890", "rahasia1"))
        assertIs<AuthResult.Failure>(env.auth.login("HM-000001", "salah"))
    }

    @Test fun repeatedFailuresLockTheAccount() = blocking {
        env.auth.register(reg())
        repeat(5) { env.auth.login("HM-000001", "salah") }
        val locked = env.auth.login("HM-000001", "rahasia1")
        assertIs<AuthResult.Failure>(locked); assertTrue(locked.message.contains("menit"))
        env.clock.t += 6 * 60_000L
        assertIs<AuthResult.Success>(env.auth.login("HM-000001", "rahasia1"))
    }

    @Test fun passwordIsNeverStoredInPlainText() = blocking {
        env.auth.register(reg())
        val c = env.db.userDao().credential("HM-000001")!!
        assertFalse(c.hash.contains("rahasia1")); assertTrue(c.salt.isNotBlank()); assertTrue(c.iterations >= 100_000)
    }

    @Test fun sessionExpires() {
        val store = InMemorySecureStore()
        val s = SessionManager(store, env.clock)
        s.start("KD-000001", Role.KADER)
        assertNotNull(s.validate())
        env.clock.t += 13 * 3600_000L
        assertNull(s.validate()); assertNull(s.session.value)
        s.start("HM-000001", Role.WARGA); env.clock.t += 13 * 3600_000L
        assertNotNull(s.validate()) // warga: 30 hari
    }

    @Test fun sessionRestoresFromSecureStore() {
        val store = InMemorySecureStore()
        SessionManager(store, env.clock).start("AD-000001", Role.ADMIN)
        assertEquals(Role.ADMIN, SessionManager(store, env.clock).session.value?.role)
    }

    @Test fun qrResolveRequiresMatchingOpaqueToken() = blocking {
        val u = (env.auth.register(reg()) as AuthResult.Success).user
        assertNotNull(env.citizens.resolveQr(id.sehati.app.domain.rules.QrPayload.build(u.sehatiId, u.qrToken)))
        assertNull(env.citizens.resolveQr(id.sehati.app.domain.rules.QrPayload.build(u.sehatiId, "x".repeat(24))))
        assertNull(env.citizens.resolveQr("sehati://citizen/HM-999999/${u.qrToken}"))
    }
}
