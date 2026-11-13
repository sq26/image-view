package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.ui.screen.BrowseZipScreen
import com.sq26.imageview.ui.screen.DirectoryListScreen
import com.sq26.imageview.ui.screen.SmbBrowseScreen
import com.sq26.imageview.ui.screen.SmbBrowseVM
import com.sq26.imageview.ui.screen.SmbFileListScreen
import com.sq26.imageview.ui.screen.SmbFileListVM
import com.sq26.imageview.ui.screen.SmbZipFileListScreen
import com.sq26.imageview.ui.screen.SmbZipFileListVM
import com.sq26.imageview.utils.SmbFileData
import kotlinx.serialization.Serializable

/**
 * 路由定义。
 * 在 Navigation 3 中，所有路由都需要实现 NavKey 接口，以便支持类型安全和状态持久化。
 * Nav 3 会自动处理 Serializable 类到 Bundle 的转换，因此不再需要手动编写 NavType。
 */
@Serializable
data object DirectoryList : NavKey

@Serializable
data class SmbFileList(val path: String) : NavKey

@Serializable
data class SmbBrowse(val path: String, val fileName: String) : NavKey

@Serializable
data class SmbZipFileList(val data: SmbFileData) : NavKey

@Serializable
data class BrowseZip(val source: ZipSource, val fileName: String) : NavKey

@Composable
fun MainNavHost() {
    val backStack = rememberNavBackStack(DirectoryList)
    // 2. 创建 Navigator，它是 UI 层触发导航的唯一接口。
    val navigator = remember { Navigator(backStack) }

    // 3. 定义 entryProvider。它将 NavKey（路由）解析为具体的 UI 条目。
    // 在这里，我们可以直接从 key 对象中获取类型安全的参数。
    val entryProvider = entryProvider {
        // DirectoryList -> 目录列表屏幕
        entry<DirectoryList> {
            DirectoryListScreen(toSmbFileList = { name ->
                navigator.navigate(SmbFileList(name))
            })
        }

        // FileList -> 文件列表屏幕
        entry<SmbFileList> { key ->
            val vm = hiltNavViewModel<SmbFileList, SmbFileListVM, SmbFileListVM.Factory>(key)
            SmbFileListScreen(
                vm = vm,
                onBack = {
                    navigator.goBack()
                },
                toSmbBrowse = { path, fileName ->
                    navigator.navigate(SmbBrowse(path, fileName))
                },
                toBrowseZip = { data ->
                    navigator.navigate(SmbZipFileList(data))
                }
            )
        }

        // Browse -> SMB 图片预览屏幕
        entry<SmbBrowse> { key ->
            val vm = hiltNavViewModel<SmbBrowse, SmbBrowseVM, SmbBrowseVM.Factory>(key)
            SmbBrowseScreen(
                vm = vm,
                onBack = {
                    navigator.goBack()
                })
        }

        // ZipFileList -> Zip 内部文件列表屏幕
        entry<SmbZipFileList> { key ->
            val vm = hiltNavViewModel<SmbZipFileList, SmbZipFileListVM, SmbZipFileListVM.Factory>(key)
            SmbZipFileListScreen(
                vm = vm,
                onBack = {
                    navigator.goBack()
                },
                toBrowseZip = { fileName ->
//                    navigator.navigate(BrowseZip(key.source, fileName))
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
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider
    )
}
