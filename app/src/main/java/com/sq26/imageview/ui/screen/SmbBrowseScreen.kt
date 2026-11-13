package com.sq26.imageview.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.sq26.imageview.ui.components.ZoomableImage

@Composable
fun SmbBrowseScreen(
    vm: SmbBrowseVM,onBack: () -> Unit) {
    val uiState = vm.uiState
    val pagerState = rememberPagerState { uiState.fileList.size }
    LaunchedEffect(uiState.initIndex) {
        pagerState.requestScrollToPage(uiState.initIndex)
    }
    Box {
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
        ) {
            val smbFile = uiState.fileList[it]
            ZoomableImage(
                ImageRequest.Builder(LocalContext.current).data(smbFile)
                    .size(Size.ORIGINAL) // 强制加载原图
                    .build()
            )

        }
    }
}