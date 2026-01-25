package com.sq26.imageview.ui.screen

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class ZipFileListVM @Inject constructor() : ViewModel() {

    val directoryList = mutableStateListOf<String>()
    val fileList = mutableStateOf<List<FileData>>(listOf())

    var zipFile: ZipFile? = null

    var rootPath = ""
    fun initData(path: String) {
        viewModelScope.launchIO {
            if (zipFile == null) {
                rootPath = path
                zipFile = ZipFile(rootPath, Charset.forName("GBK"))
                getAllFilePath()
            }
        }
    }

    private val allFilePathList = mutableListOf<String>()
    fun getAllFilePath() {
        viewModelScope.launchIO {
            allFilePathList.clear()
            val entries = zipFile?.entries()
            if (entries != null)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val fileName = entry.name
                    allFilePathList.add(fileName)
                }
            updateList()
        }
    }

    fun updateList() {
        viewModelScope.launchIO {
            val list = mutableListOf<FileData>()
            val directoryPath = directoryList.joinToString("")
            allFilePathList.forEach { fullPath ->
                if (directoryPath == fullPath) return@forEach
                if (!fullPath.startsWith(directoryPath)) return@forEach
                val relative = fullPath.removePrefix(directoryPath)
                val isDirectory = relative.endsWith("/")
                val slashCount = relative.count { it == '/' }
                val isDirectChild = when {
                    isDirectory -> slashCount == 1
                    else -> slashCount == 0
                }
                if (!isDirectChild) return@forEach
                val name =
                    if (isDirectory) relative else relative.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                list.add(FileData(name = name, ext = ext, isDirectory = isDirectory))
            }
            list.sortWith(compareBy({ !it.isDirectory }, { it.name }))
            Log.i("TAG", "initZipData: $list")
            fileList.value = list
        }
    }

    suspend fun loadZipImageLocalPath(path: String): String {
        return withContext(Dispatchers.IO) {
            val name = directoryList.joinToString("") + path
            Log.i("TAG", "path: $path")
            val localFile = File(rootPath + "_", name)
            Log.i("TAG", "rootPath: $rootPath")
            Log.i("TAG", "loadZipImageLocalPath: ${localFile.canonicalPath}")
            localFile.parentFile?.mkdirs()
            if (localFile.exists()) {
                return@withContext localFile.canonicalPath
            } else {
                localFile.createNewFile()
            }

            zipFile?.getEntry(name)?.let { entry ->
                zipFile!!.getInputStream(entry).use { inputStream ->
                    localFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read <= 0) break
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
            localFile.canonicalPath
        }
    }


}
