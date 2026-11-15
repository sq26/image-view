package com.sq26.imageview.utils

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import com.sq26.imageview.data.ZipEntryInfo
import com.sq26.imageview.data.ZipSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object SmbZipUtil {

    private var fileList: List<ZipEntryInfo> = listOf()
    private var path: String = ""

    private var file: File? = null
    private var ras: ZipRandomAccessHelper.RandomAccessSource? = null
    val fileDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    fun initSmbZip(path: String): Boolean {
        if (this.path != path) {
            file?.close()
            this.path = path
            file = SmbClientUtil.getFile(path) ?: return false

            ras = object : ZipRandomAccessHelper.RandomAccessSource {
                override fun read(
                    position: Long,
                    buffer: ByteArray,
                    offset: Int,
                    length: Int
                ): Int {
                    return runBlocking(fileDispatcher) {
                        file!!.read(
                            buffer,
                            position,
                            offset,
                            length
                        )
                    }
                }

                override fun size(): Long =
                    runBlocking(fileDispatcher) { file!!.fileInformation.standardInformation.endOfFile }
            }
            fileList = ZipRandomAccessHelper.listEntries(ras!!)

        }
        return true
    }

    fun queryZipEntryInfoList() = fileList

    suspend fun getEntryInputStream(entry: ZipEntryInfo): InputStream? {
        // 获取数据段的真实偏移量（跳过 Local Header）
        val dataOffset = withContext(Dispatchers.IO) {
            ZipRandomAccessHelper.getDataOffset(ras!!, entry.offset)
        }

        if (dataOffset == -1L) {
            Log.i("TAG", "getEntryInputStream: 没读到数据")
            return null
        }

        // 创建一个只读取该 Entry 范围内字节的 InputStream
        fun createRawStream(inflater: Inflater? = null): InputStream =
            object : InputStream() {
                private var currentPos = dataOffset
                private val endPos = dataOffset + entry.compressedSize

                override fun read(): Int {
                    if (currentPos >= endPos) return -1
                    val b = ByteArray(1)
                    val n = runBlocking(fileDispatcher) { file!!.read(b, currentPos, 0, 1) }
                    if (n <= 0) return -1
                    currentPos++
                    return b[0].toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (currentPos >= endPos) return -1
                    val toRead = minOf(len.toLong(), endPos - currentPos).toInt()
                    val n = runBlocking(fileDispatcher) { file!!.read(b, currentPos, off, toRead) }
                    if (n > 0) currentPos += n
                    return n
                }

                override fun close() {
                    inflater?.end()
                }
            }
        // 如果是 Deflate 压缩，需要解压流
        return if (entry.compressionMethod == ZipEntry.DEFLATED) {
            val inflater = Inflater(true)
            val rawStream = createRawStream(inflater)
            InflaterInputStream(rawStream, inflater)
        } else {
            createRawStream()
        }

    }
}