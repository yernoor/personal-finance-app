package dev.yernur.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.yernur.moneytracker.ui.AppRoot
import dev.yernur.moneytracker.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme { AppRoot() }
            }
        }
    }
}
