@file:Suppress("unused", "UNUSED_PARAMETER")
package com.femi.gallery.presentation.selection

import com.femi.gallery.domain.model.WriteRequest

/** Stubs for tools/verify: the parts of the selection package that the editor's ViewModel names. */
enum class WriteKind { Move, Failed }

class WriteBatch(
    val kind: WriteKind,
    val itemCount: Int,
    val requests: List<WriteRequest>,
    val onDeclined: ((requestIndex: Int) -> Unit)? = null,
    val onApproved: (() -> Unit)? = null,
)
