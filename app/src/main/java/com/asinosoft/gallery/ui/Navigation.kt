package com.asinosoft.gallery.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.asinosoft.gallery.model.GalleryViewModel

@Composable
fun Navigation(
    navController: NavHostController,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    NavHost(
        navController = navController,
        startDestination = Router.Home.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        composable(route = Router.Home.route) {
            MainView(viewModel) { offset ->
                navController.navigate(
                    Router.Pager.createRoute(offset)
                )
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

            PagerView(viewModel, offset) {
                navController.navigateUp()
            }
        }
    }
}
