package com.sq26.imageview.ui.screen

import android.util.Log
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.request.ImageRequest
import coil3.size.Size
import com.sq26.imageview.ui.components.ZoomableImage

@Composable
fun BrowseScreen(name: String, path: String, fileName: String, onBack: () -> Unit) {
    val vm = hiltViewModel<BrowseVM>()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.initData(name, path, fileName)
    }
    val fileList by remember { vm.fileList }
    val initIndex by remember { vm.initIndex }
    val pagerState = rememberPagerState { fileList.size }
    LaunchedEffect(initIndex) {
        Log.i("TAG", "BrowseScreen: $initIndex")
//        pagerState.requestScrollToPage(initIndex)
        pagerState.scrollToPage(initIndex)
    }
    Box {
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
        ) {
            var path by remember { mutableStateOf("") }
            val smbPath = fileList[it]
            LaunchedEffect(smbPath) {
                path = vm.loadSmbImageLocalPath(context, smbPath)
            }
            if (path.isNotEmpty())
                ZoomableImage(
                    ImageRequest.Builder(LocalContext.current).data(path)
                        .size(Size.ORIGINAL) // 强制加载原图
                        .build()
                )
        }
    }
}