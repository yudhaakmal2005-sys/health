package id.sehati.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.data.repository.NewMeasurement
import id.sehati.app.data.sync.*
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.rules.MeasurementInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

class ScriptedTransport : SyncTransport {
    enum class Mode { OFFLINE, OK, REJECT, NOT_CONFIGURED }
    var mode = Mode.OFFLINE
    val received = mutableListOf<SyncItemDto>()
    override val label = "scripted"
    override suspend fun push(request: SyncPushRequest): TransportResult = when (mode) {
        Mode.OFFLINE -> TransportResult.Offline("tidak ada jaringan")
        Mode.NOT_CONFIGURED -> TransportResult.NotConfigured("belum dikonfigurasi")
        Mode.OK -> { received += request.items; TransportResult.Success(request.items.map { SyncAckDto(it.id, AckStatus.OK, "srv-${it.entityId.takeLast(6)}") }) }
        Mode.REJECT -> TransportResult.Success(request.items.map { SyncAckDto(it.id, AckStatus.REJECTED, null, "ditolak") })
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncEngineTest {
    private lateinit var t: ScriptedTransport
    private lateinit var env: TestEnv

    @Before fun setUp() { t = ScriptedTransport(); env = TestEnv(t) }
    @After fun tearDown() { env.close() }

    private suspend fun measurement(userId: String) =
        env.health.saveMeasurement(NewMeasurement(userId, DataSource.SELF, MeasurementInput(systolic = 120, diastolic = 80)))

    @Test fun offlineKeepsDataLocalThenSyncsWhenOnline() = runBlocking {
        val u = env.citizen()
        val c = measurement(u.sehatiId)
        assertEquals("LOCAL_ONLY", env.db.healthDao().check(c.id)!!.header.syncStatus)

        assertIs<SyncOutcome.Offline>(env.engine.syncNow())
        assertEquals("LOCAL_ONLY", env.db.healthDao().check(c.id)!!.header.syncStatus) // aman di perangkat
        assertTrue(env.db.systemDao().pending(100).isNotEmpty())

        t.mode = ScriptedTransport.Mode.OK
        val out = env.engine.syncNow()
        assertIs<SyncOutcome.Done>(out); assertEquals(0, out.failed)
        val h = env.db.healthDao().check(c.id)!!.header
        assertEquals("SYNCED", h.syncStatus); assertNotNull(h.serverId)
        assertTrue(env.db.systemDao().pending(100).isEmpty())
    }

    @Test fun secondSyncDoesNotUploadAgain() = runBlocking {
        val u = env.citizen(); measurement(u.sehatiId)
        t.mode = ScriptedTransport.Mode.OK
        env.engine.syncNow()
        val sent = t.received.size
        assertTrue(sent > 0)
        assertEquals(SyncOutcome.NothingToSync, env.engine.syncNow())
        assertEquals(sent, t.received.size)
    }

    @Test fun eachMutationHasUniqueIdempotencyKey() = runBlocking {
        val u = env.citizen(); measurement(u.sehatiId); measurement(u.sehatiId)
        t.mode = ScriptedTransport.Mode.OK; env.engine.syncNow()
        assertEquals(t.received.size, t.received.map { it.id }.toSet().size)
    }

    @Test fun rejectedItemsAreMarkedFailedAndRetriedLater() = runBlocking {
        val u = env.citizen(); val c = measurement(u.sehatiId)
        t.mode = ScriptedTransport.Mode.REJECT
        val out = env.engine.syncNow()
        assertIs<SyncOutcome.Done>(out); assertTrue(out.failed > 0)
        assertEquals("SYNC_FAILED", env.db.healthDao().check(c.id)!!.header.syncStatus)
        assertTrue(env.db.systemDao().observeFailedCount().first() > 0)

        t.mode = ScriptedTransport.Mode.OK
        env.engine.syncNow()
        assertEquals("SYNCED", env.db.healthDao().check(c.id)!!.header.syncStatus)
        assertEquals(0, env.db.systemDao().observeFailedCount().first())
    }

    @Test fun dataOfCitizenWithoutConsentIsNeverUploaded() = runBlocking {
        val u = env.citizen(consentServer = false)
        val c = measurement(u.sehatiId)
        t.mode = ScriptedTransport.Mode.OK
        env.engine.syncNow()
        assertTrue(t.received.none { it.entityId == c.id })
        assertEquals("LOCAL_ONLY", env.db.healthDao().check(c.id)!!.header.syncStatus)
    }

    @Test fun notConfiguredServerKeepsEverythingLocal() = runBlocking {
        val u = env.citizen(); val c = measurement(u.sehatiId)
        t.mode = ScriptedTransport.Mode.NOT_CONFIGURED
        assertIs<SyncOutcome.NotConfigured>(env.engine.syncNow())
        assertEquals("LOCAL_ONLY", env.db.healthDao().check(c.id)!!.header.syncStatus)
    }

    @Test fun loopbackIsIdempotent() = runBlocking {
        val lb = LoopbackSyncTransport()
        val req = SyncPushRequest("d", listOf(SyncItemDto("k1", "measurement", "e1", "UPSERT", 1, "{}")))
        assertEquals(AckStatus.OK, (lb.push(req) as TransportResult.Success).acks.single().status)
        assertEquals(AckStatus.DUPLICATE, (lb.push(req) as TransportResult.Success).acks.single().status)
    }
}
