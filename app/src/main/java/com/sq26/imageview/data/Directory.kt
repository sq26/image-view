package com.sq26.imageview.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "directory")
data class Directory(
    @PrimaryKey val name: String = "",
    val type: Int = TYPE_SMB,
    val path: String,
    val port: Int = 445,
    val shareName: String = "share",
    val sharePath: String = "",
    val user: String = "",
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