package io.github.vvb2060.ims.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import io.github.vvb2060.ims.ui.theme.TensorIMSTheme

abstract class BaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TensorIMSTheme {
                content()
            }
        }
    }

    @Composable
    abstract fun content()
}