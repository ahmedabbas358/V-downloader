package com.junkfood.seal.ui.page.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.database.objects.Subscription
import com.junkfood.seal.util.DatabaseUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    val subscriptions by DatabaseUtil.db.subscriptionDao().getAllSubscriptionsFlow().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Subscriptions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Channel or Playlist URL") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val sub = Subscription(
                                    url = urlInput,
                                    title = urlInput,
                                    lastCheckedTimestamp = System.currentTimeMillis()
                                )
                                DatabaseUtil.db.subscriptionDao().insert(sub)
                            }
                            urlInput = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Add")
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(subscriptions, key = { it.id }) { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = sub.title, style = MaterialTheme.typography.bodyLarge)
                            Text(text = sub.url, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    DatabaseUtil.db.subscriptionDao().delete(sub)
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
