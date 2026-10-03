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
import com.asinosoft.gallery.model.DateFilter

@Composable
fun Navigation(nav: NavHostController, modifier: Modifier = Modifier) {
    val navigateToMedia = { media: Media, filters: Set<String>, date: DateFilter? ->
        nav.navigate("pager/${media.id}/${filters.joinToString(",")}/$date")
    }
    val navigateToAlbum = { album: Album -> nav.navigate("album/${album.id}") }
    val navigateToAlbumMedia = { albumId: Long, media: Media, filters: Set<String>, date: DateFilter? ->
        nav.navigate("album/$albumId/pager/${media.id}/${filters.joinToString(",")}/$date")
    }
    val navigateToSettings = { nav.navigate("settings") }

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
                onAlbumClick = navigateToAlbum,
                onSettingsClick = navigateToSettings,
            )
        }

        composable(
            "pager/{imageId}/{filters}/{date}",
            arguments = listOf(
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp
            )
        }

        composable(
            "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { route ->
            val albumId = route.arguments?.getLong("albumId")!!

            AlbumView(
                onMediaClick = { media, filters, date ->
                    navigateToAlbumMedia(
                        albumId,
                        media,
                        filters,
                        date
                    )
                },
                onClose = nav::navigateUp
            )
        }

        composable(
            "album/{albumId}/pager/{imageId}/{filters}/{date}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; nullable = true }
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp
            )
        }

        composable("settings") {
            SettingsView(onClose = nav::navigateUp)
        }
    }
}
