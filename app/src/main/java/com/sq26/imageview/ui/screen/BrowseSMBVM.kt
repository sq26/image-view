package com.sq26.imageview.ui.screen

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
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
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BrowseSMBVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    val fileList = mutableStateOf<List<FileData>>(listOf())
    val initIndex = mutableIntStateOf(0)
    var directory: Directory? = null
    var share: DiskShare? = null
    fun initData(context: Context, name: String, path: String, fileName: String) {
        viewModelScope.launchIO {
            directory = directoryDao.getDirectory(name)
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
                initSMBData(path, fileName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun initSMBData(path: String, fileName: String) {
        share?.list(path)?.let { shareFileList ->
            val list = mutableListOf<FileData>()
            for (information in shareFileList) {
                val fileName = information.fileName
                // 过滤 "." 和 ".."
                if (fileName == "." || fileName == "..") continue
                // 获取扩展名
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in AppConfig.extensions)
                    list.add(FileData(name = fileName, ext = ext, path = "$path/$fileName"))
            }
            list.sortWith(compareBy { it.name })
            fileList.value = list
            initIndex.intValue = list.indexOfFirst { it.path == "$path/$fileName" }
        }
    }

    suspend fun loadSmbFileLocalPath(context: Context, path: String): String {
        return withContext(Dispatchers.IO) {
            val file = share!!.openFile(
                path, setOf(AccessMask.GENERIC_READ), null, setOf(
                    SMB2ShareAccess.FILE_SHARE_READ
                ), SMB2CreateDisposition.FILE_OPEN, null
            )
            val localPath =
                "${directory?.name}/${directory?.shareName}/" + path
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
}