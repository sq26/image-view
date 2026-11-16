package com.sq26.imageview.ui.screen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.coil.ZipCacheManager
import com.sq26.imageview.data.SmbZipEntryModel
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.ui.nav.NavKeyFactory
import com.sq26.imageview.ui.nav.SmbZipFileList
import com.sq26.imageview.utils.SmbFileData
import com.sq26.imageview.utils.SmbZipUtil
import com.sq26.imageview.utils.launchIO
import com.sq26.imageview.utils.launchMain
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ZipFileListState(
    val showInitFailureDialog: Boolean = false,
    val fileList: List<SmbZipEntryModel> = emptyList(),
    val directoryList: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ZipFileListEvent {
    data class Init(val source: ZipSource) : ZipFileListEvent()
    data class ToggleDirectory(val name: String, val isEnter: Boolean) : ZipFileListEvent()
    data class BackToDirectory(val index: Int) : ZipFileListEvent()
}

@HiltViewModel(assistedFactory = SmbZipFileListVM.Factory::class)
class SmbZipFileListVM @AssistedInject constructor(
    @Assisted val navKey: SmbZipFileList,
    private val zipCacheManager: ZipCacheManager
) : ViewModel() {

    var uiState by mutableStateOf(ZipFileListState())
        private set

    init {
        initData()
    }

    private fun initData() {
        viewModelScope.launchIO {
            val initSuccess = SmbZipUtil.initSmbZip(navKey.data.filePath())
            if (!initSuccess) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(showInitFailureDialog = true)
                }
                return@launchIO
            }
            val fileList = SmbZipUtil.queryZipEntryInfoList()
                .map { SmbZipEntryModel(source = navKey.data, entry = it) }
            launchMain {
                uiState = uiState.copy(fileList = fileList)
            }
        }
    }

    fun onEvent(event: ZipFileListEvent){
        when(event){
            is ZipFileListEvent.ToggleDirectory -> {
                val newDirList = uiState.directoryList.toMutableList()
                if (event.isEnter) newDirList.add(event.name) else newDirList.removeLastOrNull()
                uiState = uiState.copy(directoryList = newDirList)
                updateList2()
            }
            is ZipFileListEvent.BackToDirectory -> {
                val newDirList = uiState.directoryList.take(event.index + 1)
                uiState = uiState.copy(directoryList = newDirList)
                updateList2()
            }
            else -> {}
        }
    }

    private fun updateList2(){
        viewModelScope.launchIO {
            val fileList = SmbZipUtil.queryZipEntryInfoList(uiState.directoryList.joinToString("/"))
                .map { SmbZipEntryModel(source = navKey.data, entry = it) }
            launchMain {
                uiState = uiState.copy(fileList = fileList)
            }
        }
    }

    @AssistedFactory
    interface Factory : NavKeyFactory<SmbZipFileList, SmbZipFileListVM>

    private var zipSource: ZipSource? = null
    private var allFilePathList = mutableListOf<String>()

    fun onIntent(intent: ZipFileListEvent) {
        when (intent) {
            is ZipFileListEvent.Init -> initData(intent.source)
            is ZipFileListEvent.ToggleDirectory -> {
                val newDirList = uiState.directoryList.toMutableList()
                if (intent.isEnter) newDirList.add(intent.name) else newDirList.removeLastOrNull()
                uiState = uiState.copy(directoryList = newDirList)
                updateList()
            }

            is ZipFileListEvent.BackToDirectory -> {
                val newDirList = uiState.directoryList.take(intent.index + 1)
                uiState = uiState.copy(directoryList = newDirList)
                updateList()
            }
        }
    }

    private fun initData(source: ZipSource) {
        if (zipSource == source) return
        zipSource = source
        viewModelScope.launchIO {
            uiState = uiState.copy(isLoading = true)
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
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    private fun updateList() {
        val list = mutableListOf<SmbFileData>()
        val directoryPath = uiState.directoryList.joinToString("")
        allFilePathList.forEach { fullPath ->
            if (directoryPath == fullPath) return@forEach
            if (!fullPath.startsWith(directoryPath)) return@forEach
            val relative = fullPath.removePrefix(directoryPath)
            val isDirectory = relative.endsWith("/")
            val slashCount = relative.count { it == '/' }
            val isDirectChild = if (isDirectory) slashCount == 1 else slashCount == 0
            if (!isDirectChild) return@forEach
            val name = if (isDirectory) relative else relative.substringAfterLast('/')
            list.add(
                SmbFileData(
                    name = name,
                    ext = name.substringAfterLast('.', "").lowercase(),
                    isDirectory = isDirectory
                )
            )
        }
        list.sortWith(compareBy({ !it.isDirectory }, { it.name }))
//        uiState = uiState.copy(fileList = list)
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

