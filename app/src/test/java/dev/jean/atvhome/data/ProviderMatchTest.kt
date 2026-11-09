package dev.jean.atvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The real BR provider names from TMDB against the app labels on the user's BRAVIA. */
class ProviderMatchTest {

    private val apps = listOf(
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.wbd.stream" to "HBO Max",
        "com.globo.globotv" to "Globoplay",
        "com.apple.atve.sony.appletv" to "Apple TV",
        "br.com.claro.now.smarttvclient" to "Claro tv+",
        "com.spotify.tv.android" to "Spotify",
    )

    private val providers = listOf(
        Provider(8, "Netflix", null),
        Provider(1796, "Netflix Standard with Ads", null),
        Provider(119, "Amazon Prime Video", null),
        Provider(350, "Apple TV", null),
        Provider(337, "Disney Plus", null),
        Provider(167, "Claro video", null),
        Provider(1899, "HBO Max", null),
        Provider(307, "Globoplay", null),
        Provider(283, "Crunchyroll", null),
    )

    private val match = ProviderMatch.match(providers, apps)

    @Test
    fun matchesEveryInstalledService() {
        assertEquals("com.netflix.ninja", match[8])
        assertEquals("com.netflix.ninja", match[1796])
        assertEquals("com.amazon.amazonvideo.livingroom", match[119])
        assertEquals("com.apple.atve.sony.appletv", match[350])
        assertEquals("com.disney.disneyplus", match[337])
        assertEquals("br.com.claro.now.smarttvclient", match[167])
        assertEquals("com.wbd.stream", match[1899])
        assertEquals("com.globo.globotv", match[307])
    }

    @Test
    fun leavesServicesWithoutAppAlone() {
        assertNull(match[283])
    }
}
