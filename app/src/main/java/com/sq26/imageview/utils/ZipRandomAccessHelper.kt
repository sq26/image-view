package com.sq26.imageview.utils

import com.sq26.imageview.data.ZipEntryInfo
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ZIP 随机访问助手。
 * 传统的 ZipInputStream 需要从头开始顺序读取整个文件才能找到特定的 Entry。
 * 本助手利用 ZIP 文件的结构特点（索引信息存储在文件末尾），通过“随机访问（Random Access）”实现秒开。
 * 
 * ZIP 文件结构简述：
 * 1. [Local File Header 1] + [Data 1]
 * 2. [Local File Header 2] + [Data 2]
 * ...
 * 3. [Central Directory (中央目录索引区)] -> 存储了所有文件的名称、压缩信息及在文件中的偏移量。
 * 4. [End of Central Directory Record (EOCD)] -> 存储了中央目录的起始位置。
 */
object ZipRandomAccessHelper {
    private val EOCD_SIG = 0x06054b50 // EOCD 签名
    private val CENTRAL_DIR_SIG = 0x02014b50 // 中央目录头签名
    private val LOCAL_FILE_HEADER_SIG = 0x04034b50 // 本地文件头签名

    /**
     * 随机访问源接口。
     * 只要实现该接口，无论文件是在本地还是在 SMB 网络共享上，都可以进行随机读取。
     */
    interface RandomAccessSource {
        fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
        fun size(): Long
    }

    /**
     * 获取 ZIP 文件中的所有 Entry 信息。
     * 流程：找 EOCD -> 获取中央目录位置 -> 解析中央目录。
     */
    fun listEntries(source: RandomAccessSource): List<ZipEntryInfo> {
        val fileSize = source.size()
        if (fileSize < 22) return emptyList()

        // 1. 寻找 EOCD (End of Central Directory Record)
        // EOCD 通常在文件最后 22 字节，但如果有注释，可能往前偏移最多 64KB。
        val bufferSize = minOf(fileSize, 65536L + 22L).toInt()
        val buffer = ByteArray(bufferSize)
        val readCount = source.read(fileSize - bufferSize, buffer, 0, bufferSize)
        
        var eocdPos = -1
        // 从后往前搜寻签名
        for (i in readCount - 22 downTo 0) {
            if (readIntLe(buffer, i) == EOCD_SIG) {
                eocdPos = i
                break
            }
        }
        if (eocdPos == -1) return emptyList()

        // 解析 EOCD 获取中央目录的元数据
        val entriesCount = readShortLe(buffer, eocdPos + 10).toInt() and 0xFFFF
        val centralDirSize = readIntLe(buffer, eocdPos + 12).toLong() and 0xFFFFFFFFL
        val centralDirOffset = readIntLe(buffer, eocdPos + 16).toLong() and 0xFFFFFFFFL

        // 2. 读取中央目录 (Central Directory)
        val cdBuffer = ByteArray(centralDirSize.toInt())
        source.read(centralDirOffset, cdBuffer, 0, cdBuffer.size)

        val entries = mutableListOf<ZipEntryInfo>()
        var pos = 0
        // 循环解析中央目录中的每一个文件条目
        repeat(entriesCount) {
            if (pos + 46 > cdBuffer.size) return@repeat
            if (readIntLe(cdBuffer, pos) != CENTRAL_DIR_SIG) return@repeat

            val compressionMethod = readShortLe(cdBuffer, pos + 10).toInt() and 0xFFFF
            val compressedSize = readIntLe(cdBuffer, pos + 20).toLong() and 0xFFFFFFFFL
            val uncompressedSize = readIntLe(cdBuffer, pos + 24).toLong() and 0xFFFFFFFFL
            val nameLen = readShortLe(cdBuffer, pos + 28).toInt() and 0xFFFF
            val extraLen = readShortLe(cdBuffer, pos + 30).toInt() and 0xFFFF
            val commentLen = readShortLe(cdBuffer, pos + 32).toInt() and 0xFFFF
            val localHeaderOffset = readIntLe(cdBuffer, pos + 42).toLong() and 0xFFFFFFFFL

            // 文件名编码处理，中文 ZIP 通常使用 GBK
            val name = String(cdBuffer, pos + 46, nameLen, charset("GBK"))
            val isDirectory = name.endsWith("/")

            entries.add(
                ZipEntryInfo(
                    name = if (isDirectory) name else name.substringAfterLast('/'),
                    path = name,
                    offset = localHeaderOffset,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    compressionMethod = compressionMethod,
                    isDirectory = isDirectory
                )
            )

            // 跳到下一个条目位置
            pos += 46 + nameLen + extraLen + commentLen
        }

        return entries
    }

    /**
     * 根据本地文件头（Local File Header）的偏移量，计算实际数据的起始位置。
     * Local Header = 签名(4) + 固定头(26) + 文件名长度 + 扩展字段长度。
     */
    fun getDataOffset(source: RandomAccessSource, localHeaderOffset: Long): Long {
        val header = ByteArray(30)
        source.read(localHeaderOffset, header, 0, 30)
        if (readIntLe(header, 0) != LOCAL_FILE_HEADER_SIG) return -1
        
        val nameLen = readShortLe(header, 26).toInt() and 0xFFFF
        val extraLen = readShortLe(header, 28).toInt() and 0xFFFF
        
        return localHeaderOffset + 30 + nameLen + extraLen
    }

    // 读取小端序的 32 位整数
    private fun readIntLe(buffer: ByteArray, pos: Int): Int {
        return (buffer[pos].toInt() and 0xFF) or
                ((buffer[pos + 1].toInt() and 0xFF) shl 8) or
                ((buffer[pos + 2].toInt() and 0xFF) shl 16) or
                ((buffer[pos + 3].toInt() and 0xFF) shl 24)
    }

    // 读取小端序的 16 位短整数
    private fun readShortLe(buffer: ByteArray, pos: Int): Short {
        return ((buffer[pos].toInt() and 0xFF) or
                ((buffer[pos + 1].toInt() and 0xFF) shl 8)).toShort()
    }
}
