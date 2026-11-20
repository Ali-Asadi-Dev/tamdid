package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.ServiceCatalog
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.ServiceAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(catalog: ServiceCatalog, onClose: () -> Unit, onPick: (String?) -> Unit) {
    val format = LocalAppFormat.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { catalog.search(query) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.list_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    ListRow(
                        onClick = { onPick(null) },
                        leading = {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                            }
                        },
                        title = stringResource(R.string.catalog_custom),
                        subtitle = stringResource(R.string.catalog_custom_body),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(results, key = { it.id }) { service ->
                    ListRow(
                        onClick = { onPick(service.id) },
                        leading = { ServiceAvatar(service.displayName(format.language), service.color) },
                        title = service.displayName(format.language),
                        subtitle = service.category.displayName(format.language),
                    )
                }
            }
        }
    }
}
