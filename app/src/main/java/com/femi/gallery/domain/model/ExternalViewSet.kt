package com.femi.gallery.domain.model

import java.net.URLDecoder

/** One photo or video that another app handed over; only its address is known. */
data class ExternalMediaRef(val uri: String, val isVideo: Boolean)

/** What an "Open with" shows: the items the sender provided, in its order, and which of them was opened. */
data class ExternalViewSet(val items: List<ExternalMediaRef>, val startIndex: Int)

/**
 * Builds the set of photos and videos to swipe through when another app opens one of them. Only what the sender
 * provided is used (the clip of an VIEW intent), never other photos of the library: a photo of a hidden album
 * must not bring its neighbours with it, and the file may not be in the library at all.
 *
 * Pure, so the rules are tested. Addresses of this app's own providers (the Locked folder, shared files) are never
 * accepted from outside: another app must not make this app read them with its own rights.
 */
object ExternalViewSets {

    /** Enough to swipe through a chat's photos; each item costs one lookup of its type when the app opens. */
    const val MAX_ITEMS = 50

    /** The address of the item the intent is about: a content or file address that is not this app's own. */
    fun isAllowedPrimary(uri: String, ownPackage: String): Boolean {
        val scheme = scheme(uri)
        return (scheme == "content" && !isOwn(uri, ownPackage)) || scheme == "file"
    }

    /**
     * The addresses of the sender's clip that may be shown beside the selected one: content addresses only, not
     * this app's own, without duplicates, in the sender's order, at most [MAX_ITEMS] around the selected one.
     * Empty when the selected item is not part of the clip, then there is nothing to swipe to.
     */
    fun candidates(selectedUri: String, clipUris: List<String>, ownPackage: String): List<String> {
        val usable = clipUris
            .filter { scheme(it) == "content" && !isOwn(it, ownPackage) }
            .distinct()
        val index = usable.indexOf(selectedUri)
        if (index < 0) return emptyList()
        if (usable.size <= MAX_ITEMS) return usable
        val from = (index - MAX_ITEMS / 2).coerceIn(0, usable.size - MAX_ITEMS)
        return usable.subList(from, from + MAX_ITEMS)
    }

    /**
     * Puts the set together once the type of each candidate is known ([mimeOf] gives null when it is not). Anything that
     * is not an image or a video is left out; the selected item always stays, as the caller already knows what it is.
     */
    fun assemble(
        selectedUri: String,
        selectedIsVideo: Boolean,
        candidates: List<String>,
        mimeOf: (String) -> String?,
    ): ExternalViewSet {
        val items = candidates.mapNotNull { uri ->
            if (uri == selectedUri) return@mapNotNull ExternalMediaRef(uri, selectedIsVideo)
            val mime = mimeOf(uri) ?: return@mapNotNull null
            when {
                mime.startsWith("image/") -> ExternalMediaRef(uri, isVideo = false)
                mime.startsWith("video/") -> ExternalMediaRef(uri, isVideo = true)
                else -> null
            }
        }
        val index = items.indexOfFirst { it.uri == selectedUri }
        return if (index < 0) {
            ExternalViewSet(listOf(ExternalMediaRef(selectedUri, selectedIsVideo)), 0)
        } else {
            ExternalViewSet(items, index)
        }
    }

    private fun scheme(uri: String): String = uri.substringBefore(':', missingDelimiterValue = "").lowercase()

    /** Whether the address names a provider of this app, as the content resolver would read it (escapes decoded). */
    private fun isOwn(uri: String, ownPackage: String): Boolean {
        val afterScheme = uri.substringAfter("://", missingDelimiterValue = "")
        val rawAuthority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        val authority = try {
            URLDecoder.decode(rawAuthority.replace("+", "%2B"), "UTF-8").lowercase()
        } catch (_: IllegalArgumentException) {
            return true // not an address anything should read
        }
        val own = ownPackage.lowercase()
        return authority == own || authority.startsWith("$own.")
    }
}
