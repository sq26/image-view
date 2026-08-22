package com.sq26.imageview.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * 创建并记住导航状态，支持在配置更改和进程死亡时持久化。
 *
 * @param startRoute 启动时的初始路由。
 * @param topLevelRoutes 顶级路由集合（例如底部导航栏的各个选项）。
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): NavigationState {

    // 使用 rememberSerializable 持久化当前的顶级路由。
    // NavKeySerializer 负责处理 NavKey 的序列化，以便存储在 SavedStateHandle 中。
    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }

    // 为每个顶级路由创建一个独立的后退栈（NavBackStack）。
    // rememberNavBackStack 会自动处理栈内路由的持久化。
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/**
 * 导航状态持有者。
 * 它管理着多个后退栈以及当前激活的顶级路由。
 *
 * @param startRoute 起始路由，用户通常通过此路由退出应用。
 * @param topLevelRoute 当前激活的顶级路由状态。
 * @param backStacks 路由到其对应后退栈的映射表。
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    // 当前显示的顶级路由。
    var topLevelRoute: NavKey by topLevelRoute

    // 当前处于活跃状态的栈标识。
    // 如果当前不是起始路由，则包含起始栈和当前活跃栈，以便支持返回到起始路由。
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

/**
 * 将 NavigationState 转换为 NavEntries，供 NavDisplay 显示。
 *
 * @param entryProvider 路由解析器，负责将 NavKey 转换为具体的 NavEntry（包含 UI 内容）。
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {

    // 为每个栈生成经过装饰的条目（NavEntries）。
    // 装饰器（如 rememberSaveableStateHolderNavEntryDecorator）负责在导航切换时保留各个屏幕的状态（如滚动位置）。
    // rememberViewModelStoreNavEntryDecorator 负责为每个条目提供独立的 ViewModelStore，
    // 确保 ViewModel 的生命周期与 NavEntry 绑定（即出栈时调用 onCleared）。
    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    // 根据当前使用的栈，扁平化所有条目并返回一个可观察的列表。
    return stacksInUse
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}
