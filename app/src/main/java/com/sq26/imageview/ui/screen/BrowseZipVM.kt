package com.sq26.imageview.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.coil.ZipCacheManager
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.utils.SmbFileData
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseZipState(
    val fileList: List<SmbFileData> = emptyList(),
    val initIndex: Int = 0,
    val isLoading: Boolean = false
)

sealed class BrowseZipIntent {
    data class Init(val source: ZipSource, val fileName: String) : BrowseZipIntent()
}

@HiltViewModel
class BrowseZipVM @Inject constructor(
    private val zipCacheManager: ZipCacheManager
) : ViewModel() {

    var state by mutableStateOf(BrowseZipState())
        private set

    private var zipSource: ZipSource? = null

    fun onIntent(intent: BrowseZipIntent) {
        when (intent) {
            is BrowseZipIntent.Init -> initData(intent.source, intent.fileName)
        }
    }

    private fun initData(source: ZipSource, fileName: String) {
        if (zipSource == source) return
        zipSource = source
        viewModelScope.launchIO {
            state = state.copy(isLoading = true)
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
                    list.add(SmbFileData(name = name, ext = name.substringAfterLast('.', "").lowercase(), path = fullPath))
                }

                list.sortWith(compareBy { it.name })
                state = state.copy(
                    fileList = list,
                    initIndex = list.indexOfFirst { it.path == fileName }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                state = state.copy(isLoading = false)
            }
        }
    }

    override fun onCleared() {
        zipSource?.let {
            if (it is ZipSource.Smb) {
                viewModelScope.launch {
                    zipCacheManager.releaseShare(it)
                }
            }
        }
    }
}
