package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sq26.imageview.ui.screen.BrowseSMBScreen
import com.sq26.imageview.ui.screen.BrowseZipScreen
import com.sq26.imageview.ui.screen.DirectoryListScreen
import com.sq26.imageview.ui.screen.FileListScreen
import com.sq26.imageview.ui.screen.ZipFileListScreen
import com.sq26.imageview.utils.launchMain
import kotlinx.serialization.Serializable

@Serializable
data object DirectoryList

@Serializable
data class FileList(val name: String)

@Serializable
data class Browse(val name: String, val path: String, val fileName: String)

@Serializable
data class ZipFileList(val path: String)

@Serializable
data class BrowseZip(val path: String, val fileName: String)


@Composable
fun MainNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DirectoryList) {
        composable<DirectoryList> {
            DirectoryListScreen(toFileList = { name ->
                navController.navigate(FileList(name))
            })
        }

        composable<FileList> {
            val scope = rememberCoroutineScope()
            val params: FileList = it.toRoute()
            FileListScreen(
                name = params.name,
                onBack = {
                    scope.launchMain {
                        navController.popBackStack()
                    }
                },
                toBrowse = { path, fileName ->
                    navController.navigate(Browse(params.name, path, fileName))
                },
                toBrowseZip = { path ->
                    navController.navigate(ZipFileList(path))
                }
            )
        }

        composable<Browse> {
            val scope = rememberCoroutineScope()
            val params: Browse = it.toRoute()
            BrowseSMBScreen(
                name = params.name,
                path = params.path,
                fileName = params.fileName,
                onBack = {
                    scope.launchMain {
                        navController.popBackStack()
                    }
                })
        }

        composable<ZipFileList> {
            val scope = rememberCoroutineScope()
            val params: ZipFileList = it.toRoute()
            ZipFileListScreen(
                path = params.path,
                onBack = {
                    scope.launchMain {
                        navController.popBackStack()
                    }
                },
                toBrowseZip = { fileName ->
                    navController.navigate(BrowseZip(params.path, fileName))
                }
            )
        }

        composable<BrowseZip> {
            val scope = rememberCoroutineScope()
            val params: BrowseZip = it.toRoute()
            BrowseZipScreen(
                path = params.path,
                fileName = params.fileName,
                onBack = {
                    scope.launchMain {
                        navController.popBackStack()
                    }
                })
        }
    }
}