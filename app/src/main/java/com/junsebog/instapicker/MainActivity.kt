package com.junsebog.instapicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.junsebog.instapicker.feature.picking.ui.PickingRoute
import com.junsebog.instapicker.ui.theme.InstapickerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host. `@AndroidEntryPoint` lets Hilt inject the picking ViewModel. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InstapickerTheme {
                PickingRoute()
            }
        }
    }
}
