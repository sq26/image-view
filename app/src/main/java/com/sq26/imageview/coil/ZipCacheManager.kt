package com.sq26.imageview.coil

import android.content.Context
import coil3.imageLoader
import coil3.request.Options
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.data.ZipEntryInfo
import com.sq26.imageview.data.ZipEntryModel
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.utils.ZipRandomAccessHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zip 缓存与连接管理器。
 * 核心功能：
 * 1. SMB 连接池：复用同一个服务器的连接，避免重复登录，并在无引用时延迟关闭。
 * 2. ZIP 随机访问：配合 [ZipRandomAccessHelper] 实现秒开大型 ZIP 列表。
 * 3. 后台预加载：进入 ZIP 后自动在后台将图片解压并填入 Coil 磁盘缓存。
 */
@Singleton
class ZipCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryDao: DirectoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex() // 用于保护 shareMap、preloadJobs 和 entryCache 的并发访问

    // SMB 连接池：服务器名 -> 连接信息
    private val shareMap = mutableMapOf<String, ShareInfo>()
    // 正在运行的预加载任务
    private val preloadJobs = mutableMapOf<ZipSource, Job>()
    // ZIP 文件目录信息缓存，避免重复解析中央目录
    private val entryCache = mutableMapOf<ZipSource, List<ZipEntryInfo>>()

    private val keyer = ZipEntryKeyer()
    private val dummyOptions = Options(context)

    // 连接信息包装类，包含引用计数和延迟关闭任务
    private data class ShareInfo(
        val share: DiskShare,
        var refCount: Int = 0,
        var closeJob: Job? = null
    )

    private fun getCacheKey(source: ZipSource, entryPath: String): String {
        return keyer.key(ZipEntryModel(source, entryPath), dummyOptions)
    }

    /**
     * 获取 ZIP 文件的所有条目信息。
     * 优先从缓存读取，若无缓存则进行随机访问解析。
     */
    suspend fun getZipEntries(source: ZipSource): List<ZipEntryInfo> {
        mutex.withLock {
            entryCache[source]?.let { return it }
        }

        val entries = when (source) {
            is ZipSource.Local -> {
                // 本地 ZIP 使用标准库解析即可
                withContext(Dispatchers.IO) {
                    ZipFile(source.path, Charset.forName("GBK")).use { zip ->
                        zip.entries().asSequence().map { entry ->
                            ZipEntryInfo(
                                path = entry.name,
                                offset = -1,
                                compressedSize = entry.compressedSize,
                                uncompressedSize = entry.size,
                                compressionMethod = entry.method,
                                isDirectory = entry.isDirectory
                            )
                        }.toList()
                    }
                }
            }
            is ZipSource.Smb -> {
                // SMB ZIP 使用随机访问优化
                val share = acquireShare(source)
                val file = withContext(Dispatchers.IO) {
                    share.openFile(
                        source.zipPath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        setOf(SMB2ShareAccess.FILE_SHARE_READ),
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                }
                try {
                    val ras = object : ZipRandomAccessHelper.RandomAccessSource {
                        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                            return file.read(buffer, position, offset, length)
                        }
                        override fun size(): Long = file.fileInformation.standardInformation.endOfFile
                    }
                    ZipRandomAccessHelper.listEntries(ras)
                } finally {
                    file.close()
                    releaseShare(source)
                }
            }
        }
        
        mutex.withLock {
            entryCache[source] = entries
        }
        return entries
    }

    /**
     * 获取 ZIP 内特定文件的输入流。
     * 利用偏移量直接跳转读取，无需扫描整个压缩包。
     */
    suspend fun getEntryInputStream(source: ZipSource, entryPath: String): InputStream? {
        val entries = getZipEntries(source)
        val info = entries.find { it.path == entryPath } ?: return null

        return when (source) {
            is ZipSource.Local -> {
                // 本地文件的随机访问由 ZipFile 内部处理
                withContext(Dispatchers.IO) {
                    val zipFile = ZipFile(source.path, Charset.forName("GBK"))
                    val entry = zipFile.getEntry(entryPath)
                    if (entry != null) {
                        object : InputStream() {
                            private val wrapped = zipFile.getInputStream(entry)
                            override fun read() = wrapped.read()
                            override fun read(b: ByteArray) = wrapped.read(b)
                            override fun read(b: ByteArray, off: Int, len: Int) = wrapped.read(b, off, len)
                            override fun close() {
                                wrapped.close()
                                zipFile.close()
                            }
                        }
                    } else {
                        zipFile.close()
                        null
                    }
                }
            }
            is ZipSource.Smb -> {
                val share = acquireShare(source)
                val file = withContext(Dispatchers.IO) {
                    share.openFile(
                        source.zipPath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        setOf(SMB2ShareAccess.FILE_SHARE_READ),
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                }
                
                val ras = object : ZipRandomAccessHelper.RandomAccessSource {
                    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                        return file.read(buffer, position, offset, length)
                    }
                    override fun size(): Long = file.fileInformation.standardInformation.endOfFile
                }
                
                // 获取数据段的真实偏移量（跳过 Local Header）
                val dataOffset = withContext(Dispatchers.IO) {
                    ZipRandomAccessHelper.getDataOffset(ras, info.offset)
                }
                
                if (dataOffset == -1L) {
                    file.close()
                    releaseShare(source)
                    return null
                }

                // 创建一个只读取该 Entry 范围内字节的 InputStream
                val rawStream = object : InputStream() {
                    private var currentPos = dataOffset
                    private val endPos = dataOffset + info.compressedSize

                    override fun read(): Int {
                        if (currentPos >= endPos) return -1
                        val b = ByteArray(1)
                        val n = file.read(b, currentPos, 0, 1)
                        if (n <= 0) return -1
                        currentPos++
                        return b[0].toInt() and 0xFF
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (currentPos >= endPos) return -1
                        val toRead = minOf(len.toLong(), endPos - currentPos).toInt()
                        val n = file.read(b, currentPos, off, toRead)
                        if (n > 0) currentPos += n
                        return n
                    }

                    override fun close() {
                        file.close()
                        scope.launch { releaseShare(source) }
                    }
                }

                // 如果是 Deflate 压缩，需要解压流
                if (info.compressionMethod == ZipEntry.DEFLATED) {
                    InflaterInputStream(rawStream, Inflater(true))
                } else {
                    rawStream
                }
            }
        }
    }

    suspend fun getSmbInputStream(source: ZipSource.Smb, entryPath: String? = null): InputStream? {
        if (entryPath != null) return getEntryInputStream(source, entryPath)
        
        // This is still needed for reading the whole zip if necessary, but we should avoid it.
        val share = acquireShare(source)
        val file = withContext(Dispatchers.IO) {
            share.openFile(
                source.zipPath,
                setOf(AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
        }
        return file.inputStream
    }

    /**
     * 获取或创建 SMB 服务器的连接。
     * 使用引用计数，多个 ZIP 文件共用同一个连接。
     */
    suspend fun acquireShare(source: ZipSource.Smb): DiskShare = mutex.withLock {
        val shareKey = source.serverName

        val info = shareMap[shareKey]
        if (info != null) {
            info.closeJob?.cancel() // 如果连接正准备关闭，取消关闭任务
            info.closeJob = null
            info.refCount++
            return info.share
        }

        // 第一次访问，建立连接
        val directory = withContext(Dispatchers.IO) {
            directoryDao.getDirectory(source.serverName)
        }
        val client = SMBClient()
        val connection = withContext(Dispatchers.IO) {
            client.connect(directory.path, directory.port)
        }
        val session = connection.authenticate(
            AuthenticationContext(
                directory.user,
                directory.password.toCharArray(),
                null
            )
        )
        val share = session.connectShare(directory.shareName) as DiskShare
        shareMap[shareKey] = ShareInfo(share, 1)
        return share
    }

    /**
     * 释放 SMB 连接引用。
     * 当引用归零时，开启一个 5 秒的延迟任务来关闭连接。
     * 如果 5 秒内再次被访问，连接将不会被关闭，实现了平滑切换。
     */
    suspend fun releaseShare(source: ZipSource.Smb) = mutex.withLock {
        val shareKey = source.serverName
        val info = shareMap[shareKey] ?: return@withLock
        info.refCount--
        if (info.refCount <= 0) {
            info.closeJob = scope.launch {
                delay(5000) // 延迟关闭
                mutex.withLock {
                    if (info.refCount <= 0) {
                        try {
                            withContext(Dispatchers.IO) {
                                info.share.close()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        shareMap.remove(shareKey)
                    }
                }
            }
        }
    }

    /**
     * 启动后台预加载任务。
     * 进入 ZIP 列表后，自动按顺序将压缩包内的所有图片解压并存入 Coil 的磁盘缓存。
     */
    fun startPreload(source: ZipSource) {
        scope.launch {
            mutex.withLock {
                if (preloadJobs.containsKey(source)) return@withLock
                preloadJobs[source] = scope.launch {
                    try {
                        when (source) {
                            is ZipSource.Local -> {}
                            is ZipSource.Smb -> {
                                val entries = getZipEntries(source)
                                val diskCache = context.imageLoader.diskCache
                                
                                entries.forEach { entryInfo ->
                                    if (!isActive) return@launch
                                    if (!entryInfo.isDirectory && isImage(entryInfo.path)) {
                                        val key = getCacheKey(source, entryInfo.path)
                                        
                                        // 检查是否已经在磁盘缓存中，如果有了就跳过
                                        val snapshot = diskCache?.openSnapshot(key)
                                        if (snapshot != null) {
                                            snapshot.close()
                                            return@forEach
                                        }

                                        // 手动将解压后的数据流写入 Coil 磁盘缓存
                                        diskCache?.openEditor(key)?.let { editor ->
                                            try {
                                                getEntryInputStream(source, entryInfo.path)?.use { input ->
                                                    diskCache.fileSystem.write(editor.data) {
                                                        writeAll(input.source().buffer())
                                                    }
                                                    editor.commit()
                                                } ?: editor.abort()
                                            } catch (_: Exception) {
                                                editor.abort()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        mutex.withLock {
                            preloadJobs.remove(source)
                        }
                    }
                }
            }
        }
    }

    fun stopPreload(source: ZipSource) {
        scope.launch {
            mutex.withLock {
                preloadJobs[source]?.cancel()
                preloadJobs.remove(source)
            }
        }
    }

    private fun isImage(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }
}
