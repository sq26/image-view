package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.sq26.imageview.ui.screen.BrowseSMBScreen
import com.sq26.imageview.ui.screen.BrowseZipScreen
import com.sq26.imageview.ui.screen.DirectoryListScreen
import com.sq26.imageview.ui.screen.FileListScreen
import com.sq26.imageview.ui.screen.ZipFileListScreen
import kotlinx.serialization.Serializable

@Serializable
data object DirectoryList : NavKey

@Serializable
data class FileList(val name: String) : NavKey

@Serializable
sealed class ZipSource {
    @Serializable
    data class Local(val path: String) : ZipSource()

    @Serializable
    data class Smb(val serverName: String, val zipPath: String) : ZipSource()
}

@Serializable
data class Browse(val name: String, val path: String, val fileName: String) : NavKey

@Serializable
data class ZipFileList(val source: ZipSource) : NavKey

@Serializable
data class BrowseZip(val source: ZipSource, val fileName: String) : NavKey

@Composable
fun MainNavHost() {
    val navigationState = rememberNavigationState(
        startRoute = DirectoryList,
        topLevelRoutes = setOf(DirectoryList)
    )
    val navigator = remember { Navigator(navigationState) }

    val entryProvider = entryProvider {
        entry<DirectoryList> {
            DirectoryListScreen(toFileList = { name ->
                navigator.navigate(FileList(name))
            })
        }

        entry<FileList> { key ->
            FileListScreen(
                name = key.name,
                onBack = {
                    navigator.goBack()
                },
                toBrowse = { path, fileName ->
                    navigator.navigate(Browse(key.name, path, fileName))
                },
                toBrowseZip = { path ->
                    navigator.navigate(ZipFileList(ZipSource.Smb(key.name, path)))
                }
            )
        }

        entry<Browse> { key ->
            BrowseSMBScreen(
                name = key.name,
                path = key.path,
                fileName = key.fileName,
                onBack = {
                    navigator.goBack()
                })
        }

        entry<ZipFileList> { key ->
            ZipFileListScreen(
                source = key.source,
                onBack = {
                    navigator.goBack()
                },
                toBrowseZip = { fileName ->
                    navigator.navigate(BrowseZip(key.source, fileName))
                }
            )
        }

        entry<BrowseZip> { key ->
            BrowseZipScreen(
                source = key.source,
                fileName = key.fileName,
                onBack = {
                    navigator.goBack()
                })
        }
    }

    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = { navigator.goBack() }
    )
}
