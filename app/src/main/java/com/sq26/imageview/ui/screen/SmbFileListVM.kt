package com.sq26.imageview.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.ui.nav.SmbFileList
import com.sq26.imageview.ui.nav.NavKeyFactory
import com.sq26.imageview.utils.SmbFileData
import com.sq26.imageview.utils.SmbClientUtil
import com.sq26.imageview.utils.launchIO
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class SmbFileListUiState(
    val showInitFailureDialog: Boolean = false,
    val hasRootDirectory: Boolean = true,
    val directoryList: List<String> = listOf(),
    val fileList: List<SmbFileData> = listOf()
)

sealed interface SmbFileListEvent {
    data object Reset : SmbFileListEvent

    data class BackDirectory(val index: Int) : SmbFileListEvent

    data class AddDirectory(val name: String) : SmbFileListEvent

    data object HandleBack : SmbFileListEvent

    data class ToBrowseZip(val data: SmbFileData) : SmbFileListEvent

    data class ToBrowse(val name: String) : SmbFileListEvent
}

@HiltViewModel(assistedFactory = SmbFileListVM.Factory::class)
class SmbFileListVM @AssistedInject constructor(
    @Assisted val navKey: SmbFileList,
    private val directoryDao: DirectoryDao,
) : ViewModel() {

    var uiState by mutableStateOf(SmbFileListUiState())
        private set

    fun onEvent(event: SmbFileListEvent) {
        when (event) {
            SmbFileListEvent.Reset -> {
                reset()
            }

            is SmbFileListEvent.BackDirectory -> {
                backDirectory(event.index + 1)
            }

            is SmbFileListEvent.AddDirectory -> {
                addDirectory(event.name)
            }

            SmbFileListEvent.HandleBack -> {
                backDirectory(uiState.directoryList.size - 1)
            }

            else -> {}
        }
    }

    init {
        initData()
    }

    private fun initData() {
        viewModelScope.launchIO {
            val directory = directoryDao.getDirectory(navKey.path)
            val initSuccess = SmbClientUtil.initShare(directory)
            if (!initSuccess) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(showInitFailureDialog = true)
                }
                return@launchIO
            }
            val directoryList = mutableListOf<String>()
            val hasRootDirectory = directory.sharePath.isEmpty()
            if (!hasRootDirectory)
                directoryList.add(directory.sharePath)
            withContext(Dispatchers.Main) {
                uiState =
                    uiState.copy(directoryList = directoryList, hasRootDirectory = hasRootDirectory)
            }
            queryList()
        }
    }

    private fun queryList() {
        viewModelScope.launchIO {
            val list = SmbClientUtil.queryFileDataList(
                uiState.directoryList.joinToString("/")
            )
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(fileList = list)
            }
        }
    }

    private fun reset() {
        viewModelScope.launchIO {
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(directoryList = listOf())
            }
            queryList()
        }
    }

    private fun backDirectory(index: Int) {
        val directoryList = uiState.directoryList.subList(0, index)
        uiState = uiState.copy(directoryList = directoryList)
        queryList()
    }

    private fun addDirectory(name: String) {
        val directoryList = uiState.directoryList.toMutableList()
        directoryList.add(name)
        uiState = uiState.copy(directoryList = directoryList)
        queryList()
    }

    @AssistedFactory
    interface Factory : NavKeyFactory<SmbFileList, SmbFileListVM>

    val directoryList = mutableStateListOf<String>()
    val fileList = mutableStateOf<List<SmbFileData>>(listOf())

    var directory: Directory? = null

    suspend fun loadSmbImageLocalPath(context: Context, path: String): String {
        return withContext(Dispatchers.IO) {
            val file = SmbClientUtil.share!!.openFile(
                directoryList.joinToString("/") + "/" + path,
                setOf(AccessMask.GENERIC_READ),
                null,
                setOf(
                    SMB2ShareAccess.FILE_SHARE_READ
                ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val localPath =
                "${directory?.name}/${directory?.shareName}/" + directoryList.joinToString("/") + "/" + path
            Log.i("TAG", "loadSmbImageLocalPath: $localPath")
            val localFile = File(context.cacheDir, localPath)
            localFile.parentFile?.mkdirs()
            if (localFile.exists()) {
                return@withContext localFile.canonicalPath
            } else {
                localFile.createNewFile()
            }
            file.inputStream.use { inputStream ->
                localFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            localFile.canonicalPath
        }
    }


    override fun onCleared() {
        Log.i("TAG", "onCleared: 注销vm")
        viewModelScope.launchIO {
            SmbClientUtil.disconnect()
        }
    }
}

