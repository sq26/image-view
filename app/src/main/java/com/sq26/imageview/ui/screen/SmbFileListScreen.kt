package com.sq26.imageview.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sq26.imageview.R
import com.sq26.imageview.utils.SmbFileData

@Composable
fun SmbFileListScreen(
    vm: SmbFileListVM,
    onBack: () -> Unit,
    toSmbBrowse: (path: String, fileName: String) -> Unit,
    toBrowseZip: (data: SmbFileData) -> Unit
) {
    val uiState = vm.uiState
    val onEvent: (SmbFileListEvent) -> Unit = { event ->
        when (event) {
            SmbFileListEvent.HandleBack -> {
                if (uiState.directoryList.size > if (uiState.hasRootDirectory) 0 else 1) {
                    vm.onEvent(event)
                } else {
                    onBack()
                }
            }

            is SmbFileListEvent.ToBrowseZip -> {
                toBrowseZip(event.data.copy(path = uiState.directoryList.joinToString("/")))
            }

            is SmbFileListEvent.ToBrowse -> {
                toSmbBrowse(uiState.directoryList.joinToString("/"), event.name)
            }

            else -> vm.onEvent(event)
        }
    }

    BackHandler {
        onEvent(SmbFileListEvent.HandleBack)
    }
    FileListScreenContent(uiState, onEvent)

    if (uiState.showInitFailureDialog)
        InitFailureDialog(onBack = onBack)
}

@Composable
private fun InitFailureDialog(onBack: () -> Unit) {
    AlertDialog(onDismissRequest = onBack, text = {
        Text("连接失败,请检查配置是否有误")
    }, confirmButton = {
        TextButton(onClick = onBack) {
            Text("确定")
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreenContent(uiState: SmbFileListUiState, onEvent: (SmbFileListEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = {
                    onEvent(SmbFileListEvent.HandleBack)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_arrow_back_24),
                        contentDescription = null
                    )
                }
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.hasRootDirectory)
                    item {
                        Row(
                            modifier = Modifier.clickable {
                                onEvent(SmbFileListEvent.Reset)
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_play_arrow_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("根目录")
                        }
                    }

                itemsIndexed(uiState.directoryList) { index, it ->
                    Row(
                        modifier = Modifier.clickable {
                            onEvent(SmbFileListEvent.BackDirectory(index))
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_play_arrow_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(it)
                    }
                }
            }
            LazyColumn {
                items(uiState.fileList) {
                    Row(
                        Modifier
                            .clickable {
                                if (it.isDirectory) {
                                    onEvent(SmbFileListEvent.AddDirectory(it.name))
                                } else {
                                    if (it.isZip()) {
                                        onEvent(SmbFileListEvent.ToBrowseZip(it))
                                    } else {
                                        onEvent(SmbFileListEvent.ToBrowse(it.name))
                                    }
                                }
                            }
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (it.isDirectory) {
                            Icon(
                                painter = painterResource(R.drawable.folder_24px),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            if (it.isZip()) {
                                Icon(
                                    painter = painterResource(R.drawable.folder_zip_24px),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                SMBAsyncImage(
                                    it,
                                    modifier = Modifier.size(78.dp)
                                )
                            }
                        }

                        Text(
                            it.name,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, end = 16.dp)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun SMBAsyncImage(data: SmbFileData, modifier: Modifier = Modifier) {
//    val context = LocalContext.current
//    val vm = hiltViewModel<FileListVM>()
//    var path by remember { mutableStateOf("") }
//    LaunchedEffect(smbPath) {
//        path = vm.loadSmbImageLocalPath(context, smbPath)
//    }
    if (data.name.isNotEmpty())
        AsyncImage(
            model = data,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
}