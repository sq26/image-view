package com.sq26.imageview.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.coil.ZipCacheManager
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class ZipFileListState(
    val fileList: List<FileData> = emptyList(),
    val directoryList: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ZipFileListIntent {
    data class Init(val source: ZipSource) : ZipFileListIntent()
    data class ToggleDirectory(val name: String, val isEnter: Boolean) : ZipFileListIntent()
    data class BackToDirectory(val index: Int) : ZipFileListIntent()
}

@HiltViewModel
class ZipFileListVM @Inject constructor(
    private val zipCacheManager: ZipCacheManager
) : ViewModel() {

    var state by mutableStateOf(ZipFileListState())
        private set

    private var zipSource: ZipSource? = null
    private var allFilePathList = mutableListOf<String>()

    fun onIntent(intent: ZipFileListIntent) {
        when (intent) {
            is ZipFileListIntent.Init -> initData(intent.source)
            is ZipFileListIntent.ToggleDirectory -> {
                val newDirList = state.directoryList.toMutableList()
                if (intent.isEnter) newDirList.add(intent.name) else newDirList.removeLastOrNull()
                state = state.copy(directoryList = newDirList)
                updateList()
            }
            is ZipFileListIntent.BackToDirectory -> {
                val newDirList = state.directoryList.take(intent.index + 1)
                state = state.copy(directoryList = newDirList)
                updateList()
            }
        }
    }

    private fun initData(source: ZipSource) {
        if (zipSource == source) return
        zipSource = source
        viewModelScope.launchIO {
            state = state.copy(isLoading = true)
            try {
                allFilePathList.clear()
                // 使用随机访问模式快速获取所有文件条目
                val entries = zipCacheManager.getZipEntries(source)
                allFilePathList.addAll(entries.map { it.path })
                
                // 如果是 SMB 资源，启动后台预加载任务填充磁盘缓存
                if (source is ZipSource.Smb) {
                    zipCacheManager.startPreload(source)
                }
                updateList()
            } catch (e: Exception) {
                state = state.copy(error = e.message)
            } finally {
                state = state.copy(isLoading = false)
            }
        }
    }

    private fun updateList() {
        val list = mutableListOf<FileData>()
        val directoryPath = state.directoryList.joinToString("")
        allFilePathList.forEach { fullPath ->
            if (directoryPath == fullPath) return@forEach
            if (!fullPath.startsWith(directoryPath)) return@forEach
            val relative = fullPath.removePrefix(directoryPath)
            val isDirectory = relative.endsWith("/")
            val slashCount = relative.count { it == '/' }
            val isDirectChild = if (isDirectory) slashCount == 1 else slashCount == 0
            if (!isDirectChild) return@forEach
            val name = if (isDirectory) relative else relative.substringAfterLast('/')
            list.add(FileData(name = name, ext = name.substringAfterLast('.', "").lowercase(), isDirectory = isDirectory))
        }
        list.sortWith(compareBy({ !it.isDirectory }, { it.name }))
        state = state.copy(fileList = list)
    }

    override fun onCleared() {
        zipSource?.let {
            if (it is ZipSource.Smb) {
                viewModelScope.launch {
                    // 退出页面时停止预加载，并释放连接引用
                    zipCacheManager.stopPreload(it)
                    zipCacheManager.releaseShare(it)
                }
            }
        }
    }
}

