package dev.jean.atvhome.data

import android.Manifest
import android.app.Application
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdShieldTest {

    private val app = RuntimeEnvironment.getApplication()
    private val cr get() = app.contentResolver
    private val guard = "dev.jean.atvhome/dev.jean.atvhome.guard.AdGuardService"

    private fun mode() = Settings.Global.getString(cr, "private_dns_mode")
    private fun services() = Settings.Secure.getString(cr, "enabled_accessibility_services").orEmpty()
    private fun allow() = shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

    @Test fun withoutThePermissionNothingChanges() = runBlocking {
        assertFalse(AdShield.set(app, true))
        assertNull(mode())
        assertFalse(AdShield.isOn(app))
    }

    @Test fun onPointsTheDnsAndWaitsForAnotherAppToListTheGuard() = runBlocking {
        allow()
        assertTrue(AdShield.set(app, true))
        assertEquals("hostname", mode())
        assertEquals("dns.adguard-dns.com", Settings.Global.getString(cr, "private_dns_specifier"))
        assertTrue(AdShield.isOn(app))
        // The launcher is in front when the switch is flipped: Home must not pay for the service
        assertFalse(guard in services())
        AdShield.launcherInFront(app, false)
        assertTrue(guard in services())
        assertEquals("1", Settings.Secure.getString(cr, "accessibility_enabled"))
        AdShield.launcherInFront(app, true)
        assertFalse(guard in services())
    }

    @Test fun offPutsBackWhatWasThere() = runBlocking {
        allow()
        Settings.Global.putString(cr, "private_dns_mode", "off")
        AdShield.set(app, true)
        AdShield.set(app, false)
        assertEquals("off", mode())
    }

    @Test fun otherServicesStayListed() = runBlocking {
        allow()
        Settings.Secure.putString(cr, "enabled_accessibility_services", "a/b.C")
        AdShield.set(app, true)
        AdShield.launcherInFront(app, false)
        assertEquals("a/b.C:$guard", services())
        AdShield.set(app, false)
        assertEquals("a/b.C", services())
    }

    @Test fun switchedOffTheGuardIsNeverListed() = runBlocking {
        allow()
        AdShield.launcherInFront(app, false)
        assertEquals("", services())
    }
}
