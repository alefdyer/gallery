package com.asinosoft.gallery.ui

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Router(
    val route: String,
    val arguments: List<NamedNavArgument> = emptyList()
) {
    data object Photos : Router("photos")

    data object Albums : Router("albums")

    data object Pager : Router(
        route = "image/{offset}",
        arguments = listOf(navArgument("offset") {
            type = NavType.IntType
        })
    ) {
        fun createRoute(offset: Int) = "image/$offset"
    }
}
