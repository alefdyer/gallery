package com.asinosoft.gallery.ui

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
    NavHost(navController = navController, startDestination = Router.Home.route) {
        composable(route = Router.Home.route) {
            MainView(viewModel) { offset ->
                navController.navigate(
                    Router.Pager.createRoute(offset)
                )
            }
        }
        composable(route = Router.Pager.route, arguments = Router.Pager.arguments) {
            val offset = it.arguments?.getInt("offset") ?: 0

            PagerView(viewModel, offset) {
                navController.navigateUp()
            }
        }
    }
}
