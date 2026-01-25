package com.sq26.imageview.ui.screen

import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
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
class BrowseZipVM @Inject constructor() : ViewModel() {

    val fileList = mutableStateOf<List<FileData>>(listOf())
    val initIndex = mutableIntStateOf(0)
    var zipFile: ZipFile? = null
    var rootPath = ""
    fun initData(path: String, filePath: String) {
        viewModelScope.launchIO {
            if (zipFile == null) {
                rootPath = path
                zipFile = ZipFile(rootPath, Charset.forName("GBK"))
                val fileName = filePath.substringAfterLast('/')
                val directoryPath = filePath.removeSuffix(fileName)
                val list = mutableListOf<FileData>()
                val entries = zipFile?.entries()

                if (entries != null)
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val fullPath = entry.name
                        if (directoryPath == fullPath) continue
                        if (!fullPath.startsWith(directoryPath)) continue
                        val isDirectory = fullPath.endsWith("/")
                        if (isDirectory) continue
                        val relative = fullPath.removePrefix(directoryPath)
                        val slashCount = relative.count { it == '/' }
                        val isDirectChild = slashCount == 0
                        if (!isDirectChild) continue
                        val name = relative.substringAfterLast('/')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        list.add(FileData(name = name, ext = ext, path = fullPath))
                    }

                list.sortWith(compareBy { it.name })
                fileList.value = list
                Log.i("TAG", "initData: $list")
                if (list.isNotEmpty())
                    initIndex.intValue = list.indexOfFirst { it.path == filePath }
            }
        }
    }

    suspend fun loadZipImageLocalPath(path: String): String {
        return withContext(Dispatchers.IO) {
            Log.i("TAG", "path: $path")
            val localFile = File(rootPath + "_", path)
            Log.i("TAG", "rootPath: $rootPath")
            Log.i("TAG", "loadZipImageLocalPath: ${localFile.canonicalPath}")
            localFile.parentFile?.mkdirs()
            if (localFile.exists()) {
                return@withContext localFile.canonicalPath
            } else {
                localFile.createNewFile()
            }

            zipFile?.getEntry(path)?.let { entry ->
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