package com.sq26.imageview.data

import com.sq26.imageview.utils.SmbFileData
import kotlinx.serialization.Serializable

@Serializable
sealed class ZipSource {
    @Serializable
    data class Local(val path: String) : ZipSource()

    @Serializable
    data class Smb(val serverName: String, val zipPath: String) : ZipSource()
}

data class ZipEntryModel(
    val source: ZipSource,
    val entryPath: String
)

data class SmbZipEntryModel(
    val source: SmbFileData,
    val entry: ZipEntryInfo
)

data class ZipEntryInfo(
    val name: String,
    val path: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val compressionMethod: Int,
    val isDirectory: Boolean
)
