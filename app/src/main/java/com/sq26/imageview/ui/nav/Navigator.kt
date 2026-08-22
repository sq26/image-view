package com.sq26.imageview.ui.nav

import androidx.navigation3.runtime.NavKey

/**
 * 导航控制器，负责通过更新 NavigationState 来处理导航事件（前进和回退）。
 * 在 Navigation 3 中，它是唯一的事件提交入口。
 */
class Navigator(val state: NavigationState){
    /**
     * 导航到指定路由。
     * 如果路由是顶级路由（在 topLevelRoutes 中定义），则切换当前激活的栈。
     * 否则，将该路由推入当前活跃栈的末尾。
     */
    fun navigate(route: NavKey){
        if (route in state.backStacks.keys){
            // 顶级路由：直接切换当前显示的顶级栈
            state.topLevelRoute = route
        } else {
            // 普通路由：在当前活跃的栈中添加新页面
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    /**
     * 处理回退请求。
     * 如果当前活跃栈已经回退到根部，则尝试切换回起始顶级路由。
     * 否则，从当前栈中移除最后一个条目。
     */
    fun goBack(){
        val currentStack = state.backStacks[state.topLevelRoute] ?:
        error("未找到路由 ${state.topLevelRoute} 对应的栈")
        val currentRoute = currentStack.last()

        // 如果我们已经位于当前顶级路由的根部，则回退到应用定义的起始路由栈
        if (currentRoute == state.topLevelRoute){
            state.topLevelRoute = state.startRoute
        } else {
            // 否则弹出当前页
            currentStack.removeLastOrNull()
        }
    }
}
