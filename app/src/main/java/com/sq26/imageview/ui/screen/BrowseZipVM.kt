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

data class BrowseZipState(
    val fileList: List<FileData> = emptyList(),
    val initIndex: Int = 0,
    val isLoading: Boolean = false
)

sealed class BrowseZipIntent {
    data class Init(val source: ZipSource, val fileName: String) : BrowseZipIntent()
}

@HiltViewModel
class BrowseZipVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    var state by mutableStateOf(BrowseZipState())
        private set

    private var zipSource: ZipSource? = null
    private var share: DiskShare? = null

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
            val allFilePathList = mutableListOf<String>()
            try {
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

                val fileNameOnly = fileName.substringAfterLast('/')
                val directoryPath = fileName.removeSuffix(fileNameOnly)
                val list = mutableListOf<FileData>()

                allFilePathList.forEach { fullPath ->
                    if (directoryPath == fullPath) return@forEach
                    if (!fullPath.startsWith(directoryPath)) return@forEach
                    val isDirectory = fullPath.endsWith("/")
                    if (isDirectory) return@forEach
                    val relative = fullPath.removePrefix(directoryPath)
                    if (relative.count { it == '/' } != 0) return@forEach
                    val name = relative.substringAfterLast('/')
                    list.add(FileData(name = name, ext = name.substringAfterLast('.', "").lowercase(), path = fullPath))
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
