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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipFileListScreen(
    path: String,
    onBack: () -> Unit,
    toBrowseZip: (fileName: String) -> Unit
) {
    val vm = hiltViewModel<ZipFileListVM>()
    LaunchedEffect(path) {
        vm.initData(path)
    }
    val fileList by remember { vm.fileList }
    val directoryList = remember { vm.directoryList }
    fun handleBack() {
        if (directoryList.isNotEmpty()) {
            directoryList.removeLastOrNull()
            vm.updateList()
        } else {
            onBack()
        }
    }
    BackHandler {
        handleBack()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = {
                    handleBack()
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
                item {
                    Row(
                        modifier = Modifier.clickable {
                            directoryList.clear()
                            vm.updateList()
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
                items(directoryList) {
                    Row(
                        modifier = Modifier.clickable {
                            directoryList.removeRange(
                                fromIndex = directoryList.indexOf(it) + 1,
                                directoryList.size
                            )
                            vm.updateList()
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
                items(fileList) {
                    Row(
                        Modifier
                            .clickable {
                                if (it.isDirectory) {
                                    directoryList.add(it.name)
                                    vm.updateList()
                                } else {
                                    toBrowseZip(directoryList.joinToString("") + it.name)
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
                            ZipAsyncImage(
                                it.name,
                                modifier = Modifier.size(78.dp)
                            )
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
fun ZipAsyncImage(path: String, modifier: Modifier = Modifier) {
    val vm = hiltViewModel<ZipFileListVM>()
    var localPath by remember { mutableStateOf("") }
    LaunchedEffect(path) {
        localPath = vm.loadZipImageLocalPath(path)
    }
    if (localPath.isNotEmpty())
        AsyncImage(
            model = File(localPath),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
}