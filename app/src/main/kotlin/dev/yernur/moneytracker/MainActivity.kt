package dev.yernur.moneytracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.data.security.RecentsMode
import dev.yernur.moneytracker.ui.LockScreen
import dev.yernur.moneytracker.ui.AppRoot
import dev.yernur.moneytracker.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyApp).container
        askForNotificationsOnce()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme {
                    val locked by container.lock.locked.collectAsStateWithLifecycle()
                    val pin by container.settings.pin.collectAsStateWithLifecycle()
                    Box {
                        // Kept composed under the lock screen, so unlocking returns to the same screen.
                        Box(Modifier.blur(if (covered) 30.dp else 0.dp)) { AppRoot() }
                        pin?.takeIf { locked }?.let { LockScreen(container.lock, it.length) }
                    }
                }
            }
        }
    }

    /** Blur mode: true while the app is not in front, so the thumbnail the system takes for recents shows a blurred app. Unverified that every launcher takes it after this is drawn. */
    private var covered by mutableStateOf(false)

    override fun onPause() {
        super.onPause()
        val mode = (application as MoneyApp).container.settings.recentsMode.value
        covered = mode == RecentsMode.Blur
        // Hide: FLAG_SECURE only while the app is not in front, so screenshots still work inside it.
        if (mode == RecentsMode.Hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        covered = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        (application as MoneyApp).container.lock.onBackground()
    }

    override fun onStart() {
        super.onStart()
        (application as MoneyApp).container.lock.onForeground()
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
