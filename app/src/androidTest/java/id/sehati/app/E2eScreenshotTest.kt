package id.sehati.app

import android.content.Context
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Uji end-to-end di emulator dengan database terenkripsi asli (SQLCipher + Keystore).
 * Setiap layar penting diambil screenshot-nya ke /sdcard/Android/data/<pkg>/files/shots.
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class E2eScreenshotTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val dir: File by lazy {
        File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null), "shots").apply { mkdirs() }
    }
    private var n = 0

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), Configuration.Builder().build())
        hilt.inject()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        device.takeScreenshot(File(dir, "%02d_%s.png".format(++n, name)))
    }

    private fun AndroidComposeTestRule<*, *>.waitGone(tag: String, ms: Long = 30_000) =
        waitUntil(ms) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    private fun AndroidComposeTestRule<*, *>.waitTag(tag: String, ms: Long = 90_000) = try {
        waitUntil(ms) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        // Bukti saat gagal: layar + pohon semantik, agar penyebab terlihat dari artifact/screenshot CI.
        runCatching { device.takeScreenshot(File(dir, "%02d_GAGAL_menunggu_%s.png".format(++n, tag))) }
        runCatching { File(dir, "gagal_semantik.txt").writeText(onRoot(useUnmergedTree = true).printToString()) }
        throw e
    }

    private fun AndroidComposeTestRule<*, *>.click(tag: String) {
        waitTag(tag); runCatching { onNodeWithTag(tag).performScrollTo() }; onNodeWithTag(tag).performClick()
    }

    private fun AndroidComposeTestRule<*, *>.type(tag: String, text: String) {
        waitTag(tag); runCatching { onNodeWithTag(tag).performScrollTo() }
        onNodeWithTag(tag).performTextClearance(); onNodeWithTag(tag).performTextInput(text)
    }

    private fun logout(profileTag: String = "nav_profile") {
        rule.click(profileTag); rule.click("logout_button"); rule.click("logout_dialog_confirm")
    }

    private fun AndroidComposeTestRule<*, *>.assessment(prefix: String) {
        waitTag("assessment_screen")
        type("assess_height_field", "165"); type("assess_weight_field", "72")
        shot("${prefix}_asesmen_tubuh")
        repeat(7) { click("submit_assessment_button") }
        shot("${prefix}_asesmen_pengukuran")
        click("submit_assessment_button")
        waitTag("health_status_card"); shot("${prefix}_profil_sehati")
        click("submit_assessment_button"); shot("${prefix}_rencana")
        click("submit_assessment_button")
        waitTag("home_screen"); waitTag("home_greeting_name"); waitGone("welcome_overlay")
    }

    @Test fun test1_onboardingRegistration() {
        rule.waitTag("welcome_screen", 180_000); shot("welcome")
        rule.click("welcome_start_button")
        rule.waitTag("onboarding_screen"); shot("onboarding_1")
        rule.click("onboarding_next_button"); shot("onboarding_2_apa_itu")
        rule.click("onboarding_next_button"); shot("onboarding_3_consent")
        rule.click("consent_local_switch"); rule.click("onboarding_next_button")
        rule.type("onboarding_name_field", "Budi Uji"); rule.type("onboarding_day_field", "15")
        rule.type("onboarding_month_field", "6"); rule.type("onboarding_year_field", "1985")
        rule.click("sex_Laki-laki"); rule.type("onboarding_rw_field", "02"); shot("onboarding_4_identitas")
        rule.click("onboarding_next_button"); shot("onboarding_5_tujuan")
        rule.click("onboarding_next_button")
        rule.type("onboarding_password_field", "rahasia1"); rule.type("onboarding_confirm_field", "rahasia1"); shot("onboarding_6_akun")
        rule.click("onboarding_next_button")
        rule.assessment("baru")
        shot("beranda_warga_baru")
        logout()
    }

    @Test fun test2_goldenPath() {
        rule.waitTag("welcome_screen", 120_000)
        rule.click("welcome_login_button"); rule.waitTag("login_screen"); shot("login_demo")
        rule.click("demo_login_warga")
        rule.assessment("demo")
        rule.waitTag("home_greeting_name"); shot("beranda")
        rule.click("heart_risk_card"); rule.waitTag("heart_risk_screen"); shot("faktor_risiko_jantung")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        runCatching { rule.onNodeWithTag("daily_fact_card").performScrollTo() }; shot("beranda_tantangan_fakta")
        rule.click("open_challenges"); rule.waitTag("challenges_screen"); shot("tantangan_lencana")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("qa_ask"); rule.waitTag("coach_screen"); rule.click("coach_chip_Apa tanda bahaya serangan jantung?"); shot("tanya_sehati")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("home_sos_button"); rule.waitTag("emergency_screen"); shot("darurat")
        runCatching { rule.onNodeWithTag("cpr_coach").performScrollTo() }; shot("darurat_rjp")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("qa_breath"); rule.waitTag("breathing_screen"); shot("latihan_napas")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("qa_meds"); rule.waitTag("medication_screen"); shot("obat_saya")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("back_button"); rule.waitTag("home_greeting_name")
        rule.click("nav_move"); rule.waitTag("move_screen"); shot("aktivitas")
        rule.click("nav_food"); rule.waitTag("food_screen"); shot("makanan")
        rule.click("nav_health"); rule.waitTag("health_screen"); shot("kesehatan")
        rule.click("show_qr_button"); rule.waitTag("qr_screen"); shot("qr")
        rule.click("back_button")
        rule.click("nav_profile"); rule.waitTag("profile_screen"); shot("profil")
        rule.click("profile_reminders_button"); rule.waitTag("reminder_settings_screen"); shot("pengingat")
        rule.click("back_button"); rule.waitTag("profile_screen")
        rule.click("logout_button"); rule.click("logout_dialog_confirm")

        rule.click("welcome_login_button"); rule.click("demo_login_kader")
        rule.waitTag("kader_name"); rule.waitGone("welcome_overlay"); shot("kader_hari_ini")
        rule.click("kader_tab_citizens")
        rule.type("citizen_search_field", "HM-000127"); rule.waitTag("register_visit_HM-000127"); shot("kader_cari_warga")
        rule.click("register_visit_HM-000127")
        rule.waitTag("kader_exam_screen")
        rule.type("kader_weight", "72"); rule.type("kader_height", "165"); rule.type("kader_waist", "90")
        rule.type("kader_sys", "145"); rule.type("kader_dia", "92"); rule.type("kader_glucose", "168"); shot("kader_pengukuran")
        rule.click("review_measurement_button"); shot("kader_tinjau")
        rule.click("save_measurement_button"); rule.waitTag("saved_banner"); shot("kader_penyuluhan")
        rule.click("education_next_button")
        rule.click("kader_confirm_checkbox"); shot("kader_validasi")
        rule.click("validate_visit_button"); rule.waitTag("visit_result"); rule.waitTag("sync_status_synced"); shot("kader_selesai")
        rule.click("kader_tab_followup"); rule.waitTag("kader_followup_screen"); shot("kader_followup")
        rule.click("kader_tab_sync"); rule.waitTag("kader_sync_screen"); shot("kader_sinkron")
        rule.click("kader_logout_button"); rule.click("kader_logout_dialog_confirm")

        rule.click("welcome_login_button"); rule.click("demo_login_admin")
        rule.waitTag("admin_overview_screen"); rule.waitGone("welcome_overlay"); rule.waitTag("admin_kpi_registered"); shot("admin_overview")
        rule.click("admin_tab_community"); rule.waitTag("admin_community_screen"); shot("admin_heartmap")
        rule.click("admin_tab_followup"); rule.waitTag("registry_HM-000127"); shot("admin_followup")
        rule.click("admin_tab_reports"); rule.waitTag("admin_reports_screen"); shot("admin_laporan")
        rule.click("admin_tab_cadres"); rule.waitTag("admin_cadres_screen"); shot("admin_kader")
        rule.click("admin_tab_logistics"); rule.waitTag("admin_logistics_screen"); shot("admin_logistik")
        rule.click("admin_tab_settings"); rule.waitTag("admin_settings_screen"); shot("admin_pengaturan")
    }
}
