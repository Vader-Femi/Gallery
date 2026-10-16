package com.femi.gallery.presentation.navigation

object GalleryRoutes {
    const val VIEWER = "viewer/{index}"

    fun viewer(mediaIndex: Int) = "viewer/$mediaIndex"
}
