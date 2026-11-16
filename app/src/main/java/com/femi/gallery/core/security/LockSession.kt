package com.femi.gallery.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the user has authenticated to see the Locked folder. It re-locks whenever the app
 * goes to the background or the user leaves the Locked folder.
 */
@Singleton
class LockSession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _keyLost = MutableStateFlow(false)

    /**
     * True when the key that protects the Locked folder on this phone is gone (the screen lock was removed, the
     * Keystore was reset, ...): the items cannot be opened any more, except by restoring a backup.
     */
    val keyLost: StateFlow<Boolean> = _keyLost.asStateFlow()

    fun setKeyLost(lost: Boolean) {
        _keyLost.value = lost
    }

    private val _shownInPane = MutableStateFlow(false)

    /**
     * True while the Locked folder is the album in the detail pane of the two-pane Albums screen (tablets, unfolded
     * foldables). There the folder is not a navigation destination of its own, so the navigation alone cannot tell
     * that the user is still looking at it.
     */
    val shownInPane: StateFlow<Boolean> = _shownInPane.asStateFlow()

    fun setShownInPane(shown: Boolean) {
        _shownInPane.value = shown
    }

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
