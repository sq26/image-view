package com.sq26.imageview.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.sq26.imageview.AppConfig
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.utils.launchDefault
import com.sq26.imageview.utils.launchIO
import com.sq26.imageview.utils.launchMain
import com.sq26.imageview.utils.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeoutException
import javax.inject.Inject

@HiltViewModel
class FileListVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    val directoryList = mutableStateListOf<String>()
    val fileList = mutableStateOf<List<FileData>>(listOf())

    var directory: Directory? = null

    val hasRootDirectory = mutableStateOf(true)
    fun initData(name: String, context: Context, onBack: () -> Unit) {
        viewModelScope.launchIO {
            if (directory == null) {
                directory = directoryDao.getDirectory(name)
                hasRootDirectory.value = directory?.sharePath.isNullOrEmpty()
                linkSMB(context, onBack)
            }
        }
    }
    var share: DiskShare? = null
    fun linkSMB(context: Context, onBack: () -> Unit) {
        if (directory == null) return
        viewModelScope.launchIO {
            Log.i("TAG", "directory: $directory")
            val client = SMBClient()
            try {
                val connection = client.connect(directory!!.path, directory!!.port)
                val ac = AuthenticationContext(
                    directory!!.user,
                    directory!!.password.toCharArray(),
                    null
                )
                val session = connection.authenticate(ac)
                share = session.connectShare(directory!!.shareName) as DiskShare
                if (directory!!.sharePath.isNotEmpty())
                    directoryList.add(directory!!.sharePath)
                updateList()
            } catch (e: Exception) {
                e.printStackTrace()
                launchMain {
                    context.showToast("连接失败")
                    onBack()
                }
            }
        }
    }


    fun updateList() {
        viewModelScope.launchIO {
            share?.list(directoryList.joinToString("/"))?.let { shareFileList ->
                val list = mutableListOf<FileData>()
                for (information in shareFileList) {
                    val fileName = information.fileName
                    // 过滤 "." 和 ".."
                    if (fileName == "." || fileName == "..") continue
                    val isDirectory = (information.fileAttributes and 0x10L) != 0L
                    // 获取扩展名
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (isDirectory || ext in AppConfig.extensions)
                        list.add(
                            FileData(
                                name = fileName,
                                isDirectory = isDirectory,
                                ext = ext
                            )
                        )
                    Log.i(
                        "TAG",
                        "fileName: ${information.fileName} fileAttributes: ${information.fileAttributes}"
                    )
                }
                Log.i("TAG", "linkSMB: $list")
                fileList.value = list
            }
        }
    }

    suspend fun loadSmbImageLocalPath(context: Context, path: String): String {
        return withContext(Dispatchers.IO) {
            val file = share!!.openFile(
                directoryList.joinToString("/") + "/" + path, setOf(AccessMask.GENERIC_READ), null, setOf(
                    SMB2ShareAccess.FILE_SHARE_READ
                ), SMB2CreateDisposition.FILE_OPEN, null
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
        super.onCleared()
        viewModelScope.launchDefault {
            share?.close()
        }
    }
}

data class FileData(val name: String, val isDirectory: Boolean, val ext: String) {
    fun isZip() = ext == "zip"
}