package com.sq26.imageview.ui.screen

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectoryListVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    val list = directoryDao.getAllFlow()

    fun addSMBDirectory(name: String, ip: String, user: String, password: String,shareName:String,sharePath:String) {
        viewModelScope.launchIO {
            directoryDao.insertAll(
                Directory(
                    name = name,
                    type = Directory.TYPE_SMB,
                    path = ip,
                    shareName = shareName,
                    sharePath = sharePath,
                    user = user,
                    password = password
                )
            )
        }
    }


}