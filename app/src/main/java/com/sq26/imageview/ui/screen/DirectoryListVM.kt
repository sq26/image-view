package com.sq26.imageview.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.utils.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DirectoryListVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    val list = directoryDao.getAllFlow()

    fun addSMBDirectory(
        directory: Directory
    ) {
        viewModelScope.launchIO {
            directoryDao.insertAll(
                directory
            )
        }
    }


}