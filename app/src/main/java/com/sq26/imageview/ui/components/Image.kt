package com.sq26.imageview.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.launch
import java.io.RandomAccessFile
import kotlin.math.abs

@Composable
fun ZoomableImage(model: Any) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(1f, tween(300)) }
                                launch { offset.animateTo(Offset.Zero, tween(300)) }
                            } else {
                                val targetScale = 3f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val relTap = tapOffset - center
                                val targetOffset = -relTap * (targetScale - 1f)
                                launch { scale.animateTo(targetScale, tween(300)) }
                                launch { offset.animateTo(targetOffset, tween(300)) }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var zoomAccumulator = 1f
                    var panAccumulator = Offset.Zero

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.fastAny { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoomAccumulator *= zoomChange
                                panAccumulator += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoomAccumulator) * centroidSize
                                val panMotion = panAccumulator.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                                    val boxCenter = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                    val relativeCentroid = centroid - boxCenter
                                    
                                    val nextOffset = offset.value + (relativeCentroid - offset.value) * (1 - zoomChange) + panChange
                                    
                                    val maxX = (boxSize.width * (newScale - 1f)) / 2f
                                    val maxY = (boxSize.height * (newScale - 1f)) / 2f
                                    
                                    val targetOffset = Offset(
                                        nextOffset.x.coerceIn(-maxX, maxX),
                                        nextOffset.y.coerceIn(-maxY, maxY)
                                    )

                                    val canScrollHorizontal = if (scale.value > 1f) {
                                        val atLeftEdge = offset.value.x >= maxX - 5f && panChange.x > 0
                                        val atRightEdge = offset.value.x <= -maxX + 5f && panChange.x < 0
                                        atLeftEdge || atRightEdge
                                    } else {
                                        true
                                    }

                                    val consumeEvent = (scale.value > 1f && !canScrollHorizontal) || zoomChange != 1f

                                    if (consumeEvent) {
                                        // 手势期间使用 snapTo，实现“跟手”效果
                                        scope.launch {
                                            scale.snapTo(newScale)
                                            offset.snapTo(targetOffset)
                                        }
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
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                }
        )
    }
}

@Composable
fun SubsamplingScaleImage(image: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember { SubsamplingScaleImageView(context).apply {
        setDoubleTapZoomDuration(150)
    } }
    LaunchedEffect(image) {
        view.setImage(ImageSource.uri(image))
    }
    AndroidView(factory = { view }, modifier = modifier)
}

//判断是否是动画webp
fun isWebpAnimated(filePath: String): Boolean {
    val file = RandomAccessFile(filePath, "r")
    try {
        if (file.length() < 30) return false

        val header = ByteArray(30)
        file.read(header)

        // 检查是否为 WebP (RIFF....WEBP)
        val isWebp = String(header.sliceArray(0..3)) == "RIFF" &&
                String(header.sliceArray(8..11)) == "WEBP"

        if (!isWebp) return false

        // 检查是否为扩展格式 VP8X
        val isVp8x = String(header.sliceArray(12..15)) == "VP8X"
        if (isVp8x) {
            // VP8X 的第 21 个字节（从0开始）的第 2 位（bit 1）表示是否有动画
            val flags = header[20].toInt()
            return (flags and 0x02) != 0
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        file.close()
    }
    return false
}
