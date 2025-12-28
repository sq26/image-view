package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sq26.imageview.ui.screen.DirectoryListScreen
import kotlinx.serialization.Serializable

@Serializable
data object DirectoryList

@Composable
fun MainNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DirectoryList) {
        composable<DirectoryList> { backStackEntry ->
            DirectoryListScreen()
        }
    }
}