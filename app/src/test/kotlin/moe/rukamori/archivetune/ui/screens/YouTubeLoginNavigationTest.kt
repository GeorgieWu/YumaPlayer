package moe.rukamori.archivetune.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
    fun musicStorePagesNeverReplaceTheLoginPageEvenWithAGesture() {
        assertEquals(LoginNavigationAction.Block, loginNavigationAction("https", true, false, true, musicStorePage = true))
        assertEquals(LoginNavigationAction.Block, loginNavigationAction("https", true, true, false, musicStorePage = true))
        assertEquals(LoginNavigationAction.Block, loginNavigationAction("http", false, false, false, musicStorePage = true))
    }

    @Test
    fun intentWrappedMusicStorePageCannotLaunchTheStoreEvenWithAGesture() {
        val incomingUri = mockk<Uri>()
        every { incomingUri.scheme } returns "intent"
        every { incomingUri.toString() } returns "intent://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music#Intent;scheme=https;end"
        val storeUri = mockk<Uri>()
        every { storeUri.scheme } returns "https"
        every { storeUri.host } returns "play.google.com"
        every { storeUri.path } returns "/store/apps/details"
        every { storeUri.getQueryParameter("id") } returns "com.google.android.apps.youtube.music"
        val parsedIntent = mockk<Intent>()
        every { parsedIntent.data } returns storeUri
        val request = mockk<WebResourceRequest>()
        every { request.url } returns incomingUri
        every { request.isForMainFrame } returns true
        every { request.hasGesture() } returns true
        every { request.isRedirect } returns false
        val context = mockk<Context>(relaxed = true)

        mockkStatic(Intent::class)
        try {
            every { Intent.parseUri(any<String>(), any()) } returns parsedIntent
            assertTrue(handleYouTubeLoginNavigation(context, request) { throw AssertionError("Blocked promotion is not an external-link error") })
            verify(exactly = 0) { context.startActivity(any()) }
        } finally {
            unmockkStatic(Intent::class)
        }
    }

    @Test
    fun webPagesAndAuthRedirectsStayInWebView() {
        for (scheme in listOf("https", "http", "HTTPS")) {
            assertEquals(LoginNavigationAction.WebView, loginNavigationAction(scheme, true, false, true))
        }
    }

    @Test
    fun automaticAppAndStorePromotionsAreConsumed() {
        for (scheme in listOf("intent", "vnd.youtube.music", "market")) {
            assertEquals(LoginNavigationAction.Block, loginNavigationAction(scheme, true, false, false))
        }
    }

    @Test
    fun redirectsCannotInheritPermissionToOpenAnExternalApp() {
        assertEquals(LoginNavigationAction.Block, loginNavigationAction("intent", true, true, true))
    }

    @Test
    fun subframesCannotLaunchApps() {
        assertEquals(LoginNavigationAction.Block, loginNavigationAction("intent", false, true, false))
    }

    @Test
    fun deliberateTopLevelExternalLinksAreDispatchedOutsideWebView() {
        assertEquals(LoginNavigationAction.ExternalApp, loginNavigationAction("intent", true, true, false))
    }
}
