package id.sehati.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import id.sehati.app.data.TestEnv
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.sync.LoopbackSyncTransport
import id.sehati.app.data.sync.SyncController
import id.sehati.app.data.work.WorkScheduler
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.ui.kader.SyncState
import id.sehati.app.ui.kader.VisitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

/** Alur layar Posyandu 5 langkah dijalankan lewat ViewModel yang sama dengan UI. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VisitFlowViewModelTest {
    private lateinit var env: TestEnv
    private lateinit var vm: VisitViewModel

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
        env = TestEnv(LoopbackSyncTransport())
        vm = VisitViewModel(env.posyandu, env.citizens, env.health, SyncController(env.db, env.engine, SettingsStore(ctx)), WorkScheduler(ctx))
    }
    @After fun tearDown() { Dispatchers.resetMain(); env.close() }

    @Test fun fiveStepFlowSavesToCitizenRecordAndSyncs() = runBlocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        val visit = env.posyandu.registerVisit(citizen.sehatiId, "fac-melati")

        vm.load(visit.id); withTimeout(10_000) { vm.ui.first { !it.loading } }
        assertEquals(citizen.sehatiId, vm.ui.value.citizen?.sehatiId)

        // Langkah 2: pengukuran + validasi
        vm.goReview(); assertEquals(0, vm.ui.value.step); assertNotNull(vm.ui.value.error) // kosong ditolak
        vm.update { copy(weight = "72", height = "165", waist = "90", systolic = "145", diastolic = "92", heartRate = "78", glucose = "168") }
        assertEquals(26.4f, vm.bmi())
        vm.goReview(); assertEquals(1, vm.ui.value.step)

        // Langkah 3: konfirmasi & simpan
        vm.confirmAndSave(); withTimeout(10_000) { vm.ui.first { it.step == 2 } }
        val saved = vm.ui.value.saved!!
        assertEquals(145 to 92, saved.bloodPressure)
        assertTrue(vm.ui.value.recommended.any { it.id == "hipertensi" })

        // Langkah 4: penyuluhan
        vm.saveEducation(); withTimeout(10_000) { vm.ui.first { it.step == 3 } }

        // Langkah 5: wajib konfirmasi kader
        vm.validateAndSync(); assertNotNull(vm.ui.value.error)
        vm.setConfirmed(true); vm.validateAndSync()
        withTimeout(20_000) { vm.ui.first { it.result != null && it.syncMessage != null } }

        assertEquals(SyncState.SYNCED, vm.ui.value.syncState)
        assertTrue(vm.ui.value.result!!.followUps.isNotEmpty())
        assertTrue(RiskLevel.parse(vm.ui.value.result!!.profile.level.name).rank >= RiskLevel.HIGHER_MONITORING.rank)
        // data yang sama terlihat pada sisi warga
        assertEquals(saved.id, env.health.checks(citizen.sehatiId).single().id)
        assertEquals("SYNCED", env.db.healthDao().check(saved.id)!!.header.syncStatus)
    }

    @Test fun measurementIsSavedLocallyEvenWhenSyncCannotRun() = runBlocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen(consentServer = false)   // tidak boleh dikirim → tetap lokal
        env.session.start("KD-000001", Role.KADER)
        val visit = env.posyandu.registerVisit(citizen.sehatiId, null)
        vm.load(visit.id); withTimeout(10_000) { vm.ui.first { !it.loading } }
        vm.update { copy(systolic = "130", diastolic = "85") }; vm.goReview()
        vm.confirmAndSave(); withTimeout(10_000) { vm.ui.first { it.step == 2 } }
        vm.saveEducation(); withTimeout(10_000) { vm.ui.first { it.step == 3 } }
        vm.setConfirmed(true); vm.validateAndSync()
        withTimeout(20_000) { vm.ui.first { it.result != null && it.syncMessage != null } }
        assertEquals(SyncState.LOCAL, vm.ui.value.syncState)
        assertEquals(1, env.health.checks(citizen.sehatiId).size)
    }
}
