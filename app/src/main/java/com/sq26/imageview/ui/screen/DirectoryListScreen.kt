package com.sq26.imageview.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sq26.imageview.R
import com.sq26.imageview.data.Directory
import com.sq26.imageview.data.DirectoryDao
import com.sq26.imageview.ui.theme.ImageViewTheme
import com.sq26.imageview.utils.showToast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryListScreen() {
    val vm = hiltViewModel<DirectoryListVM>()
    val list = vm.list.collectAsStateWithLifecycle(listOf())
    var showEditSMBServerDialog by remember { mutableStateOf(false) }
    var selectItem by remember { mutableStateOf<Directory?>(null) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("目录列表") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                selectItem = null
                showEditSMBServerDialog = true
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
            items(list.value) {
                Column(
                    Modifier
                        .clickable {
                            selectItem = it
                            showEditSMBServerDialog = true
                        }
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(it.name)
                    Text(it.path)
                }
                HorizontalDivider()
            }
        }
    }

    if (showEditSMBServerDialog)
        EditSMBServerDialog(selectItem) {
            showEditSMBServerDialog = false
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSMBServerDialog(directory: Directory?, onDismissRequest: () -> Unit) {
    val vm = hiltViewModel<DirectoryListVM>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(directory?.name.orEmpty()) }
    var ip by remember { mutableStateOf(directory?.path.orEmpty()) }
    var user by remember { mutableStateOf(directory?.user.orEmpty()) }
    var password by remember { mutableStateOf(directory?.password.orEmpty()) }
    var shareName by remember { mutableStateOf(directory?.shareName.orEmpty()) }
    var sharePath by remember { mutableStateOf(directory?.sharePath.orEmpty()) }
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
                    vm.addSMBDirectory(name, ip, user, password,shareName,sharePath)
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ImageViewTheme {
        DirectoryListScreen()
    }
}