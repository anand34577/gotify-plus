package com.gotify.client

import com.gotify.client.data.db.toEntity
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.notification.Priority
import com.gotify.client.ui.apps.resolveAppImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun notificationPrioritiesUseExpectedBands() {
        assertEquals(Priority.LOW, Priority.fromInt(3))
        assertEquals(Priority.NORMAL, Priority.fromInt(7))
        assertEquals(Priority.HIGH, Priority.fromInt(8))
    }
}
