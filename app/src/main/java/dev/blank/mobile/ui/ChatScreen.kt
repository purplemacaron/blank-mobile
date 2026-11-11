package dev.blank.mobile.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blank.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onSessions: () -> Unit, onSettings: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val phonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermissions()
    }
    val installPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.completeInstallPick(uri)
    }
    LaunchedEffect(vm.pickRequest) {
        if (vm.pickRequest) installPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
    }
    LaunchedEffect(Unit) { vm.refreshPermissions() }
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.content?.length, vm.messages.lastOrNull()?.reasoning?.length) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSessions) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.sessions))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (vm.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_mark),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (vm.config.configured) R.string.chat_ready_hint else R.string.chat_not_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.messages) { message ->
                        if (message.role == "tool") ToolCard(message.content) else MessageBubble(message)
                    }
                }
            }
            if (!vm.accessibilityConnected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.accessibility_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val context = LocalContext.current
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) {
                                Text(stringResource(R.string.accessibility_enable))
                            }
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.accessibility_battery_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val context = LocalContext.current
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) {
                                Text(stringResource(R.string.accessibility_battery_button))
                            }
                        }
                    }
                }
            }
            if (vm.phonePermissionsMissing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.phone_permission_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            phonePermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE))
                        }) {
                            Text(stringResource(R.string.phone_permission_button))
                        }
                    }
                }
            }
            vm.pendingApproval?.let { call ->
                ApprovalCard(detail = vm.describeCall(call), onDecide = { allow, always -> vm.decideApproval(allow, always) })
            }
            vm.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.composer_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                if (vm.streaming) {
                    TextButton(onClick = { vm.stop() }) {
                        Text(stringResource(R.string.stop))
                    }
                } else {
                    IconButton(
                        onClick = {
                            vm.send(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank() && vm.config.configured,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && message.reasoning.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 4.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        stringResource(R.string.reasoning_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        message.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                message.content.ifBlank { "…" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}