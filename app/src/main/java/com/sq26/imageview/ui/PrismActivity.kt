package com.sq26.imageview.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.core.content.IntentCompat
import coil3.compose.AsyncImage
import com.sq26.imageview.ui.theme.ImageViewTheme

class PrismActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var uri: Uri? = null
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type?.startsWith("image/") == true) {
                (IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Parcelable::class.java
                ) as? Uri)?.let {
                    uri = it
                }
            }
        }
        setContent {
            ImageViewTheme {
                if (uri != null)
                    Test4(uri)
            }
        }
    }
}

@Composable
fun Test4(uri: Uri) {
    //对比度
    var contrast by remember { mutableFloatStateOf(1f) } // 0f..10f (1 should be default)
    //亮度
    val brightness = 0f // -255f..255f (0 should be default)
    val colorMatrix = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = uri,
            contentDescription = null,
//            modifier = Modifier.fillMaxWidth(),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        )
        Column(modifier = Modifier.align(alignment = Alignment.BottomCenter)) {
            Slider(
                value = contrast, onValueChange = { contrast = it },
                valueRange = 0f..10f
            )
            Text(contrast.toString())
            Row() {

                Button(onClick = {
                    contrast++
                }) {
                    Text("增加对比度")
                }
                Button(onClick = {
                    contrast--
                }) {
                    Text("减小对比度")
                }
            }
        }

    }
}
