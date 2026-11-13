package com.sq26.imageview.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.sq26.imageview.data.SmbZipEntryModel
import com.sq26.imageview.utils.SmbClientUtil
import com.sq26.imageview.utils.SmbZipUtil
import com.sq26.imageview.utils.ZipRandomAccessHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry

class SmbZipFileDataKeyer : Keyer<SmbZipEntryModel> {
    override fun key(
        data: SmbZipEntryModel,
        options: Options
    ): String {
        return "smb:${data.source.serverName}:${data.source.path}:${data.source.name}:${data.entry.path}"
    }
}

class SmbZipFileDataFetcher(private val data: SmbZipEntryModel) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val inputStream = SmbZipUtil.getEntryInputStream(data.entry)
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

    class Factory : Fetcher.Factory<SmbZipEntryModel> {
        override fun create(
            data: SmbZipEntryModel,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return SmbZipFileDataFetcher(data)
        }

    }
}