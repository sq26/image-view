package com.sq26.imageview.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.ui.nav.ZipSource
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ZipEntryModel(
    val source: ZipSource,
    val entryPath: String
)

class ZipEntryFetcher(
    private val model: ZipEntryModel,
    private val options: Options,
    private val directoryDao: DirectoryDao
) : Fetcher {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ZipEntryFetcherEntryPoint {
        fun directoryDao(): DirectoryDao
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val inputStream = when (val source = model.source) {
            is ZipSource.Local -> {
                val zipFile = ZipFile(source.path, Charset.forName("GBK"))
                val entry = zipFile.getEntry(model.entryPath)
                if (entry != null) {
                    // 对于本地文件，我们包装一下 InputStream 以便在关闭时同时关闭 ZipFile
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
                val smbStream = getSmbInputStream(source)
                val zis = ZipInputStream(smbStream, Charset.forName("GBK"))
                var entry: ZipEntry?
                var found = false
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == model.entryPath) {
                        found = true
                        break
                    }
                }
                if (found) zis else {
                    zis.close()
                    null
                }
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

    private suspend fun getSmbInputStream(source: ZipSource.Smb): InputStream {
        val directory = directoryDao.getDirectory(source.serverName)
        val client = SMBClient()
        val connection = client.connect(directory.path, directory.port)
        val session = connection.authenticate(
            AuthenticationContext(
                directory.user,
                directory.password.toCharArray(),
                null
            )
        )
        val share = session.connectShare(directory.shareName) as DiskShare
        return share.openFile(
            source.zipPath,
            setOf(AccessMask.GENERIC_READ),
            null,
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).inputStream
    }

    class Factory : Fetcher.Factory<ZipEntryModel> {
        override fun create(
            data: ZipEntryModel,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            val entryPoint = EntryPointAccessors.fromApplication(
                options.context,
                ZipEntryFetcherEntryPoint::class.java
            )
            return ZipEntryFetcher(data, options, entryPoint.directoryDao())
        }
    }
}
