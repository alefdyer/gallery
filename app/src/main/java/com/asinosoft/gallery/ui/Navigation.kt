package com.asinosoft.gallery.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.asinosoft.gallery.model.GalleryViewModel

@Composable
fun Navigation(nav: NavHostController, model: GalleryViewModel = hiltViewModel()) {
    val albums by model.albums.collectAsState(initial = listOf())
    val media by model.images.collectAsState(initial = listOf())
    val storageAccounts by model.storages.collectAsState(initial = listOf())
    val albumImages by model.albumImages.collectAsState(listOf())
    val isRefreshing by model.isRescanning.collectAsState()

    NavHost(
        navController = nav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable("main") {
            MainView(
                media,
                albums,
                storageAccounts,
                onMediaClick = { image ->
                    nav.navigate("pager/" + Uri.encode(image.uri.toString()))
                },
                onAlbumClick = { album ->
                    nav.navigate("album/" + Uri.encode(album.id.toString()))
                },
                onAddStorage = model::addStorage,
                onDeleteStorage = model::deleteStorage,
                isRefreshing = isRefreshing,
                onRefresh = model::rescan
            )
        }

        composable("pager/{uri}") { route ->
            val uri = Uri.decode(route.arguments?.getString("uri")).toUri()
            val image = media.find { it.uri == uri }
            PagerView(media, current = image, onClose = nav::navigateUp)
        }

        composable("album/{albumId}") { route ->
            val albumId = route.arguments?.getString("albumId")!!.toLong()
            model.setAlbumId(albumId)

            ImageListView(
                albumImages,
                albumId = albumId,
                onClick = { image ->
                    val imagePath = Uri.encode(image.uri.toString())
                    nav.navigate("album/$albumId/pager/$imagePath")
                },
                onClose = nav::navigateUp
            )
        }

        composable("album/{albumId}/pager/{imagePath}") { route ->
            val albumId = route.arguments?.getString("albumId")!!.toLong()
            model.setAlbumId(albumId)

            val imagePath = Uri.decode(route.arguments?.getString("imagePath")).toUri()
            val image = albumImages.find { it.uri == imagePath }

            PagerView(albumImages, current = image, onClose = nav::navigateUp)
        }
    }
}
