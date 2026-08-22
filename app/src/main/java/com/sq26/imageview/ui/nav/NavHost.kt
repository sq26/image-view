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

/**
 * 路由定义。
 * 在 Navigation 3 中，所有路由都需要实现 NavKey 接口，以便支持类型安全和状态持久化。
 * Nav 3 会自动处理 Serializable 类到 Bundle 的转换，因此不再需要手动编写 NavType。
 */
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
    // 1. 初始化导航状态。这里 DirectoryList 被定义为启动路由和唯一的顶级路由。
    val navigationState = rememberNavigationState(
        startRoute = DirectoryList,
        topLevelRoutes = setOf(DirectoryList)
    )

    // 2. 创建 Navigator，它是 UI 层触发导航的唯一接口。
    val navigator = remember { Navigator(navigationState) }

    // 3. 定义 entryProvider。它将 NavKey（路由）解析为具体的 UI 条目。
    // 在这里，我们可以直接从 key 对象中获取类型安全的参数。
    val entryProvider = entryProvider {
        // DirectoryList -> 目录列表屏幕
        entry<DirectoryList> {
            DirectoryListScreen(toFileList = { name ->
                navigator.navigate(FileList(name))
            })
        }

        // FileList -> 文件列表屏幕
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

        // Browse -> SMB 图片预览屏幕
        entry<Browse> { key ->
            BrowseSMBScreen(
                name = key.name,
                path = key.path,
                fileName = key.fileName,
                onBack = {
                    navigator.goBack()
                })
        }

        // ZipFileList -> Zip 内部文件列表屏幕
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

        // BrowseZip -> Zip 图片预览屏幕
        entry<BrowseZip> { key ->
            BrowseZipScreen(
                source = key.source,
                fileName = key.fileName,
                onBack = {
                    navigator.goBack()
                })
        }
    }

    // 4. 使用 NavDisplay 显示内容。
    // 它会根据 navigationState 中的路由序列，通过 entryProvider 渲染出当前的界面。
    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = { navigator.goBack() }
    )
}
