package com.asinosoft.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asinosoft.gallery.model.GalleryViewModel

@Composable
fun Navigation(nav: NavHostController, model: GalleryViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val albums by model.albums.collectAsState(initial = listOf())
    val media by model.images.collectAsState(initial = listOf())
    val storages by model.storages.collectAsState(initial = listOf())
    val albumImages by model.albumImages.collectAsState(listOf())
    val isRefreshing by model.isRescanning.collectAsState()

    NavHost(
        modifier = modifier,
        navController = nav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable("main") {
            MainView(
                images = media,
                albums = albums,
                storages = storages,
                onMediaClick = { image -> nav.navigate("pager/${image.id}") },
                onAlbumClick = { album -> nav.navigate("album/${album.id}") },
                onShare = { selection -> model.share(selection, context) },
                onDelete = { selection, callback -> model.delete(selection, context, callback) },
                onAddTag = model::addToAlbum,
                onCreateTag = model::addToNewAlbum,
                onRemoveTag = model::removeFromAlbum,
                onCheckStorage = model::checkStorage,
                onAddStorage = model::addStorage,
                onDeleteStorage = model::deleteStorage,
                isRefreshing = isRefreshing,
                onRefresh = { model.rescan(storages) }
            )
        }

        composable(
            "pager/{imageId}",
            arguments = listOf(navArgument("imageId") { type = NavType.LongType })
        ) { route ->
            val imageId = route.arguments?.getLong("imageId")!!
            val image = media.find { it.id == imageId }
            PagerView(
                items = media,
                current = image,
                onEdit = { mediaId -> model.edit(mediaId, context) },
                onShare = { selection -> model.share(selection, context) },
                onDelete = { selection, callback -> model.delete(selection, context, callback) },
                onClose = nav::navigateUp
            )
        }

        composable(
            "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { route ->
            val albumId = route.arguments?.getLong("albumId")!!
            model.setAlbumId(albumId)

            ImageListView(
                albums = albums,
                images = albumImages,
                albumId = albumId,
                onClick = { image -> nav.navigate("album/$albumId/pager/${image.id}") },
                onClose = nav::navigateUp,
                onShare = { selection -> model.share(selection, context) },
                onDelete = { selection, callback -> model.delete(selection, context, callback) },
                onAddTag = { selection, albumId -> model.addToAlbum(selection, albumId) },
                onCreateTag = { selection, albumName -> model.addToNewAlbum(selection, albumName) },
                onRemoveTag = { selection, albumId -> model.removeFromAlbum(selection, albumId) }
            )
        }

        composable(
            "album/{albumId}/pager/{imageId}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("imageId") { type = NavType.LongType }
            )
        ) { route ->
            val albumId = route.arguments?.getLong("albumId")!!
            model.setAlbumId(albumId)

            val imageId = route.arguments?.getLong("imageId")
            val image = albumImages.find { it.id == imageId }

            PagerView(
                items = albumImages,
                current = image,
                onEdit = { mediaId -> model.edit(mediaId, context) },
                onShare = { selection -> model.share(selection, context) },
                onDelete = { selection, callback -> model.delete(selection, context, callback) },
                onClose = nav::navigateUp
            )
        }
    }
}
