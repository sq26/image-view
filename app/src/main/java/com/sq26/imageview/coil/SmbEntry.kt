package com.sq26.imageview.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.sq26.imageview.utils.SmbClientUtil
import com.sq26.imageview.utils.SmbFileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source

class SmbFileDataKeyer : Keyer<SmbFileData> {
    override fun key(
        data: SmbFileData,
        options: Options
    ): String {
        return "smb:${data.serverName}:${data.path}:${data.name}"
    }
}

class SmbFileDataFetcher(private val data: SmbFileData) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val inputStream = SmbClientUtil.getInputStream(data.path+"/"+data.name)
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

    class Factory : Fetcher.Factory<SmbFileData> {
        override fun create(
            data: SmbFileData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return SmbFileDataFetcher(data)
        }

    }
}