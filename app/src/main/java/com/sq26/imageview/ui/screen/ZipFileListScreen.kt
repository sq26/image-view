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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.sq26.imageview.R
import com.sq26.imageview.coil.ZipEntryModel
import com.sq26.imageview.ui.nav.ZipSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipFileListScreen(
    source: ZipSource,
    onBack: () -> Unit,
    toBrowseZip: (fileName: String) -> Unit
) {
    val vm = hiltViewModel<ZipFileListVM>()
    val state = vm.state

    LaunchedEffect(source) {
        vm.onIntent(ZipFileListIntent.Init(source))
    }

    BackHandler {
        if (state.directoryList.isNotEmpty()) {
            vm.onIntent(ZipFileListIntent.ToggleDirectory("", false))
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
                        if (state.directoryList.isNotEmpty()) {
                            vm.onIntent(ZipFileListIntent.ToggleDirectory("", false))
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
                    directoryList = state.directoryList,
                    onCrumbClick = { index -> vm.onIntent(ZipFileListIntent.BackToDirectory(index)) },
                    onRootClick = { vm.onIntent(ZipFileListIntent.BackToDirectory(-1)) }
                )
                ZipFileList(
                    fileList = state.fileList,
                    onItemClick = { file ->
                        if (file.isDirectory) {
                            vm.onIntent(ZipFileListIntent.ToggleDirectory(file.name, true))
                        } else {
                            toBrowseZip(file.name)
                        }
                    },
                    source = source,
                    directoryPath = state.directoryList.joinToString("")
                )
            }
            if (state.isLoading) {
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
                Icon(painterResource(R.drawable.baseline_play_arrow_24), null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                Text("根目录")
            }
        }
        itemsIndexed(directoryList) { index, crumb ->
            Row(
                modifier = Modifier.clickable { onCrumbClick(index) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.baseline_play_arrow_24), null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                Text(crumb.trim('/'))
            }
        }
    }
}

@Composable
private fun ZipFileList(
    fileList: List<FileData>,
    onItemClick: (FileData) -> Unit,
    source: ZipSource,
    directoryPath: String
) {
    LazyColumn {
        items(fileList) { file ->
            Row(
                Modifier
                    .clickable { onItemClick(file) }
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.isDirectory) {
                    Icon(painterResource(R.drawable.folder_24px), null, Modifier.padding(16.dp).size(36.dp), MaterialTheme.colorScheme.primary)
                } else {
                    ZipAsyncImage(ZipEntryModel(source, directoryPath + file.name), Modifier.size(78.dp))
                }
                Text(file.name, Modifier.padding(vertical = 8.dp).padding(end = 16.dp))
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun ZipAsyncImage(model: ZipEntryModel, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}