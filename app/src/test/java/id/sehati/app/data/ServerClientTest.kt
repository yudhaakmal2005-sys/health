package id.sehati.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.core.security.InMemorySecureStore
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.remote.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

/** Kontrak klien ↔ server diuji dengan server tiruan (tanpa jaringan nyata). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ServerClientTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var web: MockWebServer
    private lateinit var client: ServerClient

    @Before fun setUp() {
        web = MockWebServer(); web.start()
        client = ServerClient(SettingsStore(ApplicationProvider.getApplicationContext()), InMemorySecureStore(), json, defaultBaseUrl = web.url("/").toString())
    }
    @After fun tearDown() = web.close()

    private fun login() = client.saveToken(LoginResponse("tok", System.currentTimeMillis() + 60_000, RemoteUser("HM-000127", "Tariska")))

    @Test fun mapsErrorBodyToFailure() = blocking {
        web.enqueue(MockResponse.Builder().code(401).body("""{"error":{"code":"INVALID_CREDENTIALS","message":"SEHATI ID atau kata sandi salah"}}""").build())
        val r = client.call { it.login(LoginRequest("HM-1", "x", "d")) }
        assertIs<ApiResult.Failure>(r)
        assertEquals("INVALID_CREDENTIALS", r.code)
        assertEquals("/api/v1/auth/login", web.takeRequest().url.encodedPath)
    }

    @Test fun streamsChatEvents() = blocking {
        login()
        val sse = "event: meta\ndata: {\"emergency\":false}\n\n" +
            "event: delta\ndata: {\"text\":\"Halo \"}\n\n" +
            "event: delta\ndata: {\"text\":\"warga\"}\n\n" +
            "event: done\ndata: {\"stopReason\":\"end_turn\"}\n\n"
        web.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(sse).build())
        val events = client.chat(ChatRequest(listOf(ChatTurn("user", "halo")))).toList()
        assertEquals(ChatEvent.Meta(false), events.first())
        assertEquals("Halo warga", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
        assertEquals(ChatEvent.Done("end_turn"), events.last())
        assertEquals("Bearer tok", web.takeRequest().headers["Authorization"])
    }

    @Test fun chatDisabledIsReportedAsFailure() = blocking {
        login()
        web.enqueue(MockResponse.Builder().code(503).body("""{"error":{"code":"AI_DISABLED","message":"AI belum aktif"}}""").build())
        val e = client.chat(ChatRequest(listOf(ChatTurn("user", "halo")))).toList().single()
        assertEquals(ChatEvent.Failure("AI_DISABLED", "AI belum aktif"), e)
    }

    @Test fun pullParsesTombstones() = blocking {
        login()
        web.enqueue(MockResponse.Builder().body("""{"items":[{"seq":5,"type":"medlog","entityId":"a","subjectId":"HM-000127","version":2,"deleted":true,"payload":null}],"nextCursor":5,"hasMore":false}""").build())
        val r = client.call { it.pull(0) }
        assertIs<ApiResult.Ok<PullResponse>>(r)
        assertTrue(r.value.items.single().deleted)
        assertNull(r.value.items.single().payload)
    }

    @Test fun noTokenMeansNoChat() = blocking {
        val e = client.chat(ChatRequest(listOf(ChatTurn("user", "halo")))).toList().single()
        assertIs<ChatEvent.Failure>(e)
    }
}
