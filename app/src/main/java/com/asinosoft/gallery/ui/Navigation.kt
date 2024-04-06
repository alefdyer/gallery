package com.asinosoft.gallery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.asinosoft.gallery.R
import com.asinosoft.gallery.model.GalleryViewModel

@Composable
fun Navigation(
    navController: NavHostController,
    model: GalleryViewModel = hiltViewModel(),
) {
    val albums by model.albums.collectAsState(listOf())
    val images by model.images.collectAsState(listOf())

    NavHost(
        navController = navController,
        startDestination = Router.Photos.route,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable(route = Router.Photos.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                MainView(images) { image ->
                    val offset = images.indexOf(image)
                    navController.navigate(Router.Pager.createRoute(offset))
                }
                ViewModeBar(onAlbums = { navController.navigate(Router.Albums.route) })
            }
        }
        composable(route = Router.Albums.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AlbumListView(albums) { album ->
                    // TODO: navigate to album
                }
                ViewModeBar(onPhotos = { navController.navigate(Router.Photos.route) })
            }
        }
        composable(route = Router.Pager.route,
            arguments = Router.Pager.arguments,
            enterTransition = {
                fadeIn(animationSpec = tween(300, easing = LinearEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300, easing = LinearEasing))
            }
        ) {
            val offset = it.arguments?.getInt("offset") ?: 0

            PagerView(images, offset) {
                navController.navigateUp()
            }
        }
    }
}

@Composable
fun ViewModeBar(
    onPhotos: (() -> Unit)? = null,
    onAlbums: (() -> Unit)? = null,
) {
    NavigationBar(
        containerColor = Color.White.copy(0.5f),
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        NavigationBarItem(
            selected = onPhotos == null,
            onClick = onPhotos ?: {},
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = stringResource(id = R.string.photos)
                )
            },
            label = { stringResource(id = R.string.photos) }
        )
        NavigationBarItem(
            selected = onAlbums == null,
            onClick = onAlbums ?: {},
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(id = R.string.albums)
                )
            },
            label = { stringResource(id = R.string.albums) }
        )
    }
}

@Preview
@Composable
fun ViewModeBarPreview() {
    Box(
        modifier = Modifier
            .size(200.dp, 400.dp)
            .background(Color.Blue),
        contentAlignment = Alignment.BottomCenter
    ) {
        ViewModeBar()
    }
}
