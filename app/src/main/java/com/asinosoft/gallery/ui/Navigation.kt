package com.asinosoft.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media

@Composable
fun Navigation(nav: NavHostController, modifier: Modifier = Modifier) {
    val navigateToMedia = { media: Media -> nav.navigate("pager/${media.id}") }
    val navigateToAlbum = { album: Album -> nav.navigate("album/${album.id}") }
    val navigateToAlbumMedia = { albumId: Long, media: Media ->
        nav.navigate("album/$albumId/pager/${media.id}")
    }

    NavHost(
        modifier = modifier,
        navController = nav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable("main") {
            MainView(
                onMediaClick = navigateToMedia,
                onAlbumClick = navigateToAlbum
            )
        }

        composable(
            "pager/{imageId}",
            arguments = listOf(navArgument("imageId") { type = NavType.LongType })
        ) {
            PagerView(onClose = nav::navigateUp)
        }

        composable(
            "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { route ->
            val albumId = route.arguments?.getLong("albumId")!!

            ImageListView(
                onMediaClick = { media -> navigateToAlbumMedia(albumId, media) },
                onClose = nav::navigateUp
            )
        }

        composable(
            "album/{albumId}/pager/{imageId}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("imageId") { type = NavType.LongType }
            )
        ) {
            PagerView(onClose = nav::navigateUp)
        }
    }
}
