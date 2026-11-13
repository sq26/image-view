package com.sq26.imageview.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.request.ImageRequest
import coil3.size.Size
import com.sq26.imageview.R
import com.sq26.imageview.data.ZipEntryModel
import com.sq26.imageview.data.ZipSource
import com.sq26.imageview.ui.components.ZoomableImage
import com.sq26.imageview.utils.SmbFileData

@Composable
fun BrowseZipScreen(source: ZipSource, fileName: String, onBack: () -> Unit) {
    val vm = hiltViewModel<BrowseZipVM>()
    val state = vm.state
    var isUiVisible by remember { mutableStateOf(true) }
    val view = LocalView.current
    val activity = LocalActivity.current

    val arrowBackIcon = painterResource(R.drawable.baseline_arrow_back_24)
    // 设置状态栏内容为浅色（适应黑色背景）
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousState = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false // false 表示显示白色文字/图标
            onDispose {
                controller.isAppearanceLightStatusBars = previousState
            }
        } else {
            onDispose {}
        }
    }

    LaunchedEffect(source, fileName) {
        vm.onIntent(BrowseZipIntent.Init(source, fileName))
    }

    val pagerState = rememberPagerState(initialPage = state.initIndex) { state.fileList.size }

    LaunchedEffect(state.initIndex) {
        if (state.fileList.isNotEmpty()) {
            pagerState.scrollToPage(state.initIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.fileList.isNotEmpty()) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
            ) { pageIndex ->
                ZipImageItem(
                    source = source,
                    file = state.fileList[pageIndex],
                    onClick = { isUiVisible = !isUiVisible }
                )
            }
        }

        // 顶部栏
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = arrowBackIcon,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                val currentFileName = if (state.fileList.isNotEmpty() && pagerState.currentPage < state.fileList.size) {
                    state.fileList[pagerState.currentPage].name
                } else {
                    fileName.substringAfterLast('/')
                }
                Text(
                    text = currentFileName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
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
    file: SmbFileData,
    onClick: () -> Unit
) {
    ZoomableImage(
        ImageRequest.Builder(LocalContext.current)
            .data(ZipEntryModel(source, file.path))
            .size(Size.ORIGINAL)
            .build(),
        onClick = onClick
    )
}