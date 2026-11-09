package dev.yernur.moneytracker.walletlogger

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Debug screen: access status, the allow-list, seen packages, the captured entries, export and clear. Framework views only. */
class MainActivity : Activity() {
    private lateinit var store: LogStore
    private lateinit var status: TextView
    private lateinit var allowed: EditText
    private lateinit var seen: TextView
    private lateinit var log: TextView
    private lateinit var pause: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LogStore(this)
        // Needed for the silent status notification (Android 13+). Without it the logger still works, only the notification is missing.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this)
        allowed = EditText(this).apply { setText(store.allowedPackages().joinToString("\n")) }
        seen = TextView(this)
        log = TextView(this).apply { setTextIsSelectable(true) }
        column.addView(status)
        column.addView(button("Open notification access settings") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        column.addView(button("Open this app's info (\"Allow restricted settings\" is here if Android hides the switch)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        })
        pause = button("") {
            store.setReadingEnabled(!store.readingEnabled())
            refresh()
        }
        column.addView(pause)
        column.addView(TextView(this).apply { text = "\nPackages whose notifications are read (one per line):" })
        column.addView(allowed)
        column.addView(button("Save packages") {
            store.setAllowedPackages(allowed.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            refresh()
        })
        column.addView(TextView(this).apply { text = "\nOther apps seen (package name and count only, content never read):" })
        column.addView(seen)
        column.addView(button("Export to a file") { exportFile() })
        column.addView(button("Clear everything") { store.clear(); refresh() })
        column.addView(TextView(this).apply { text = "\nCaptured:" })
        column.addView(log)
        // Edge-to-edge is enforced (targetSdk 36): keep the content out from under the status and navigation bars.
        val scroll = ScrollView(this).apply { addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val manager = getSystemService(NotificationManager::class.java)
        val granted = manager.isNotificationListenerAccessGranted(ComponentName(this, WalletListenerService::class.java))
        pause.text = if (store.readingEnabled()) "Reading: ON (tap to pause)" else "Reading: PAUSED (tap to resume)"
        StatusNotification.update(this)
        status.text = if (granted) "Notification access: ON" else "Notification access: OFF"
        seen.text = store.seenPackages().entries.sortedByDescending { it.value }.joinToString("\n") { "${it.key}  x${it.value}" }.ifEmpty { "(none yet)" }
        val format = DateFormat.getDateTimeInstance()
        log.text = store.entries().asReversed().take(50).joinToString("\n\n") {
            "${format.format(Date(it.postTime))}  ${it.packageName}  id=${it.notificationId}${if (it.isGroupSummary) "  [summary]" else ""}\n" +
                "title: ${it.title}\ntext: ${it.text}\nbig: ${it.bigText}"
        }.ifEmpty { "(nothing captured yet)" }
    }

    private fun exportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "walletlogger-export.jsonl")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Framework activity result API; this tool avoids extra dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || uri == null) return
        contentResolver.openOutputStream(uri)?.use { it.write(store.exportText().toByteArray()) }
        Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        StatusNotification.update(this)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private companion object {
        const val REQUEST_EXPORT = 1
        const val REQUEST_NOTIFICATIONS = 2
    }
}
