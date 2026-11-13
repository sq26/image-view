package com.sq26.imageview.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sq26.imageview.R
import com.sq26.imageview.data.Directory
import com.sq26.imageview.ui.theme.ImageViewTheme
import com.sq26.imageview.utils.showToast
import kotlinx.coroutines.launch


@Composable
fun DirectoryListScreen(
    toSmbFileList: (name: String) -> Unit
) {
    val vm = hiltViewModel<DirectoryListVM>()
    val uiState = vm.uiState
    val onEvent: (DirectoryListEvent) -> Unit = { event ->
        when (event) {
            is DirectoryListEvent.ToFileList -> {
                toSmbFileList(event.name)
            }

            else -> vm.onEvent(event)
        }
    }
    DirectoryListScreenContent(
        uiState,
        onEvent = onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryListScreenContent(
    uiState: DirectoryListUiState,
    onEvent: (DirectoryListEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("目录列表") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                onEvent(DirectoryListEvent.ShowEditSMBServerDialog(null))
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_add_24),
                    contentDescription = "Add"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
        ) {
            items(uiState.list) {
                Row(
                    modifier = Modifier
                        .clickable {
                            onEvent(DirectoryListEvent.ToFileList(it.name))
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.smb_share_24px),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(it.name)
                        Text(it.path)
                    }
                    Icon(
                        painter = painterResource(R.drawable.edit_24px),
                        contentDescription = null,
                        modifier = Modifier
                            .clickable {
                                onEvent(DirectoryListEvent.ShowEditSMBServerDialog(it))
                            }
                            .size(36.dp)
                    )
                }

                HorizontalDivider()
            }
        }
    }

    if (uiState.showEditSMBServerDialog)
        EditSMBServerDialog(uiState, onEvent = onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSMBServerDialog(
    uiState: DirectoryListUiState,
    onEvent: (DirectoryListEvent) -> Unit,
) {
    val onDismissRequest by rememberUpdatedState {
        onEvent(DirectoryListEvent.CloseEditSMBServerDialog)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(uiState.selectItem.name) }
    var ip by remember { mutableStateOf(uiState.selectItem.path) }
    var port by remember { mutableStateOf(uiState.selectItem.port.toString().ifEmpty { "445" }) }
    var user by remember { mutableStateOf(uiState.selectItem.user) }
    var password by remember { mutableStateOf(uiState.selectItem.password) }
    var shareName by remember { mutableStateOf(uiState.selectItem.shareName.ifEmpty { "share" }) }
    var sharePath by remember { mutableStateOf(uiState.selectItem.sharePath) }
    val bottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = bottomSheetState) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") })
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP") })
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("端口") })
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名") })
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") })
            OutlinedTextField(
                value = shareName,
                onValueChange = { shareName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分享目录名称") })
            OutlinedTextField(
                value = sharePath,
                onValueChange = { sharePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分享目录下路径") })
            Button(onClick = {
                if (name.isEmpty()) {
                    context.showToast("名称不能为空")
                    return@Button
                }
                if (ip.isEmpty()) {
                    context.showToast("IP不能为空")
                    return@Button
                }
                if (user.isEmpty()) {
                    context.showToast("用户名不能为空")
                    return@Button
                }
                if (shareName.isEmpty()) {
                    context.showToast("分享目录名称不能为空")
                    return@Button
                }

                scope.launch {
                    onEvent(
                        DirectoryListEvent.AddSMBDirectory(
                            Directory(
                                name = name,
                                path = ip,
                                port = port.toIntOrNull() ?: 445,
                                shareName = shareName,
                                sharePath = sharePath,
                                user = user,
                                password = password
                            )
                        )
                    )
                    bottomSheetState.hide()
                }.invokeOnCompletion {
                    onDismissRequest()
                }
            }, modifier = Modifier.fillMaxWidth(0.3f)) {
                Text("确定")
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun EditSMBServerDialogPreview() {
    ImageViewTheme {
        EditSMBServerDialog(
            uiState = DirectoryListUiState(),
            onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DirectoryListScreenPreview() {
    ImageViewTheme {
        DirectoryListScreenContent(
            uiState = DirectoryListUiState(),
            onEvent = {}
        )
    }
}

