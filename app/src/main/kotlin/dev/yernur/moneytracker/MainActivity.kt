package dev.yernur.moneytracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import dev.yernur.moneytracker.ui.AppRoot
import dev.yernur.moneytracker.ui.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyApp).container
        askForNotificationsOnce()
        lifecycleScope.launch { container.dailyWealthRefresh() }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme { AppRoot() }
            }
        }
    }

    /** Android 13+ needs a runtime grant before limit warnings can notify; ask on the first launch only. */
    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted || prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
