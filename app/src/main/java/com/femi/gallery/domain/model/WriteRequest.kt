package com.femi.gallery.domain.model

import android.content.IntentSender

/**
 * A system confirmation dialog the user must approve before MediaStore lets us modify files we
 * don't own (trash, favorite, delete). The UI launches [intentSender] and waits for the result.
 *
 * This is the one Android type that crosses into the domain layer; it is an opaque token to
 * everything except the screen that launches it.
 */
class WriteRequest(val intentSender: IntentSender)
