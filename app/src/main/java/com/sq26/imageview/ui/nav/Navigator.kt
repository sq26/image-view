package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * 导航控制器，负责通过更新 NavigationState 来处理导航事件（前进和回退）。
 * 在 Navigation 3 中，它是唯一的事件提交入口。
 */
class Navigator(val backStack: NavBackStack<NavKey>) {
    /**
     * 导航到指定路由。
     * 如果路由是顶级路由（在 topLevelRoutes 中定义），则切换当前激活的栈。
     * 否则，将该路由推入当前活跃栈的末尾。
     */
    fun navigate(route: NavKey) {
        backStack.add(route)
    }

    /**
     * 处理回退请求。
     * 如果当前活跃栈已经回退到根部，则尝试切换回起始顶级路由。
     * 否则，从当前栈中移除最后一个条目。
     */
    fun goBack() {
        backStack.removeLastOrNull()
    }
}

fun interface NavKeyFactory<K : NavKey, VM : ViewModel> {
    fun create(navKey: K): VM
}

@Composable
inline fun <reified K: NavKey ,reified VM : ViewModel,reified F: NavKeyFactory<K,VM>> hiltNavViewModel(key: K): VM
= hiltViewModel<VM,F>(
    creationCallback = {factory->
        (factory as NavKeyFactory<K,VM>).create(key)
    }
)