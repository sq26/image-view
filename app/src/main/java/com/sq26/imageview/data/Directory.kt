package com.sq26.imageview.data

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Immutable
@Entity(tableName = "directory")
data class Directory(
    //名称
    @PrimaryKey val name: String = "",
    //类型
    val type: Int = TYPE_SMB,
    //根链接地址,TYPE_SMB:存ip
    val path: String = "",
    //端口
    val port: Int = 445,
    //分享目录名称,smb共享目录的顶层文件夹名称,为空展示根目录
    val shareName: String = "share",
    //分享目录路径,是基于shareName的下一级路径
    val sharePath: String = "",
    //用户名
    val user: String = "",
    //密码
    val password: String = ""
) {
    companion object {
        const val TYPE_SMB = 1 //smb服务目录
        const val TYPE_LOCAL = 2 //本地目录
    }
}

@Dao
interface DirectoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg directories: Directory)
    @Query("SELECT * FROM directory")
    fun getAll(): List<Directory>

    @Query("SELECT * FROM directory")
    fun getAllFlow(): Flow<List<Directory>>

    @Query("SELECT * FROM directory where name = :name")
    fun getDirectory(name: String): Directory
}


@Database(
    entities = [
        Directory::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun directoryDao(): DirectoryDao
}