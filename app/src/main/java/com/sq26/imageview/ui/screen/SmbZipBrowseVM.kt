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
import com.sq26.imageview.ui.nav.SmbZipBrowse
import com.sq26.imageview.utils.SmbFileData
import com.sq26.imageview.utils.SmbZipUtil
import com.sq26.imageview.utils.launchIO
import com.sq26.imageview.utils.launchMain
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

data class SmbZipBrowseState(
    val fileList: List<SmbZipEntryModel> = emptyList(),
    val initIndex: Int = 0,
    val isLoading: Boolean = false
)

sealed class SmbZipBrowseEvent {
    data class Init(val source: ZipSource, val fileName: String) : SmbZipBrowseEvent()
}

@HiltViewModel(assistedFactory = SmbZipBrowseVM.Factory::class)
class SmbZipBrowseVM @AssistedInject constructor(
    @Assisted val navKey: SmbZipBrowse,
    private val zipCacheManager: ZipCacheManager
) : ViewModel() {

    var uiState by mutableStateOf(SmbZipBrowseState())
        private set

    init {
        initData()
    }

    private fun initData() {
        viewModelScope.launchIO {
            val path =
                if (navKey.filePath.contains("/")) navKey.filePath.substringBeforeLast("/") else ""
            Log.i("TAG", "initData: path:$path filePath:${navKey.filePath}")
            val fileList = SmbZipUtil.queryZipEntryInfoList(path)
                .filter { !it.isDirectory }
                .map { SmbZipEntryModel(source = navKey.data, entry = it) }

            launchMain {
                uiState = uiState.copy(
                    fileList = fileList,
                    initIndex = fileList.indexOfFirst { it.entry.path == navKey.filePath })
            }
        }
    }

    @AssistedFactory
    interface Factory : NavKeyFactory<SmbZipBrowse, SmbZipBrowseVM>

    private var zipSource: ZipSource? = null

    fun onIntent(intent: SmbZipBrowseEvent) {
        when (intent) {
            is SmbZipBrowseEvent.Init -> initData(intent.source, intent.fileName)
        }
    }

    private fun initData(source: ZipSource, fileName: String) {
        if (zipSource == source) return
        zipSource = source
        viewModelScope.launchIO {
            uiState = uiState.copy(isLoading = true)
            try {
                val entries = zipCacheManager.getZipEntries(source)
                val allFilePathList = entries.map { it.path }

                val fileNameOnly = fileName.substringAfterLast('/')
                val directoryPath = fileName.removeSuffix(fileNameOnly)
                val list = mutableListOf<SmbFileData>()

                allFilePathList.forEach { fullPath ->
                    if (directoryPath == fullPath) return@forEach
                    if (!fullPath.startsWith(directoryPath)) return@forEach
                    val isDirectory = fullPath.endsWith("/")
                    if (isDirectory) return@forEach
                    val relative = fullPath.removePrefix(directoryPath)
                    if (relative.count { it == '/' } != 0) return@forEach
                    val name = relative.substringAfterLast('/')
                    list.add(
                        SmbFileData(
                            name = name,
                            ext = name.substringAfterLast('.', "").lowercase(),
                            path = fullPath
                        )
                    )
                }

                list.sortWith(compareBy { it.name })
                uiState = uiState.copy(
//                    fileList = list,
                    initIndex = list.indexOfFirst { it.path == fileName }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    override fun onCleared() {
//        zipSource?.let {
//            if (it is ZipSource.Smb) {
//                viewModelScope.launch {
//                    zipCacheManager.releaseShare(it)
//                }
//            }
//        }
    }
}
