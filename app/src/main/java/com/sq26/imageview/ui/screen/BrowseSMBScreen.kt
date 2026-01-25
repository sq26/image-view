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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.request.ImageRequest
import coil3.size.Size
import com.sq26.imageview.ui.components.SubsamplingScaleImage
import com.sq26.imageview.ui.components.ZoomableImage
import com.sq26.imageview.ui.components.isWebpAnimated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BrowseSMBScreen(name: String, path: String, fileName: String, onBack: () -> Unit) {
    val vm = hiltViewModel<BrowseSMBVM>()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.initData(context, name, path, fileName)
    }
    val fileList by remember { vm.fileList }
    val initIndex by remember { vm.initIndex }
    val pagerState = rememberPagerState { fileList.size }
    LaunchedEffect(initIndex) {
        pagerState.requestScrollToPage(initIndex)
    }
    Box {
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
        ) {
            var path by remember { mutableStateOf("") }
            var isAnimated by remember { mutableStateOf(false) }
            val smbFile = fileList[it]
            LaunchedEffect(smbFile) {
                val mPath = vm.loadSmbFileLocalPath(context, smbFile.path)
                if (smbFile.ext == "webp")
                    isAnimated = withContext(Dispatchers.IO) { isWebpAnimated(mPath) }
                path = mPath
            }
            if (path.isNotEmpty()) {
                if (isAnimated) {
                    ZoomableImage(
                        ImageRequest.Builder(LocalContext.current).data(File(path))
                            .size(Size.ORIGINAL) // 强制加载原图
                            .build()
                    )
                } else {
                    SubsamplingScaleImage(path, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}