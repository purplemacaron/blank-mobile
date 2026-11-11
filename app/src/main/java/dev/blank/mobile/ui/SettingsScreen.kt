package dev.blank.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.blank.mobile.R
import dev.blank.mobile.data.ConfigStore
import dev.blank.mobile.data.Providers
import dev.blank.mobile.provider.HttpProvider
import dev.blank.mobile.provider.ModelInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(config: ConfigStore, onBack: () -> Unit) {
    var providerId by remember { mutableStateOf(config.providerId) }
    val def = Providers.byId(providerId)
    var baseUrl by remember(providerId) { mutableStateOf(config.baseUrl(def)) }
    var apiKey by remember(providerId) { mutableStateOf("") }
    var modelId by remember { mutableStateOf(config.modelId) }
    var models by remember(providerId) { mutableStateOf<List<ModelInfo>?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(providerId) {
        saved = false
        fetchFailed = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_provider), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Providers.all.forEach { candidate ->
                    FilterChip(
                        selected = candidate.id == providerId,
                        onClick = { providerId = candidate.id },
                        label = { Text(candidate.label) },
                    )
                }
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_base_url)) },
                singleLine = true,
            )
            val masked = config.maskedKey(providerId)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (masked.isNotBlank()) {
                    { Text(stringResource(R.string.settings_saved_key, masked)) }
                } else {
                    null
                },
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    fetching = true
                    fetchFailed = false
                    scope.launch {
                        try {
                            models = HttpProvider().listModels(
                                def.protocol,
                                baseUrl,
                                apiKey.ifBlank { config.apiKey(providerId) }.ifBlank { null },
                            )
                        } catch (e: Exception) {
                            fetchFailed = true
                        } finally {
                            fetching = false
                        }
                    }
                },
                enabled = !fetching,
            ) {
                if (fetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_fetch_models))
                }
            }
            if (fetchFailed) {
                Text(
                    stringResource(R.string.settings_fetch_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            models?.let { list ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { model ->
                        FilterChip(
                            selected = model.id == modelId,
                            onClick = { modelId = model.id },
                            label = { Text(model.id) },
                        )
                    }
                }
            }
            Button(
                onClick = {
                    config.providerId = providerId
                    config.setBaseUrl(providerId, baseUrl)
                    if (apiKey.isNotBlank()) config.setApiKey(providerId, apiKey)
                    config.modelId = modelId.trim()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save))
            }
            if (saved) {
                Text(
                    stringResource(R.string.settings_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}