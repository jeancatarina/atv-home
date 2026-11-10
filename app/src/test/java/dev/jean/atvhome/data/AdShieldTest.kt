package dev.jean.atvhome.data

import android.Manifest
import android.app.Application
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdShieldTest {

    private val app = RuntimeEnvironment.getApplication()
    private val cr get() = app.contentResolver
    private val guard = "dev.jean.atvhome/dev.jean.atvhome.guard.AdGuardService"
    private var dnsAnswers = true

    private fun mode() = Settings.Global.getString(cr, "private_dns_mode")
    private fun services() = Settings.Secure.getString(cr, "enabled_accessibility_services").orEmpty()
    private fun allow() = shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

    /** AdShield is an object: its view of the TV (Home in front, nothing playing) starts over each test. */
    @Before fun startOnHome() = runBlocking {
        AdShield.dnsCheck = { dnsAnswers }
        AdShield.offDelayMs = 0
        AdShield.launcherInFront(app, true)
        AdShield.playbackChanged(app, false)
    }

    /** Another app is in front and plays sound. */
    private suspend fun watching() { AdShield.launcherInFront(app, false); AdShield.playbackChanged(app, true) }

    @Test fun withoutThePermissionNothingChanges() = runBlocking {
        assertFalse(AdShield.set(app, true))
        assertEquals(AdShield.Outcome.REFUSED, AdShield.setDns(app, true))
        assertNull(mode())
        assertFalse(AdShield.isOn(app))
    }

    @Test fun theGuardIsListedOnlyWhileAnotherAppPlaysSound() = runBlocking {
        allow()
        assertTrue(AdShield.set(app, true))
        assertFalse(guard in services()) // Home in front
        AdShield.launcherInFront(app, false)
        assertFalse(guard in services()) // an app's menu, nothing playing
        AdShield.playbackChanged(app, true)
        assertTrue(guard in services())
        AdShield.launcherInFront(app, true)
        assertFalse(guard in services()) // back on Home: at once
        watching()
        AdShield.playbackChanged(app, false)
        delay(200) // the short grace for a pause or the gap between an ad and the film
        assertFalse(guard in services())
    }

    @Test fun theGuardAloneNeverTouchesTheDns() = runBlocking {
        allow()
        AdShield.set(app, true)
        assertNull(mode())
        assertFalse(AdShield.dnsOn(app))
    }

    @Test fun switchedOffTheGuardIsNeverListed() = runBlocking {
        allow()
        watching()
        assertEquals("", services())
        AdShield.set(app, true)
        AdShield.set(app, false)
        AdShield.playbackChanged(app, false)
        AdShield.playbackChanged(app, true)
        assertEquals("", services())
    }

    @Test fun accessibilityIsLeftExactlyAsItWas() = runBlocking {
        allow()
        Settings.Secure.putString(cr, "accessibility_enabled", "0")
        AdShield.set(app, true)
        watching()
        assertEquals("1", Settings.Secure.getString(cr, "accessibility_enabled"))
        AdShield.launcherInFront(app, true)
        assertEquals("0", Settings.Secure.getString(cr, "accessibility_enabled"))
    }

    @Test fun otherServicesStayListedAndOn() = runBlocking {
        allow()
        Settings.Secure.putString(cr, "enabled_accessibility_services", "a/b.C")
        Settings.Secure.putString(cr, "accessibility_enabled", "1")
        AdShield.set(app, true)
        watching()
        assertEquals("a/b.C:$guard", services())
        AdShield.set(app, false)
        assertEquals("a/b.C", services())
        assertEquals("1", Settings.Secure.getString(cr, "accessibility_enabled"))
    }

    @Test fun theNetworkFilterPointsTheDnsAndPutsBackWhatWasThere() = runBlocking {
        allow()
        Settings.Global.putString(cr, "private_dns_mode", "off")
        assertEquals(AdShield.Outcome.DONE, AdShield.setDns(app, true))
        assertEquals("hostname", mode())
        assertEquals("dns.adguard-dns.com", Settings.Global.getString(cr, "private_dns_specifier"))
        assertTrue(AdShield.dnsOn(app))
        AdShield.setDns(app, false)
        assertEquals("off", mode())
        assertFalse(AdShield.dnsOn(app))
    }

    @Test fun aResolverThatDoesNotAnswerNeverLeavesTheTvOffline() = runBlocking {
        allow()
        Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
        dnsAnswers = false
        assertEquals(AdShield.Outcome.NO_ANSWER, AdShield.setDns(app, true))
        assertEquals("opportunistic", mode())
        assertFalse(AdShield.dnsOn(app))
    }

    @Test fun backOnHomeAResolverThatStoppedAnsweringIsSwitchedOff() = runBlocking {
        allow()
        Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
        AdShield.setDns(app, true)
        assertFalse(AdShield.heal(app))
        assertEquals("hostname", mode())
        dnsAnswers = false
        assertTrue(AdShield.heal(app))
        assertEquals("opportunistic", mode())
        assertFalse(AdShield.dnsOn(app))
        assertFalse(AdShield.heal(app)) // already off: nothing more to do
    }

    @Test fun aRefusedCaptureIsRemembered() {
        assertFalse(AdShield.captureRefused(app))
        AdShield.rememberCapture(app, granted = false)
        assertTrue(AdShield.captureRefused(app))
        AdShield.rememberCapture(app, granted = true)
        assertFalse(AdShield.captureRefused(app))
    }
}
