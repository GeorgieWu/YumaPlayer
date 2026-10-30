package moe.rukamori.archivetune.ui.screens.login

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLoginNavigationTest {
    @Test
    fun extractionRequiresTheActualYouTubeHostNotAContinueQuery() {
        assertTrue(isYouTubeLoginHost("www.youtube.com"))
        assertTrue(isYouTubeLoginHost("M.YOUTUBE.COM"))
        assertTrue(isYouTubeLoginHost("youtube.com"))
        assertFalse(isYouTubeLoginHost(URI(DEFAULT_LOGIN_URL).host))
        assertFalse(isYouTubeLoginHost("youtube.com.example.com"))
        assertFalse(isYouTubeLoginHost("notyoutube.com"))
        assertFalse(isYouTubeLoginHost(null))
    }

    @Test
    fun defaultGoogleLoginReturnsToTheYouTubeWebsiteRatherThanMobileMusic() {
        val login = URI(DEFAULT_LOGIN_URL)
        assertEquals("https", login.scheme)
        assertEquals("accounts.google.com", login.host)
        assertEquals("/ServiceLogin", login.path)
        assertEquals(
            "https://www.youtube.com",
            URLDecoder.decode(login.rawQuery.substringAfter("continue="), StandardCharsets.UTF_8.name()),
        )
    }

    @Test
    fun storeDetectionUsesTheActualHostPathAndPackage() {
        val musicPackage = "com.google.android.apps.youtube.music"
        assertTrue(isYouTubeMusicStorePage("play.google.com", "/store/apps/details", musicPackage))
        assertTrue(isYouTubeMusicStorePage("PLAY.GOOGLE.COM", "/store/apps/details/", musicPackage))
        assertFalse(isYouTubeMusicStorePage("play.google.com.example.com", "/store/apps/details", musicPackage))
        assertFalse(isYouTubeMusicStorePage("accounts.google.com", "/store/apps/details", musicPackage))
        assertFalse(isYouTubeMusicStorePage("play.google.com", "/store/books/details", musicPackage))
        assertFalse(isYouTubeMusicStorePage("play.google.com", "/store/apps/details", "another.app"))
        assertFalse(isYouTubeMusicStorePage("play.google.com", "/store/apps/details", null))
    }

    @Test
    fun musicStoreWebPagesAreBlocked() {
        for (scheme in listOf("https", "http", "HTTPS")) {
            assertTrue(shouldBlockYouTubeLoginNavigation(uri(scheme, "play.google.com", "/store/apps/details", "com.google.android.apps.youtube.music")))
        }
    }

    @Test
    fun intentWrappedMusicStorePagesAreBlockedWithoutParsingOrLaunching() {
        assertTrue(shouldBlockYouTubeLoginNavigation(uri("intent", "play.google.com", "/store/apps/details", "com.google.android.apps.youtube.music")))
    }

    @Test
    fun marketMusicStoreLinksAreBlocked() {
        assertTrue(shouldBlockYouTubeLoginNavigation(uri("market", "details", packageName = "com.google.android.apps.youtube.music")))
    }

    @Test
    fun webPagesAndAuthRedirectsStayInWebView() {
        for (scheme in listOf("https", "http", "HTTPS")) {
            for (host in listOf("accounts.google.com", "www.youtube.com", "music.youtube.com")) {
                assertFalse(shouldBlockYouTubeLoginNavigation(uri(scheme, host)))
            }
        }
    }

    @Test
    fun nonWebSchemesAreAlwaysBlocked() {
        for (scheme in listOf("intent", "vnd.youtube.music", "market", "mailto", "data", null)) {
            assertTrue(shouldBlockYouTubeLoginNavigation(uri(scheme)))
        }
    }

    @Test
    fun unrelatedStoreWebPagesRemainInWebView() {
        assertFalse(shouldBlockYouTubeLoginNavigation(uri("https", "play.google.com", "/store/apps/details", "another.app")))
        assertFalse(shouldBlockYouTubeLoginNavigation(uri("https", "play.google.com", "/store/books/details")))
    }

    private fun uri(
        scheme: String?,
        host: String? = null,
        path: String? = null,
        packageName: String? = null,
    ): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns scheme
        every { uri.host } returns host
        every { uri.path } returns path
        every { uri.getQueryParameter("id") } returns packageName
        return uri
    }
}
