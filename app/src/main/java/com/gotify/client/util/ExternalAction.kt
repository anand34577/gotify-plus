package com.gotify.client.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Converts Gotify action payloads into user-launched intents.
 *
 * Gotify supports both ordinary URLs and Android intent URIs. Keeping the
 * conversion in one place prevents the notification and message-detail paths
 * from handling the same payload differently.
 */
fun gotifyActionIntent(rawAction: String): Intent {
    return runCatching {
        Intent.parseUri(rawAction, Intent.URI_INTENT_SCHEME).sanitized()
    }.getOrElse {
        Intent(Intent.ACTION_VIEW, rawAction.toUri())
    }
}

/** Payloads come from the server: never let them carry URI grants or a hidden selector. */
fun Intent.sanitized(): Intent = apply {
    selector = null
    flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
}

fun Context.launchGotifyAction(rawAction: String): Boolean {
    if (rawAction.isBlank()) return false
    return runCatching {
        startActivity(gotifyActionIntent(rawAction))
        true
    }.getOrDefault(false)
}
