package id.sehati.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Bukan uji kebenaran: alur pameran yang direkam (`adb screenrecord`) dengan animasi MENYALA, agar gerak antarmuka
 * dapat dinilai dari video. Memakai UiAutomator (bukan Compose test) karena animasi tak berujung membuat Compose tidak pernah idle.
 * Dijalankan terpisah oleh .github/scripts/smoke.sh.
 */
@HiltAndroidTest
class ShowcaseTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    private val d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), Configuration.Builder().build())
        hilt.inject()
    }

    private fun tap(tag: String, wait: Long = 15_000): Boolean {
        val o = d.wait(Until.findObject(By.res(tag)), wait) ?: return false
        o.click(); return true
    }
    private fun pause(ms: Long) = Thread.sleep(ms)
    private fun swipeUp() = d.swipe(d.displayWidth / 2, (d.displayHeight * 0.78).toInt(), d.displayWidth / 2, (d.displayHeight * 0.30).toInt(), 40)
    private fun swipeDown() = d.swipe(d.displayWidth / 2, (d.displayHeight * 0.30).toInt(), d.displayWidth / 2, (d.displayHeight * 0.78).toInt(), 40)
    private fun type(tag: String, text: String) { d.wait(Until.findObject(By.res(tag)), 10_000)?.text = text }
    private fun scrollTo(tag: String): Boolean {
        repeat(6) { if (d.findObject(By.res(tag)) != null) return true; swipeUp(); pause(500) }
        return d.findObject(By.res(tag)) != null
    }

    @Test fun showcase() {
        ActivityScenario.launch(MainActivity::class.java).use {
            d.wait(Until.findObject(By.res("welcome_screen")), 180_000)
            pause(2500)
            tap("welcome_login_button"); pause(1200)
            type("login_id_field", "HM-000128"); type("login_password_field", "demo1234")
            d.pressBack(); pause(400)
            tap("login_button")
            pause(4500) // sambutan: logo berputar + EKG
            d.wait(Until.findObject(By.res("home_greeting_name")), 30_000)
            pause(2000)
            swipeUp(); pause(1500); swipeUp(); pause(1500); swipeDown(); pause(800); swipeDown(); pause(1500)

            if (scrollTo("open_challenges")) { tap("open_challenges"); pause(2000); swipeUp(); pause(1200); d.pressBack(); pause(1500) }
            swipeDown(); swipeDown(); swipeDown(); pause(800)

            tap("qa_ask"); pause(1500)
            tap("coach_chip_Apa tanda bahaya serangan jantung?"); pause(3500)
            d.pressBack(); pause(1500)

            tap("home_sos_button"); pause(1500)
            if (scrollTo("cpr_toggle")) { tap("cpr_toggle"); pause(5000); tap("cpr_toggle") }
            pause(800); d.pressBack(); pause(1500)

            tap("qa_breath"); pause(1200); tap("breath_toggle"); pause(9000); tap("breath_toggle"); pause(800)
            d.pressBack(); pause(1500)

            for (t in listOf("nav_move", "nav_food", "nav_health", "nav_profile", "nav_home")) { tap(t); pause(2200) }
            pause(1500)
        }
    }
}
