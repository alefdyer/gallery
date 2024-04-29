package com.asinosoft.gallery.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.asinosoft.gallery.model.GalleryViewModel

@Composable
fun Navigation(
    nav: NavHostController,
    model: GalleryViewModel = hiltViewModel(),
) {
    val albums by model.albums.collectAsState()
    val images by model.images.collectAsState()
    val albumImages by model.albumImages.collectAsState()

    NavHost(
        navController = nav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {
        composable("main") {
            MainView(
                images,
                albums,
                onImageClick = { image -> nav.navigate("pager/" + Uri.encode(image.path)) },
                onAlbumClick = { album -> nav.navigate("album/" + Uri.encode(album.name)) },
            )
        }

        composable("pager/{path}") { route ->
            val path = Uri.decode(route.arguments?.getString("path"))
            val image = images.find { it.path == path }
            PagerView(images, image) { nav.navigateUp() }
        }

        composable("album/{name}") { route ->
            val name = Uri.decode(route.arguments?.getString("name"))
            model.switchToAlbum(name)
            ImageListView(albumImages) {
                val path = Uri.encode(it.path)
                nav.navigate("album/$name/pager/$path")
            }
        }

        composable("album/{name}/pager/{path}") { route ->
            val name: String? = Uri.decode(route.arguments?.getString("name"))
            name?.let { model.switchToAlbum(it) }
            val path = Uri.decode(route.arguments?.getString("path"))
            val image = albumImages.find { it.path == path }
            PagerView(albumImages, image) { nav.navigateUp() }
        }
    }
}
