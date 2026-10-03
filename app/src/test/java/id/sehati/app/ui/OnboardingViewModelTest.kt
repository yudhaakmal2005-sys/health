package id.sehati.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.data.TestEnv
import id.sehati.app.domain.model.Sex
import id.sehati.app.ui.onboarding.OnboardingForm
import id.sehati.app.ui.onboarding.OnboardingViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingViewModelTest {
    private lateinit var env: TestEnv
    private lateinit var vm: OnboardingViewModel
    @Before fun setUp() { env = TestEnv(); vm = OnboardingViewModel(env.auth) }
    @After fun tearDown() { env.close() }

    private val valid = OnboardingForm(name = "Tariska", day = "1", month = "3", year = "1992", sex = Sex.FEMALE, rw = "01", consentLocal = true, password = "rahasia1", confirm = "rahasia1")

    @Test fun consentIsRequiredToContinue() { assertNotNull(vm.pageError(2, valid.copy(consentLocal = false))); assertNull(vm.pageError(2, valid)) }
    @Test fun identityRequiresValidFields() {
        assertNotNull(vm.pageError(3, valid.copy(name = "")))
        assertNotNull(vm.pageError(3, valid.copy(day = "31", month = "2")))      // 31 Feb
        assertNotNull(vm.pageError(3, valid.copy(year = "2999")))                // masa depan
        assertNotNull(vm.pageError(3, valid.copy(sex = null)))
        assertNotNull(vm.pageError(3, valid.copy(rw = "")))
        assertNotNull(vm.pageError(3, valid.copy(phone = "123")))
        assertNull(vm.pageError(3, valid))
    }
    @Test fun passwordRulesAndConfirmation() {
        assertNotNull(vm.pageError(5, valid.copy(password = "123", confirm = "123")))
        assertNotNull(vm.pageError(5, valid.copy(confirm = "beda")))
        assertNull(vm.pageError(5, valid))
    }
}
