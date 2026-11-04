package com.funactivity.ppmr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.funactivity.ppmr.ui.navigation.AppNavHost
import com.funactivity.ppmr.ui.theme.FunActivityTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FunActivityTheme {
                AppNavHost()
            }
        }
    }
}
