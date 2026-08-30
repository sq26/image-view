package com.sq26.imageview.coil

import coil3.key.Keyer
import coil3.request.Options
import com.sq26.imageview.data.ZipEntryModel
import com.sq26.imageview.data.ZipSource

/**
 * Coil 缓存键生成器。
 * Coil 使用 Key 来决定一张图片是否已经加载过。
 * 对于 ZIP 内部的文件，我们需要包含：ZIP 路径 + 内部文件路径，以确保唯一性。
 */
class ZipEntryKeyer : Keyer<ZipEntryModel> {
    override fun key(data: ZipEntryModel, options: Options): String {
        return when (val source = data.source) {
            // 本地 ZIP：zip:local:[文件绝对路径]:[ZIP内路径]
            is ZipSource.Local -> "zip:local:${source.path}:${data.entryPath}"
            // SMB ZIP：zip:smb:[服务器名]:[ZIP远程路径]:[ZIP内路径]
            is ZipSource.Smb -> "zip:smb:${source.serverName}:${source.zipPath}:${data.entryPath}"
        }
    }
}
