package com.sq26.imageview.utils

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sq26.imageview.AppConfig
import com.sq26.imageview.data.Directory
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InputStream

object SmbClientUtil {
    private val client = SMBClient()
    var share: DiskShare? = null
    private var directory: Directory? = null
    fun initShare(currentDirectory: Directory): Boolean {
        if (share == null || directory?.name != currentDirectory.name) {
            disconnect()
            directory = currentDirectory
            try {
                val connection = client.connect(currentDirectory.path, currentDirectory.port)
                val ac = AuthenticationContext(
                    currentDirectory.user,
                    currentDirectory.password.toCharArray(),
                    null
                )

                val session = connection.authenticate(ac)
                share = session.connectShare(currentDirectory.shareName) as DiskShare
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    fun disconnect() {
        if (share != null) {
            try {
                share?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun queryFileDataList(path: String): List<SmbFileData> {
        val list = mutableListOf<SmbFileData>()
        return share?.let { share ->
            try {
                for (information in share.list(path)) {
                    val fileName = information.fileName
                    // 过滤 "." 和 ".."
                    if (fileName == "." || fileName == "..") continue
                    val isDirectory = (information.fileAttributes and 0x10L) != 0L
                    // 获取扩展名
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (isDirectory || ext in AppConfig.extensions)
                        list.add(
                            SmbFileData(
                                name = fileName,
                                isDirectory = isDirectory,
                                ext = ext,
                                path = path,
                                serverName = directory?.name.orEmpty()
                            )
                        )
                }
            } catch (e: SMBApiException) {
                e.printStackTrace()
            }
            //对list排序,先按isDirectory=true 放前面,再按name属性排序
            list.sortWith(compareBy({ !it.isDirectory }, { it.name }))
            list.toList()
        } ?: list
    }

    fun getFile(path: String): File? {
        return share?.openFile(
            path,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }
    fun getInputStream(path: String): InputStream? {
        val file = getFile(path)
        return file?.inputStream
    }
}

@Serializable
data class SmbFileData(
    //文件名
    val name: String,
    //是否文件夹
    val isDirectory: Boolean = false,
    //类型
    val ext: String,
    //目录路径
    val path: String = "",
    //服务名
    val serverName: String = "",
) {
    fun filePath() = if (path.isEmpty()) name else "$path/$name"
    fun isZip() = ext == "zip"
}