package com.sq26.imageview.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sq26.imageview.R
import com.sq26.imageview.data.SmbZipEntryModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbZipFileListScreen(
    vm: SmbZipFileListVM,
    onBack: () -> Unit,
    toBrowseZip: (fileName: String) -> Unit
) {
    val uiState = vm.uiState


    BackHandler {
        if (uiState.directoryList.isNotEmpty()) {
            vm.onIntent(ZipFileListEvent.ToggleDirectory("", false))
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("压缩包内容") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.directoryList.isNotEmpty()) {
                            vm.onIntent(ZipFileListEvent.ToggleDirectory("", false))
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(painterResource(R.drawable.baseline_arrow_back_24), null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column {
                Breadcrumbs(
                    directoryList = uiState.directoryList,
                    onCrumbClick = { index -> vm.onIntent(ZipFileListEvent.BackToDirectory(index)) },
                    onRootClick = { vm.onIntent(ZipFileListEvent.BackToDirectory(-1)) }
                )
                ZipFileList(
                    fileList = uiState.fileList,
                    onItemClick = { file ->
                        if (file.entry.isDirectory) {
                            vm.onIntent(ZipFileListEvent.ToggleDirectory(file.entry.name, true))
                        } else {
                            toBrowseZip(file.entry.name)
                        }
                    }
                )
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun Breadcrumbs(
    directoryList: List<String>,
    onCrumbClick: (Int) -> Unit,
    onRootClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Row(
                modifier = Modifier.clickable { onRootClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.baseline_play_arrow_24),
                    null,
                    Modifier.size(16.dp),
                    MaterialTheme.colorScheme.primary
                )
                Text("根目录")
            }
        }
        itemsIndexed(directoryList) { index, crumb ->
            Row(
                modifier = Modifier.clickable { onCrumbClick(index) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.baseline_play_arrow_24),
                    null,
                    Modifier.size(16.dp),
                    MaterialTheme.colorScheme.primary
                )
                Text(crumb.trim('/'))
            }
        }
    }
}

@Composable
private fun ZipFileList(
    fileList: List<SmbZipEntryModel>,
    onItemClick: (SmbZipEntryModel) -> Unit
) {
    val state = rememberLazyListState()
    LazyColumn(state = state) {
        items(fileList) { file ->
            Row(
                Modifier
                    .clickable { onItemClick(file) }
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.entry.isDirectory) {
                    Icon(
                        painterResource(R.drawable.folder_24px),
                        null,
                        Modifier
                            .padding(16.dp)
                            .size(36.dp),
                        MaterialTheme.colorScheme.primary
                    )
                } else {
                    ZipAsyncImage(
                        model = file,
                        modifier = Modifier.size(78.dp),
                        isScrolling = state.isScrollInProgress
                    )
                }
                Text(file.entry.name, Modifier
                    .padding(vertical = 8.dp)
                    .padding(end = 16.dp))
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun ZipAsyncImage(
    model: SmbZipEntryModel,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 监听滑动状态，决定加载策略
    val request = androidx.compose.runtime.remember(model, isScrolling) {
        coil3.request.ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (isScrolling) {
                    // 滑动过程中：只从缓存读取，不触发耗时的网络/磁盘写入 IO。
                    // 这样可以确保列表滑动极度丝滑，不会因为抢占 IO 带宽而产生掉帧或卡顿。
                    diskCachePolicy(coil3.request.CachePolicy.READ_ONLY)
                    memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        // 使用针对压缩包的专用占位图
        placeholder = painterResource(R.drawable.smb_share_24px)
    )
}
