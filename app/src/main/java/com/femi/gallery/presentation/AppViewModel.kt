package com.femi.gallery.presentation

import androidx.lifecycle.ViewModel
import com.femi.gallery.core.security.LockSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** App-wide state that screens share: whether the Locked folder is currently unlocked. */
@HiltViewModel
class AppViewModel @Inject constructor(
    val lockSession: LockSession,
) : ViewModel()
