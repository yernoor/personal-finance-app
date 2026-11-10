package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.data.db.TagEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(embedded: Boolean = false, onBack: () -> Unit) {
    val container = dev.yernur.moneytracker.LocalAppContainer.current
    val tags by container.db.tagDao().observeLive().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<TagEntity?>(null) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = embeddedInsets(embedded),
        topBar = {
            if (!embedded) AppTopBar(title = { Text("Tags") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
        },
        floatingActionButton = { FloatingActionButton({ adding = true }, modifier = Modifier.testTag("new-tag")) { Icon(Icons.Outlined.Add, "New tag") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = LocalChromeInset.current)) {
            items(tags, key = { it.uid }) { tag ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), // lets the glass glow show through
                    headlineContent = { Text(tag.name) },
                    supportingContent = { Text("Used ${tag.useCount} times") },
                    trailingContent = {
                        Row {
                            IconButton({ renaming = tag }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton({ deleting = tag }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    },
                )
            }
        }
    }
    if (adding) TextInputDialog("New tag", "", { adding = false }) { name -> adding = false; scope.launch { container.ledger.tagNamed(name) } }
    renaming?.let { tag ->
        TextInputDialog("Rename tag", tag.name, { renaming = null }) { name -> renaming = null; scope.launch { container.ledger.renameTag(tag.uid, name) } }
    }
    deleting?.let { tag ->
        ConfirmDialog("Delete tag \"${tag.name}\"?", "Delete", { deleting = null }) { deleting = null; scope.launch { container.ledger.deleteTag(tag.uid) } }
    }
}
