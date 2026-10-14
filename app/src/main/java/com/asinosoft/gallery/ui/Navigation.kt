package com.asinosoft.gallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.model.PrivateMovesViewModel
import com.asinosoft.gallery.ui.component.ManageMediaDialog
import com.asinosoft.gallery.ui.component.canAskManageMedia
import com.asinosoft.gallery.ui.component.openManageMediaSettings
import kotlinx.coroutines.flow.collectLatest
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.MediaType
import com.asinosoft.gallery.ui.component.rememberDeviceAuth

@Composable
fun Navigation(
    nav: NavHostController,
    modifier: Modifier = Modifier,
    openAlbumId: Long? = null,
    onAlbumOpened: () -> Unit = {},
) {
    LaunchedEffect(openAlbumId) {
        val albumId = openAlbumId ?: return@LaunchedEffect
        nav.navigate("album/$albumId") {
            popUpTo("main")
        }
        onAlbumOpened()
    }

    val navigateToMedia = { media: Media, filters: Set<String>, date: DateFilter?, type: MediaType? ->
        nav.navigate("pager/${pagerArgs(media, filters, date, type)}")
    }
    val navigateToAlbum = { album: Album -> nav.navigate("album/${album.id}") }
    val navigateToAlbumMedia = { albumId: Long, media: Media, filters: Set<String>, date: DateFilter?, type: MediaType? ->
        nav.navigate("album/$albumId/pager/${pagerArgs(media, filters, date, type)}")
    }
    val navigateToStorage = { storage: Storage -> nav.navigate("storage/${storage.id}") }
    val navigateToStorageMedia = { storageId: Long, media: Media, filters: Set<String>, date: DateFilter?, type: MediaType? ->
        nav.navigate("storage/$storageId/pager/${pagerArgs(media, filters, date, type)}")
    }
    val navigateToSettings = { nav.navigate("settings") }
    val reportCurrentMedia: (Long) -> Unit = { mediaId ->
        nav.previousBackStackEntry?.savedStateHandle?.set(RETURN_MEDIA_ID, mediaId)
    }
    val openPrivateAlbum = rememberDeviceAuth { nav.navigate(PRIVATE_ROUTE) }

    Box(modifier.fillMaxSize()) {
        AppNavHost(nav, navigateToMedia, navigateToAlbum, navigateToAlbumMedia, navigateToStorage,
            navigateToStorageMedia, navigateToSettings, reportCurrentMedia, openPrivateAlbum)
        PrivateMoveNotifications(
            onOpenPrivateAlbum = openPrivateAlbum,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PrivateMoveNotifications(
    onOpenPrivateAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    model: PrivateMovesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showManageMedia by remember { mutableStateOf(false) }
    val message = stringResource(R.string.moved_to_private)
    val action = stringResource(R.string.view)
    val currentOnOpen by rememberUpdatedState(onOpenPrivateAlbum)

    LaunchedEffect(model) {
        model.movedIn.collectLatest {
            if (canAskManageMedia(context)) showManageMedia = true
            val result = snackbarHostState.showSnackbar(message, action, duration = SnackbarDuration.Long)
            if (SnackbarResult.ActionPerformed == result) currentOnOpen()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 88.dp)
    )

    if (showManageMedia) {
        ManageMediaDialog(
            onOpenSettings = {
                showManageMedia = false
                openManageMediaSettings(context)
            },
            onDismiss = { showManageMedia = false }
        )
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    navigateToMedia: (Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    navigateToAlbum: (Album) -> Unit,
    navigateToAlbumMedia: (Long, Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    navigateToStorage: (Storage) -> Unit,
    navigateToStorageMedia: (Long, Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    navigateToSettings: () -> Unit,
    reportCurrentMedia: (Long) -> Unit,
    openPrivateAlbum: () -> Unit,
) {
    NavHost(
        navController = nav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        predictivePopEnterTransition = { _ ->
            if (initialState.destination.route in SLIDE_ROUTES) {
                slideInHorizontally(tween(ALBUM_SLIDE_MS, easing = LinearEasing)) {
                    -(it * ALBUM_PARALLAX).toInt()
                }
            } else {
                fadeIn(animationSpec = tween(300))
            }
        },
        predictivePopExitTransition = { _ ->
            if (initialState.destination.route in SLIDE_ROUTES) {
                slideOutHorizontally(tween(ALBUM_SLIDE_MS, easing = LinearEasing)) { it }
            } else {
                fadeOut(animationSpec = tween(300))
            }
        }
    ) {
        composable(
            "main",
            exitTransition = {
                if (targetState.destination.route in SLIDE_ROUTES) {
                    slideOutHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) {
                        -(it * ALBUM_PARALLAX).toInt()
                    }
                } else {
                    fadeOut(animationSpec = tween(300))
                }
            },
            popEnterTransition = {
                if (initialState.destination.route in SLIDE_ROUTES) {
                    slideInHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) {
                        -(it * ALBUM_PARALLAX).toInt()
                    }
                } else {
                    fadeIn(animationSpec = tween(300))
                }
            }
        ) { entry ->
            val returnMediaId by entry.returnMediaId()
            MainView(
                onMediaClick = navigateToMedia,
                onAlbumClick = navigateToAlbum,
                onStorageClick = navigateToStorage,
                onSettingsClick = navigateToSettings,
                onFavoritesClick = { nav.navigate("favorites") },
                onPrivateAlbumClick = openPrivateAlbum,
                returnMediaId = returnMediaId,
                onReturnHandled = { entry.clearReturnMediaId() }
            )
        }

        composable(
            FAVORITES_ROUTE,
            arguments = listOf(favoritesArgument),
            enterTransition = {
                slideInHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
            },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = {
                slideOutHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
            }
        ) { entry ->
            val returnMediaId by entry.returnMediaId()
            AlbumView(
                loadNeighbours = false,
                onMediaClick = { media, filters, date, type ->
                    nav.navigate("favorites/pager/${pagerArgs(media, filters, date, type)}")
                },
                onClose = nav::navigateUp,
                returnMediaId = returnMediaId,
                onReturnHandled = { entry.clearReturnMediaId() }
            )
        }

        composable(
            PRIVATE_ROUTE,
            enterTransition = {
                slideInHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
            },
            popExitTransition = {
                slideOutHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
            }
        ) {
            PrivateAlbumView(onClose = nav::navigateUp)
        }

        composable(
            "favorites/pager/$PAGER_ARGS",
            arguments = listOf(
                favoritesArgument,
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; nullable = true },
                typeArgument
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp,
                onCurrentMediaChange = reportCurrentMedia
            )
        }

        composable(
            "pager/$PAGER_ARGS",
            arguments = listOf(
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" },
                typeArgument
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp,
                onCurrentMediaChange = reportCurrentMedia
            )
        }

        composable(
            ALBUM_ROUTE,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            enterTransition = {
                if (initialState.destination.route == ALBUM_ROUTE) {
                    fadeIn(tween(ALBUM_SWAP_FADE_MS, delayMillis = ALBUM_SWAP_DELAY_MS))
                } else {
                    slideInHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
                }
            },
            exitTransition = {
                if (targetState.destination.route == ALBUM_ROUTE) {
                    fadeOut(tween(1, delayMillis = ALBUM_SWAP_DELAY_MS + ALBUM_SWAP_FADE_MS))
                } else {
                    fadeOut(animationSpec = tween(300))
                }
            },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = {
                slideOutHorizontally(tween(ALBUM_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
            }
        ) { route ->
            val albumId = route.arguments?.getLong("albumId")!!
            val returnMediaId by route.returnMediaId()

            AlbumView(
                loadNeighbours = transition.currentState == transition.targetState,
                onSwitchAlbum = { album, _ ->
                    nav.navigate("album/${album.id}") {
                        popUpTo(ALBUM_ROUTE) { inclusive = true }
                    }
                },
                onMediaClick = { media, filters, date, type ->
                    navigateToAlbumMedia(
                        albumId,
                        media,
                        filters,
                        date,
                        type
                    )
                },
                onClose = nav::navigateUp,
                returnMediaId = returnMediaId,
                onReturnHandled = { route.clearReturnMediaId() }
            )
        }

        composable(
            "album/{albumId}/pager/$PAGER_ARGS",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; nullable = true },
                typeArgument
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp,
                onCurrentMediaChange = reportCurrentMedia
            )
        }

        composable(
            "storage/{storageId}",
            arguments = listOf(
                navArgument("storageId") { type = NavType.LongType }
            )
        ) {route ->
            val storageId = route.arguments?.getLong("storageId")!!
            StorageView(
                onMediaClick = { media, filters, date, type -> navigateToStorageMedia(storageId, media, filters, date, type) },
                onClose = nav::navigateUp
            )
        }

        composable(
            "storage/{storageId}/pager/$PAGER_ARGS",
            arguments = listOf(
                navArgument("storageId") { type = NavType.LongType },
                navArgument("imageId") { type = NavType.LongType },
                navArgument("filters") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; nullable = true },
                typeArgument
            )
        ) {
            PagerView(
                onAlbumClick = navigateToAlbum,
                onClose = nav::navigateUp,
                onCurrentMediaChange = reportCurrentMedia
            )
        }

        composable("settings") {
            SettingsView(onClose = nav::navigateUp)
        }
    }
}

@Composable
private fun NavBackStackEntry.returnMediaId() =
    savedStateHandle.getStateFlow<Long?>(RETURN_MEDIA_ID, null).collectAsState()

private fun NavBackStackEntry.clearReturnMediaId() {
    savedStateHandle.set<Long?>(RETURN_MEDIA_ID, null)
}

private fun pagerArgs(media: Media, filters: Set<String>, date: DateFilter?, type: MediaType?) =
    "${media.id}/${filters.joinToString(",")}/$date" + type?.let { "?type=${it.name}" }.orEmpty()

private val typeArgument = navArgument("type") { type = NavType.StringType; nullable = true }

private val favoritesArgument = navArgument("favorites") { type = NavType.BoolType; defaultValue = true }

private const val PAGER_ARGS = "{imageId}/{filters}/{date}?type={type}"
private const val FAVORITES_ROUTE = "favorites"
private const val PRIVATE_ROUTE = "private"
private const val RETURN_MEDIA_ID = "returnMediaId"
private const val ALBUM_ROUTE = "album/{albumId}"
private val SLIDE_ROUTES = setOf(ALBUM_ROUTE, FAVORITES_ROUTE, PRIVATE_ROUTE)
private const val ALBUM_SWAP_DELAY_MS = 200
private const val ALBUM_SWAP_FADE_MS = 150
private const val ALBUM_SLIDE_MS = 300
private const val ALBUM_PARALLAX = 0.25f
