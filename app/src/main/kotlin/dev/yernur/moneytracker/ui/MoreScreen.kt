package dev.yernur.moneytracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.yernur.moneytracker.data.importer.MmbackupMapper
import dev.yernur.moneytracker.data.importer.MmbackupReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoreScreen(onCategories: () -> Unit, onTags: () -> Unit) {
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val result = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    MmbackupMapper.map(MmbackupReader.read(bytes, context.cacheDir))
                }
                container.importer.store(result)
                "Imported ${result.transactions.size} transactions, ${result.transfers.size} transfers, " +
                    "${result.accounts.size} accounts, ${result.categories.size} categories." +
                    result.warnings.take(5).joinToString("") { "\n• $it" }
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
        }
    }

    Column {
        ListItem(
            modifier = Modifier.clickable(onClick = onCategories).testTag("open-categories"),
            leadingContent = { Icon(Icons.Outlined.Category, null) }, headlineContent = { Text("Categories") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onTags).testTag("open-tags"),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, headlineContent = { Text("Tags") },
        )
        ListItem(
            modifier = Modifier.clickable { picker.launch(arrayOf("*/*")) }.testTag("import"),
            leadingContent = { Icon(Icons.Outlined.FileUpload, null) }, headlineContent = { Text("Import .mmbackup") },
            supportingContent = { Text("Adds or overwrites by id; nothing local is deleted") },
        )
    }
    message?.let { AlertDialog({ message = null }, confirmButton = { TextButton({ message = null }) { Text("OK") } }, text = { Text(it) }) }
}
