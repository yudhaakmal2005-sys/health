package id.sehati.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.domain.model.PROFILE_DISCLAIMER
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.model.SyncStatus
import id.sehati.app.domain.rules.RedFlag
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.SehatiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun riskCardShowsLabelExplanationAndDisclaimer() {
        rule.setContent { SehatiTheme { RiskCard(RiskLevel.HIGHER_MONITORING) } }
        rule.onNodeWithTag("health_status_card").assertIsDisplayed()
        rule.onNodeWithText("Higher Monitoring Need").assertIsDisplayed()
        rule.onNodeWithText(RiskLevel.HIGHER_MONITORING.summary).assertIsDisplayed()
        rule.onNodeWithText(PROFILE_DISCLAIMER).assertIsDisplayed()
    }

    @Test fun everyRiskLevelHasLabelNotJustColor() {
        rule.setContent { SehatiTheme { androidx.compose.foundation.layout.Column { RiskLevel.entries.forEach { RiskCard(it, compact = true) } } } }
        RiskLevel.entries.forEach { lvl -> rule.onNodeWithText(lvl.label).assertExists() }
    }

    @Test fun emergencyBannerShowsMedicalHelpMessage() {
        rule.setContent { SehatiTheme { EmergencyBanner() } }
        rule.onNodeWithTag("emergency_banner").assertIsDisplayed()
        rule.onNodeWithText(RedFlag.EMERGENCY_MESSAGE).assertIsDisplayed()
    }

    @Test fun syncChipShowsAllFourStatusLabels() {
        rule.setContent { SehatiTheme { androidx.compose.foundation.layout.Column { SyncStatus.entries.forEach { SyncChip(it) } } } }
        SyncStatus.entries.forEach { s -> rule.onNodeWithText(s.label).assertExists() }
    }

    @Test fun emptyStateActionIsClickable() {
        var clicked = 0
        rule.setContent { SehatiTheme { EmptyState(Icons.Rounded.Favorite, "Kosong", "Belum ada data", action = "Tambah", onAction = { clicked++ }) } }
        rule.onNodeWithText("Tambah").performClick()
        assertEquals(1, clicked)
    }

    @Test fun errorStateOffersRetry() {
        var retried = false
        rule.setContent { SehatiTheme { ErrorState("Data belum tersimpan karena koneksi terputus.", retry = "Coba lagi", onRetry = { retried = true }) } }
        rule.onNodeWithTag("error_state").assertIsDisplayed()
        rule.onNodeWithText("Coba lagi").performClick()
        assert(retried)
    }

    @Test fun loadingStateIsShown() {
        rule.setContent { SehatiTheme { LoadingState() } }
        rule.onNodeWithTag("loading_state").assertIsDisplayed()
    }

    @Test fun numberStepperIncrementsAndRespectsBounds() {
        rule.setContent {
            SehatiTheme { var v by remember { mutableIntStateOf(2) }; NumberStepper("Hari aktif", v, { v = it }, 0..3, tag = "st") }
        }
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithTag("st_plus").performClick()
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithTag("st_plus").assertIsNotEnabled()
        rule.onNodeWithTag("st_minus").assertIsEnabled()
    }

    @Test fun primaryButtonDisabledWhileLoading() {
        var clicks = 0
        rule.setContent { SehatiTheme { PrimaryButton("Simpan", { clicks++ }, loading = true, tag = "btn") } }
        rule.onNodeWithTag("btn").assertIsNotEnabled()
        rule.onNodeWithTag("btn").performClick()
        assertEquals(0, clicks)
    }

    @Test fun wizardPrimaryButtonInvokesCallbackAndShowsError() {
        var next = 0
        rule.setContent { SehatiTheme { WizardLayout(1, 4, "Judul", "Sub", onBack = {}, primaryText = "Lanjut", onPrimary = { next++ }, error = "Isi tinggi badan.") { Text("isi") } } }
        rule.onNodeWithText("Isi tinggi badan.").assertIsDisplayed()
        rule.onNodeWithTag("wizard_next_button").performClick()
        assertEquals(1, next)
    }

    @Test fun confirmDialogConfirms() {
        var ok = false
        rule.setContent { SehatiTheme { ConfirmDialog("Hapus?", "Yakin?", "Hapus", { ok = true }, {}) } }
        rule.onNodeWithTag("confirm_dialog_confirm").performClick()
        assert(ok)
    }

    @Test fun textFieldShowsErrorText() {
        rule.setContent { SehatiTheme { SehatiTextField("", {}, "Nama", error = "Nama wajib diisi.", tag = "f") } }
        rule.onNodeWithText("Nama wajib diisi.").assertIsDisplayed()
    }
}
