package id.sehati.app.e2e

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import id.sehati.app.MainActivity
import id.sehati.app.ui.theme.SehatiTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * UJI UI END-TO-END (Robolectric + Hilt + Room in-memory):
 * Warga HM-000127 → asesmen → profil → QR → keluar → Kader mengukur → validasi → sinkron → keluar → Admin melihat tindak lanjut.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class GoldenPathUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) // kurangi animasi: UI idle
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
        hilt.inject()
    }

    private fun AndroidComposeTestRule<*, *>.waitTag(tag: String, ms: Long = 60_000) =
        waitUntil(ms) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun AndroidComposeTestRule<*, *>.waitGone(tag: String, ms: Long = 30_000) =
        waitUntil(ms) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    private fun AndroidComposeTestRule<*, *>.click(tag: String) {
        waitTag(tag)
        runCatching { onNodeWithTag(tag).performScrollTo() }
        onNodeWithTag(tag).performClick()
    }

    private fun AndroidComposeTestRule<*, *>.type(tag: String, text: String) {
        waitTag(tag)
        runCatching { onNodeWithTag(tag).performScrollTo() }
        onNodeWithTag(tag).performTextClearance()
        onNodeWithTag(tag).performTextInput(text)
    }

    @Test fun fullGoldenPath() {
        // ---------- LOGIN WARGA ----------
        rule.waitTag("welcome_screen", 120_000)           // menunggu data demo selesai disemai
        rule.click("welcome_login_button")
        rule.click("demo_login_warga")

        // ---------- ASESMEN → PROFIL → RENCANA ----------
        rule.waitTag("assessment_screen")
        rule.type("assess_height_field", "165")
        rule.type("assess_weight_field", "72")
        repeat(8) { rule.click("submit_assessment_button") }   // 7× lanjut (langkah 1–7), lalu kirim di langkah 8
        rule.waitTag("health_status_card")                      // Profil SEHATI tampil (hasil asesmen tersimpan)
        rule.click("submit_assessment_button")                  // → rencana
        rule.click("submit_assessment_button")                  // → mulai SEHATI

        // ---------- BERANDA WARGA ----------
        rule.waitTag("home_screen")
        rule.waitGone("welcome_overlay")
        rule.waitTag("home_greeting_name")
        rule.onNodeWithTag("home_greeting_name").assertTextEquals("Tariska")
        rule.click("nav_health")
        rule.waitTag("health_screen")
        rule.click("show_qr_button")
        rule.waitTag("qr_screen")
        rule.onNodeWithTag("qr_sehati_id").assertTextEquals("HM-000127")
        rule.click("back_button")
        rule.click("nav_profile")
        rule.click("logout_button")
        rule.click("logout_dialog_confirm")

        // ---------- KADER ----------
        rule.click("welcome_login_button")                     // setelah keluar kembali ke layar sambutan
        rule.waitTag("login_screen")
        rule.click("demo_login_kader")
        rule.waitTag("kader_name")
        rule.waitGone("welcome_overlay")
        rule.click("kader_tab_citizens")
        rule.type("citizen_search_field", "HM-000127")
        rule.click("register_visit_HM-000127")
        rule.waitTag("kader_exam_screen")
        rule.type("kader_weight", "72")
        rule.type("kader_height", "165")
        rule.type("kader_waist", "90")
        rule.type("kader_sys", "145")
        rule.type("kader_dia", "92")
        rule.type("kader_glucose", "168")
        rule.click("review_measurement_button")
        rule.click("save_measurement_button")
        rule.waitTag("saved_banner")
        rule.click("education_next_button")
        rule.click("kader_confirm_checkbox")
        rule.click("validate_visit_button")
        rule.waitTag("visit_result")
        rule.waitTag("followup_created")                      // tindak lanjut dari hasil 145/92 & GDS 168
        rule.waitTag("sync_status_synced")                    // tersimpan lokal lalu tersinkron
        rule.click("kader_logout_button")
        rule.click("kader_logout_dialog_confirm")

        // ---------- ADMIN ----------
        rule.click("welcome_login_button")
        rule.waitTag("login_screen")
        rule.click("demo_login_admin")
        rule.waitTag("admin_overview_screen")
        rule.waitGone("welcome_overlay")
        rule.waitTag("admin_kpi_registered")
        rule.click("admin_tab_followup")
        rule.waitTag("registry_HM-000127")                    // data kader muncul di registri admin
    }
}
