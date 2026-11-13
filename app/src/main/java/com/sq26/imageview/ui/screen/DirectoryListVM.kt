package com.sq26.imageview.ui.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.utils.launchIO
import com.sq26.imageview.utils.launchMain
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Immutable
data class DirectoryListUiState(
    val list: List<Directory> = listOf(),
    val showEditSMBServerDialog: Boolean = false,
    val selectItem: Directory = Directory()
)

sealed interface DirectoryListEvent {
    data class ToFileList(val name: String) : DirectoryListEvent
    data class ShowEditSMBServerDialog(val selectItem: Directory?) : DirectoryListEvent
    data object CloseEditSMBServerDialog : DirectoryListEvent

    data class AddSMBDirectory(val directory: Directory) : DirectoryListEvent
}

@HiltViewModel
class DirectoryListVM @Inject constructor(
    private val directoryDao: DirectoryDao
) : ViewModel() {

    var uiState by mutableStateOf(DirectoryListUiState())
        private set

    fun onEvent(event: DirectoryListEvent) {
        when (event) {
            is DirectoryListEvent.ShowEditSMBServerDialog -> {
                uiState = uiState.copy(
                    showEditSMBServerDialog = true,
                    selectItem = event.selectItem ?: Directory()
                )
            }

            DirectoryListEvent.CloseEditSMBServerDialog -> {
                uiState = uiState.copy(showEditSMBServerDialog = false)
            }

            is DirectoryListEvent.AddSMBDirectory -> {
                addSMBDirectory(event.directory)
            }

            else -> {}
        }
    }

    init {
        queryList()
    }

    private fun queryList() {
        viewModelScope.launchIO {
            val list = directoryDao.getAll()
            launchMain {
                uiState = uiState.copy(list = list)
            }
        }
    }

    private fun addSMBDirectory(
        directory: Directory
    ) {
        viewModelScope.launchIO {
            directoryDao.insertAll(directory)
            queryList()
        }
    }


}