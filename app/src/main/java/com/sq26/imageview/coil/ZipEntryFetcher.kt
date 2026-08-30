package com.sq26.imageview.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.sq26.imageview.data.ZipEntryModel
import com.sq26.imageview.data.ZipSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

/**
 * Coil 资源抓取器。
 * 负责将 [ZipEntryModel]（表示 ZIP 内的一个图片）转换为 Coil 可以使用的 [FetchResult]。
 * 它是图片加载管道的第一环，决定了数据如何从源头（本地文件或远程 SMB）读取。
 */
class ZipEntryFetcher(
    private val model: ZipEntryModel,
    private val options: Options,
    private val zipCacheManager: ZipCacheManager
) : Fetcher {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ZipEntryFetcherEntryPoint {
        fun zipCacheManager(): ZipCacheManager
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val inputStream = when (val source = model.source) {
            is ZipSource.Local -> {
                val zipFile = ZipFile(source.path, Charset.forName("GBK"))
                val entry = zipFile.getEntry(model.entryPath)
                if (entry != null) {
                    // 对于本地文件，我们包装一下 InputStream 以便在关闭时同时关闭 ZipFile，防止资源泄露
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
            is ZipSource.Smb -> {
                // 对于 SMB 文件，直接从 ZipCacheManager 获取针对该 Entry 的随机访问流
                zipCacheManager.getSmbInputStream(source, model.entryPath)
            }
        }

        if (inputStream != null) {
            SourceFetchResult(
                source = ImageSource(
                    source = inputStream.source().buffer(),
                    fileSystem = FileSystem.SYSTEM
                ),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } else null
    }

    /**
     * 工厂类，用于在 Coil 配置中注册该抓取器。
     */
    class Factory : Fetcher.Factory<ZipEntryModel> {
        override fun create(
            data: ZipEntryModel,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            // 通过 Hilt 获取单例 ZipCacheManager
            val entryPoint = EntryPointAccessors.fromApplication(
                options.context,
                ZipEntryFetcherEntryPoint::class.java
            )
            return ZipEntryFetcher(data, options, entryPoint.zipCacheManager())
        }
    }
}
