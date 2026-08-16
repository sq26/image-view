package com.sq26.imageview.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.request.ImageRequest
import coil3.size.Size
import com.sq26.imageview.ui.components.ZoomableImage
import com.sq26.imageview.coil.ZipEntryModel
import com.sq26.imageview.ui.nav.ZipSource

@Composable
fun BrowseZipScreen(source: ZipSource, fileName: String, onBack: () -> Unit) {
    val vm = hiltViewModel<BrowseZipVM>()
    val state = vm.state

    LaunchedEffect(source, fileName) {
        vm.onIntent(BrowseZipIntent.Init(source, fileName))
    }

    val pagerState = rememberPagerState(initialPage = state.initIndex) { state.fileList.size }

    LaunchedEffect(state.initIndex) {
        if (state.fileList.isNotEmpty()) {
            pagerState.scrollToPage(state.initIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.fileList.isNotEmpty()) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
            ) { pageIndex ->
                ZipImageItem(
                    source = source,
                    file = state.fileList[pageIndex]
                )
            }
        }
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ZipImageItem(
    source: ZipSource,
    file: FileData
) {
    ZoomableImage(
        ImageRequest.Builder(LocalContext.current)
            .data(ZipEntryModel(source, file.path))
            .size(Size.ORIGINAL)
            .build()
    )
}