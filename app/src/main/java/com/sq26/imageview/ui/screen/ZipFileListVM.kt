package com.sq26.imageview.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.ui.nav.ZipSource
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
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
    private val directoryDao: DirectoryDao
) : ViewModel() {

    var state by mutableStateOf(ZipFileListState())
        private set

    private var zipSource: ZipSource? = null
    private var allFilePathList = mutableListOf<String>()
    private var share: DiskShare? = null

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
                when (source) {
                    is ZipSource.Local -> {
                        ZipFile(source.path, Charset.forName("GBK")).use { zip ->
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                allFilePathList.add(entries.nextElement().name)
                            }
                        }
                    }
                    is ZipSource.Smb -> {
                        getSmbInputStream(source).use { isStream ->
                            ZipInputStream(isStream, Charset.forName("GBK")).use { zis ->
                                var entry: ZipEntry?
                                while (zis.nextEntry.also { entry = it } != null) {
                                    allFilePathList.add(entry!!.name)
                                }
                            }
                        }
                    }
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

    private suspend fun getSmbInputStream(source: ZipSource.Smb): InputStream = withContext(Dispatchers.IO) {
        if (share == null) {
            val directory = directoryDao.getDirectory(source.serverName)
            val client = SMBClient()
            val connection = client.connect(directory.path, directory.port)
            val session = connection.authenticate(AuthenticationContext(directory.user, directory.password.toCharArray(), null))
            share = session.connectShare(directory.shareName) as DiskShare
        }
        share!!.openFile(source.zipPath, setOf(AccessMask.GENERIC_READ), null, setOf(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN, null).inputStream
    }

    override fun onCleared() {
        val shareToClose = share
        if (shareToClose != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    shareToClose.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

