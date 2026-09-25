package com.gotify.client

import com.gotify.client.data.db.toEntity
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.notification.Priority
import com.gotify.client.ui.apps.resolveAppImageUrl
import com.gotify.client.ui.components.shouldAttachGotifyKey
import com.gotify.client.data.datastore.UserPreferences
import com.gotify.client.data.datastore.isInQuietHours
import com.gotify.client.data.datastore.muteKey
import com.gotify.client.ui.components.dayLabel
import com.gotify.client.ui.components.parseGotifyDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBehaviorTest {
    @Test
    fun messageIdentityIsScopedByServer() {
        val message = GotifyMessage(42, 1, "body", "title", 5, "2026-01-01T00:00:00Z")
        assertEquals(1L, message.toEntity(1).serverId)
        assertEquals(2L, message.toEntity(2).serverId)
        assertEquals(message.toEntity(1).id, message.toEntity(2).id)
    }

    @Test
    fun applicationImagePathsResolveSafely() {
        assertEquals("https://example.test/image/icon.png", resolveAppImageUrl("https://example.test/", "/image/icon.png"))
        assertEquals("https://cdn.test/icon.png", resolveAppImageUrl("https://example.test", "https://cdn.test/icon.png"))
        assertNull(resolveAppImageUrl("", "image/icon.png"))
    }

    @Test
    fun quietHoursHandleMidnightWrap() {
        val m = { h: Int, min: Int -> h * 60 + min }
        // 22:00 → 07:00 wraps midnight
        assertTrue(isInQuietHours(m(23, 30), m(22, 0), m(7, 0)))
        assertTrue(isInQuietHours(m(3, 0), m(22, 0), m(7, 0)))
        assertFalse(isInQuietHours(m(7, 0), m(22, 0), m(7, 0)))
        assertFalse(isInQuietHours(m(12, 0), m(22, 0), m(7, 0)))
        // 13:00 → 14:00 same day
        assertTrue(isInQuietHours(m(13, 30), m(13, 0), m(14, 0)))
        assertFalse(isInQuietHours(m(14, 0), m(13, 0), m(14, 0)))
        // equal start/end = disabled
        assertFalse(isInQuietHours(m(9, 0), m(9, 0), m(9, 0)))
    }

    @Test
    fun gotifyDatesWithOffsetsParse() {
        val utc = java.time.Instant.parse("2026-01-01T10:00:00Z")
        assertEquals(utc, parseGotifyDate("2026-01-01T15:30:00+05:30"))
        assertEquals(utc, parseGotifyDate("2026-01-01T10:00:00.123456789Z")?.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
        assertNull(parseGotifyDate("not a date"))
    }

    @Test
    fun dayLabelsBucketByLocalDay() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 9, 25)
        assertEquals("Today", dayLabel("2026-09-25T08:00:00Z", today, zone))
        assertEquals("Yesterday", dayLabel("2026-09-24T23:59:00Z", today, zone))
        assertTrue(dayLabel("2025-09-01T12:00:00Z", today, zone).endsWith("1, 2025")) // month name is locale-specific
        assertEquals("Earlier", dayLabel("garbage", today, zone))
    }

    @Test
    fun appMutesExpire() {
        val prefs = UserPreferences(appMutes = mapOf(muteKey(1, 7) to 2_000L, muteKey(1, 8) to Long.MAX_VALUE))
        assertTrue(prefs.isAppMuted(1, 7, now = 1_000L))
        assertFalse(prefs.isAppMuted(1, 7, now = 3_000L))
        assertTrue(prefs.isAppMuted(1, 8, now = 3_000L))
        assertFalse(prefs.isAppMuted(2, 7, now = 1_000L))
    }

    @Test
    fun gotifyDefaultAppImageFallsBackToInitials() {
        // Exact value the Gotify server returns (api/application.go withResolvedImage)
        assertNull(resolveAppImageUrl("https://example.test", "static/defaultapp.png"))
        assertNull(resolveAppImageUrl("https://example.test", "https://example.test/static/defaultapp.png"))
        assertNull(resolveAppImageUrl("https://example.test/gotify", "https://example.test/gotify/static/defaultapp.png"))
        assertNull(resolveAppImageUrl("https://example.test", "/static/defaultapp.jpg"))
        // Real uploaded icons still resolve
        assertEquals("https://example.test/image/abc.png", resolveAppImageUrl("https://example.test", "image/abc.png"))
    }

    @Test
    fun notificationPrioritiesUseExpectedBands() {
        assertEquals(Priority.LOW, Priority.fromInt(3))
        assertEquals(Priority.NORMAL, Priority.fromInt(7))
        assertEquals(Priority.HIGH, Priority.fromInt(8))
    }

    @Test
    fun imageCredentialsStayOnTheSameOriginAndScheme() {
        assertEquals(
            true,
            shouldAttachGotifyKey(
                "https://example.test/image.png",
                "token",
                "https://example.test"
            )
        )
        assertEquals(
            false,
            shouldAttachGotifyKey(
                "http://example.test/image.png",
                "token",
                "https://example.test"
            )
        )
        assertEquals(
            false,
            shouldAttachGotifyKey(
                "https://cdn.test/image.png",
                "token",
                "https://example.test"
            )
        )
    }
}
