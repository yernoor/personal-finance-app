package dev.yernur.moneytracker.walletlogger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LogStore(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 96, 32, 32)
        }
        status = TextView(this)
        allowed = EditText(this).apply { setText(store.allowedPackages().joinToString("\n")) }
        seen = TextView(this)
        log = TextView(this).apply { setTextIsSelectable(true) }
        column.addView(status)
        column.addView(button("Open notification access settings") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        column.addView(button("Open this app's info (for \"Allow restricted settings\")") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        })
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
        setContentView(ScrollView(this).apply { addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().contains(packageName)
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

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private companion object {
        const val REQUEST_EXPORT = 1
    }
}
