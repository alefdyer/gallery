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
    val albums by model.albums.collectAsState(initial = listOf())
    val images by model.images.collectAsState(initial = listOf())
    val albumImages by model.albumImages.collectAsState(listOf())
    val isRefreshing by model.isRescanning.collectAsState()

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
                isRefreshing = isRefreshing,
                onRefresh = model::rescan
            )
        }

        composable("pager/{path}") { route ->
            val path = Uri.decode(route.arguments?.getString("path"))
            val image = images.find { it.path == path }
            PagerView(images, image) { nav.navigateUp() }
        }

        composable("album/{albumName}") { route ->
            val albumName = Uri.decode(route.arguments?.getString("albumName"))
            model.setAlbumName(albumName)

            ImageListView(albumImages) { image ->
                val imagePath = Uri.encode(image.path)
                nav.navigate("album/$albumName/pager/$imagePath")
            }
        }

        composable("album/{albumName}/pager/{imagePath}") { route ->
            val albumName = Uri.decode(route.arguments?.getString("albumName"))
            model.setAlbumName(albumName)

            val imagePath = Uri.decode(route.arguments?.getString("imagePath"))
            val image = albumImages.find { it.path == imagePath }

            PagerView(albumImages, image) { nav.navigateUp() }
        }
    }
}
