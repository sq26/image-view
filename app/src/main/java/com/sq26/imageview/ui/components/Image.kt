package com.sq26.imageview.ui.components

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import com.sq26.imageview.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ZoomableImage(model: Any) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                boxSize = it
            }
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onDoubleClick = {
                    val target = if (zoom > 1f) 1f else 2f
                    val animator = ValueAnimator.ofFloat(zoom, target)
                    animator.addUpdateListener {
                        zoom = it.animatedValue as Float
                    }
                    animator.duration = 100
                    animator.interpolator = LinearInterpolator()
                    animator.start()
                }) {}
            .pointerInput(Unit) {
                detectTransformGesturesAndConsume(
                    onGesture = { centroid, pan, gestureZoom, gestureRotate, consume ->
                        val oldScale = zoom
                        val newScale = zoom * gestureZoom
                        val tOffset =
                            (offset + centroid / oldScale).rotateBy(gestureRotate) -
                                    (centroid / newScale + pan / oldScale)
                        //是否消耗事件
                        var b = true
                        //限制不能往小了缩放,也不能太大
                        zoom = if (newScale < 1f) 1f else if (newScale > 3f) 3f else newScale
                        var x = tOffset.x
                        val y = 0f
                        //左边界,最大偏移度
                        val lBoundary = (boxSize.width - boxSize.width * zoom) * 0.5f
                        //偏移值*缩放比 小于左边界就超过了左边界
                        if (tOffset.x * zoom < lBoundary) {
                            x = lBoundary / zoom
                            b = false
                        }
                        //右边界,最大偏移度
                        val rBoundary = (boxSize.width * zoom - boxSize.width) * 0.5f
                        //偏移值*缩放比 大于右边界就超过了右边界
                        if (tOffset.x * zoom > rBoundary) {
                            x = rBoundary / zoom
                            b = false
                        }
                        offset = Offset(x, y)
                        //缩放正在缩放就消耗事件
                        if (zoom != newScale)
                            b = true
                        consume(b)
                    }
                )
            }, contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                translationX = -offset.x * zoom
//                    translationY = -offset.y * zoom
                translationY = 0f
                scaleX = zoom
                scaleY = zoom
                //缩放内容的偏移百分比,0.5f标识缩放中心为屏幕中心,只有这样才能在缩放时保持纵向居中
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        )
    }
}


/**官方示例
 * 围绕原点旋转给定偏移，旋转角度以度为单位。
 *
 * 正角度表示围绕右手二维笛卡尔坐标轴逆时针旋转
 * 坐标系。
 *
 * See: [Rotation matrix](https://en.wikipedia.org/wiki/Rotation_matrix)
 */
fun Offset.rotateBy(angle: Float): Offset {
    val angleInRadians = angle * (PI / 180)
    val cos = cos(angleInRadians)
    val sin = sin(angleInRadians)
    return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
}

//官方detectTransformGestures复制修改版
//增加了控制是否消耗事件的回调consume(true),标识消耗事件
suspend fun PointerInputScope.detectTransformGesturesAndConsume(
    panZoomLock: Boolean = false,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, consume: (Boolean) -> Unit) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(
                            centroid,
                            panChange,
                            zoomChange,
                            effectiveRotation
                        ) { hasConsume ->
                            if (hasConsume)
                                event.changes.fastForEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                        }
                    }

                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}

