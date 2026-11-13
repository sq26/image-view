package com.sq26.imageview.ui.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sq26.imageview.ui.nav.NavKeyFactory
import com.sq26.imageview.ui.nav.SmbBrowse
import com.sq26.imageview.utils.SmbClientUtil
import com.sq26.imageview.utils.SmbFileData
import com.sq26.imageview.utils.launchIO
import com.sq26.imageview.utils.launchMain
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@Immutable
data class SmbBrowseUiState(
    val initIndex: Int = 0,
    val fileList: List<SmbFileData> = listOf()
)

@HiltViewModel(assistedFactory = SmbBrowseVM.Factory::class)
class SmbBrowseVM @AssistedInject constructor(
    @Assisted val navKey: SmbBrowse
) : ViewModel() {

    var uiState by mutableStateOf(SmbBrowseUiState())
        private set

    init {
        initData()
    }

    private fun initData() {
        viewModelScope.launchIO {
            val fileList = SmbClientUtil.queryFileDataList(navKey.path)
            val index = fileList.indexOfFirst { it.name == navKey.fileName }
            launchMain {
                uiState = uiState.copy(initIndex = index, fileList = fileList)
            }
        }
    }

    @AssistedFactory
    interface Factory : NavKeyFactory<SmbBrowse, SmbBrowseVM>

}