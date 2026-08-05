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
    val navigateToMedia = { media: Media, filters: Set<String> ->
        nav.navigate("pager/${media.id}/${filters.joinToString(",")}")
    }
    val navigateToAlbum = { album: Album -> nav.navigate("album/${album.id}") }
    val navigateToAlbumMedia = { albumId: Long, media: Media, filters: Set<String> ->
        nav.navigate("album/$albumId/pager/${media.id}/${filters.joinToString(",")}")
    }
    val navigateToDate = { year: Int, month: Int, day: Int ->
        nav.navigate("dateView/$year/$month/$day")
    }
    val navigateToDateMedia = { year: Int, month: Int, day: Int, media: Media, filters: Set<String> ->
        nav.navigate("dateView/$year/$month/$day/pager/${media.id}/${filters.joinToString(",")}")
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
                onDateClick = navigateToDate,
                onSettingsClick = navigateToSettings,
            )
        }

        composable(
            "pager/{imageId}/{filters}",
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
                onMediaClick = { media, filters -> navigateToAlbumMedia(albumId, media, filters) },
                onClose = nav::navigateUp
            )
        }

        composable(
            "album/{albumId}/pager/{imageId}/{filters}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
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
            "dateView/{year}/{month}/{day}",
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
                navArgument("day") { type = NavType.IntType }
            )
        ) { route ->
            val year = route.arguments?.getInt("year") ?: 0
            val month = route.arguments?.getInt("month") ?: 0
            val day = route.arguments?.getInt("day") ?: 0

            DateView(
                year = year,
                month = month,
                day = day,
                onMediaClick = { curYear, curMonth, curDay, media, filters ->
                    navigateToDateMedia(curYear, curMonth, curDay, media, filters)
                },
                onClose = nav::navigateUp
            )
        }

        composable(
            "dateView/{year}/{month}/{day}/pager/{imageId}/{filters}",
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
                navArgument("day") { type = NavType.IntType },
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" }
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
