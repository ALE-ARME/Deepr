package com.yogeshpaliyal.deepr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.R
import com.yogeshpaliyal.deepr.caldav.CalDavCollection
import com.yogeshpaliyal.deepr.caldav.CalDavConfig
import com.yogeshpaliyal.deepr.caldav.CalDavConnectionTestResult
import com.yogeshpaliyal.deepr.caldav.CalDavSyncManager
import com.yogeshpaliyal.deepr.caldav.CalDavSyncResult
import com.yogeshpaliyal.deepr.caldav.CalDavSyncTrigger
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.Screen
import com.yogeshpaliyal.deepr.ui.components.SettingsItem
import com.yogeshpaliyal.deepr.ui.components.SettingsSection
import com.yogeshpaliyal.deepr.util.formatDateTime
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Cloud
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Folders
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.ShieldCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object CalDavSettingsScreen : Screen {
    @Composable
    override fun Content() {
        CalDavSettingsContent()
    }
}

@Composable
fun CalDavSettingsItem() {
    val navigator = LocalNavigator.current
    val syncManager: CalDavSyncManager = koinInject()
    val preferenceDataStore: AppPreferenceDataStore = koinInject()
    val calDavEnabled by preferenceDataStore.getCalDavEnabled.collectAsStateWithLifecycle(initialValue = false)
    val collectionName by preferenceDataStore.getCalDavCollectionName.collectAsStateWithLifecycle(initialValue = "")
    val syncStatus by syncManager.syncStatus.collectAsStateWithLifecycle()

    val description =
        when {
            syncStatus.isSyncing -> stringResource(R.string.caldav_syncing)
            calDavEnabled -> {
                val base =
                    if (collectionName.isNotBlank()) {
                        collectionName
                    } else {
                        stringResource(R.string.caldav_provider_caldav)
                    }
                val timestamp =
                    if (syncStatus.lastSyncTime > 0L) {
                        " • ${formatDateTime(syncStatus.lastSyncTime)}"
                    } else {
                        ""
                    }
                "$base$timestamp"
            }
            else -> stringResource(R.string.caldav_sync_description)
        }

    SettingsItem(
        icon = TablerIcons.Cloud,
        title = stringResource(R.string.caldav_sync),
        description = description,
        onClick = {
            navigator.add(CalDavSettingsScreen)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalDavSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val coroutineScope = rememberCoroutineScope()

    val syncManager: CalDavSyncManager = koinInject()
    val preferenceDataStore: AppPreferenceDataStore = koinInject()
    val syncStatus by syncManager.syncStatus.collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var collectionUrl by remember { mutableStateOf("") }
    var collectionName by remember { mutableStateOf("") }
    var trustSelfSigned by remember { mutableStateOf(false) }
    var syncPrivate by remember { mutableStateOf(false) }
    var syncOnStartup by remember { mutableStateOf(true) }
    var syncOnSave by remember { mutableStateOf(true) }
    var syncIntervalHours by remember { mutableLongStateOf(6L) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var testResultIsError by remember { mutableStateOf(false) }
    var discoveredCollections by remember { mutableStateOf<List<CalDavCollection>>(emptyList()) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = preferenceDataStore.getCalDavEnabled.first()
        serverUrl = preferenceDataStore.getCalDavServerUrl.first()
        username = preferenceDataStore.getCalDavUsername.first()
        password = syncManager.getDecryptedPassword()
        collectionUrl = preferenceDataStore.getCalDavCollectionUrl.first()
        collectionName = preferenceDataStore.getCalDavCollectionName.first()
        trustSelfSigned = preferenceDataStore.getCalDavTrustSelfSigned.first()
        syncPrivate = preferenceDataStore.getCalDavSyncPrivate.first()
        syncOnStartup = preferenceDataStore.getCalDavSyncOnStartup.first()
        syncOnSave = preferenceDataStore.getCalDavSyncOnSave.first()
        syncIntervalHours = preferenceDataStore.getCalDavSyncIntervalHours.first()
        loaded = true
    }

    fun currentConfig(): CalDavConfig =
        CalDavConfig(
            enabled = enabled,
            serverUrl = serverUrl,
            username = username,
            password = password,
            collectionUrl = collectionUrl,
            collectionName = collectionName,
            trustSelfSigned = trustSelfSigned,
            syncPrivate = syncPrivate,
            syncOnStartup = syncOnStartup,
            syncOnSave = syncOnSave,
            syncIntervalHours = syncIntervalHours,
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.caldav_sync)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.removeLast() }) {
                        Icon(
                            imageVector = TablerIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.back),
                            modifier = if (isRtl) Modifier.graphicsLayer(scaleX = -1f) else Modifier,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(stringResource(R.string.caldav_provider)) {
                SettingsItem(
                    icon = TablerIcons.Cloud,
                    title = stringResource(R.string.caldav_provider_caldav),
                    description =
                        if (enabled) {
                            stringResource(R.string.caldav_provider_caldav)
                        } else {
                            stringResource(R.string.caldav_provider_none)
                        },
                    onClick = {
                        enabled = !enabled
                        if (loaded) {
                            coroutineScope.launch {
                                syncManager.saveSettings(currentConfig())
                            }
                        }
                    },
                    trailing = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                enabled = checked
                                if (loaded) {
                                    coroutineScope.launch {
                                        syncManager.saveSettings(currentConfig())
                                    }
                                }
                            },
                        )
                    },
                )
            }

            SettingsSection(stringResource(R.string.caldav_server_settings)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.caldav_server_url)) },
                        placeholder = { Text(stringResource(R.string.caldav_server_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.caldav_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.caldav_password)) },
                        singleLine = true,
                        visualTransformation =
                            if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector =
                                        if (passwordVisible) {
                                            TablerIcons.EyeOff
                                        } else {
                                            TablerIcons.Eye
                                        },
                                    contentDescription =
                                        stringResource(
                                            if (passwordVisible) {
                                                R.string.hide_password
                                            } else {
                                                R.string.show_password
                                            },
                                        ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsItem(
                    icon = TablerIcons.ShieldCheck,
                    title = stringResource(R.string.caldav_trust_self_signed),
                    description = stringResource(R.string.caldav_trust_self_signed_desc),
                    onClick = {
                        trustSelfSigned = !trustSelfSigned
                    },
                    trailing = {
                        Switch(
                            checked = trustSelfSigned,
                            onCheckedChange = { trustSelfSigned = it },
                        )
                    },
                )

                SettingsItem(
                    icon = TablerIcons.Folders,
                    title = stringResource(R.string.caldav_collection),
                    description =
                        when {
                            collectionName.isNotBlank() && collectionUrl.isNotBlank() ->
                                "$collectionName ($collectionUrl)"
                            collectionUrl.isNotBlank() -> collectionUrl
                            else -> stringResource(R.string.caldav_collection_none)
                        },
                    onClick =
                        if (discoveredCollections.isNotEmpty()) {
                            { showCollectionDialog = true }
                        } else {
                            null
                        },
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTestingConnection = true
                                    testResultMessage = null
                                    when (
                                        val result =
                                            syncManager.testConnection(
                                                serverUrl = serverUrl,
                                                username = username,
                                                password = password,
                                                trustSelfSigned = trustSelfSigned,
                                            )
                                    ) {
                                        is CalDavConnectionTestResult.Success -> {
                                            discoveredCollections = result.collections
                                            if (collectionUrl.isBlank() ||
                                                result.collections.none { it.url == collectionUrl }
                                            ) {
                                                val first = result.collections.first()
                                                collectionUrl = first.url
                                                collectionName = first.displayName
                                            }
                                            syncManager.saveSettings(currentConfig())
                                            testResultIsError = false
                                            testResultMessage =
                                                context.getString(
                                                    R.string.caldav_connection_success,
                                                    result.collections.size,
                                                )
                                            if (result.collections.size > 1) {
                                                showCollectionDialog = true
                                            }
                                        }
                                        is CalDavConnectionTestResult.Error -> {
                                            testResultIsError = true
                                            testResultMessage = result.message
                                        }
                                    }
                                    isTestingConnection = false
                                }
                            },
                            enabled = !isTestingConnection && !syncStatus.isSyncing,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.caldav_testing_connection))
                            } else {
                                Text(stringResource(R.string.caldav_test_connection))
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    syncManager.saveSettings(currentConfig())
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.caldav_settings_saved),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.caldav_save_settings))
                        }
                    }

                    testResultMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (testResultIsError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.caldav_sync_options)) {
                SettingsItem(
                    icon = TablerIcons.Lock,
                    title = stringResource(R.string.caldav_sync_private),
                    description = stringResource(R.string.caldav_sync_private_desc),
                    onClick = { syncPrivate = !syncPrivate },
                    trailing = {
                        Switch(
                            checked = syncPrivate,
                            onCheckedChange = { syncPrivate = it },
                        )
                    },
                )

                SettingsItem(
                    icon = TablerIcons.Refresh,
                    title = stringResource(R.string.caldav_sync_on_startup),
                    description = stringResource(R.string.caldav_sync_on_startup_desc),
                    onClick = { syncOnStartup = !syncOnStartup },
                    trailing = {
                        Switch(
                            checked = syncOnStartup,
                            onCheckedChange = { syncOnStartup = it },
                        )
                    },
                )

                SettingsItem(
                    icon = TablerIcons.Refresh,
                    title = stringResource(R.string.caldav_sync_on_save),
                    description = stringResource(R.string.caldav_sync_on_save_desc),
                    onClick = { syncOnSave = !syncOnSave },
                    trailing = {
                        Switch(
                            checked = syncOnSave,
                            onCheckedChange = { syncOnSave = it },
                        )
                    },
                )

                SettingsItem(
                    icon = TablerIcons.Clock,
                    title = stringResource(R.string.caldav_sync_interval),
                    description = stringResource(R.string.caldav_interval_hours, syncIntervalHours),
                    onClick = { showIntervalDialog = true },
                )
            }

            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val lastSyncLabel =
                        if (syncStatus.lastSyncTime > 0L) {
                            stringResource(R.string.caldav_last_sync, formatDateTime(syncStatus.lastSyncTime))
                        } else {
                            stringResource(R.string.caldav_never_synced)
                        }

                    Text(
                        text = lastSyncLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    if (syncStatus.lastSyncMessage.isNotBlank()) {
                        Text(
                            text = syncStatus.lastSyncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (syncStatus.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                syncManager.saveSettings(currentConfig())
                                when (val result = syncManager.syncNow(CalDavSyncTrigger.MANUAL)) {
                                    is CalDavSyncResult.Success -> {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(
                                                    R.string.caldav_sync_success_summary,
                                                    result.pushedCount,
                                                    result.pulledCount,
                                                    result.deletedCount,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                    is CalDavSyncResult.Error -> {
                                        Toast
                                            .makeText(
                                                context,
                                                result.message,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                }
                            }
                        },
                        enabled = enabled && !syncStatus.isSyncing && !isTestingConnection,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (syncStatus.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.caldav_syncing))
                        } else {
                            Icon(
                                imageVector = TablerIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.caldav_sync_now))
                        }
                    }
                }
            }
        }
    }

    if (showCollectionDialog && discoveredCollections.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text(stringResource(R.string.caldav_select_collection)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredCollections.forEach { col ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        collectionUrl = col.url
                                        collectionName = col.displayName
                                        showCollectionDialog = false
                                    }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = col.url == collectionUrl,
                                onClick = {
                                    collectionUrl = col.url
                                    collectionName = col.displayName
                                    showCollectionDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = col.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = col.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showIntervalDialog) {
        val intervals = listOf(1L, 6L, 12L, 24L)
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.caldav_sync_interval)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervals.forEach { hours ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        syncIntervalHours = hours
                                        showIntervalDialog = false
                                    }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = syncIntervalHours == hours,
                                onClick = {
                                    syncIntervalHours = hours
                                    showIntervalDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.caldav_interval_hours, hours),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
