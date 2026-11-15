package com.yogeshpaliyal.deepr.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.R
import com.yogeshpaliyal.deepr.backup.SafPermissionHelper
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.Screen
import com.yogeshpaliyal.deepr.ui.components.ServerStatusBar
import com.yogeshpaliyal.deepr.ui.components.SettingsItem
import com.yogeshpaliyal.deepr.ui.components.SettingsSection
import com.yogeshpaliyal.deepr.util.RequestResult
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.FileText
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Upload
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupScreen : Screen {
    @Composable
    override fun Content() {
        BackupScreenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreenContent(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val backStack = LocalNavigator.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    // Launcher for picking CSV export location
    val csvExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                viewModel.exportCsvData(it)
            }
        }

    // Collect sync preference states
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()
    val syncFilePath by viewModel.syncFilePath.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()

    // Collect auto backup preference states
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupLocation by viewModel.autoBackupLocation.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val lastBackupError by viewModel.lastBackupError.collectAsStateWithLifecycle()

    var hasSyncPermission by remember(syncFilePath) {
        mutableStateOf(
            syncFilePath.isEmpty() ||
                !syncFilePath.startsWith("content://") ||
                SafPermissionHelper.hasPersistedWritePermission(context, syncFilePath),
        )
    }

    var hasBackupPermission by remember(autoBackupLocation) {
        mutableStateOf(
            autoBackupLocation.isEmpty() ||
                SafPermissionHelper.hasPersistedWritePermission(context, autoBackupLocation),
        )
    }

    DisposableEffect(lifecycleOwner, syncFilePath, autoBackupLocation) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasSyncPermission =
                        syncFilePath.isEmpty() ||
                        !syncFilePath.startsWith("content://") ||
                        SafPermissionHelper.hasPersistedWritePermission(context, syncFilePath)
                    hasBackupPermission =
                        autoBackupLocation.isEmpty() ||
                        SafPermissionHelper.hasPersistedWritePermission(context, autoBackupLocation)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for picking sync file location
    val syncFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/markdown"),
        ) { uri ->
            uri?.let {
                if (SafPermissionHelper.takePersistableWritePermission(context, it)) {
                    hasSyncPermission = true
                    viewModel.setSyncFilePath(it.toString())
                } else {
                    errorDialogMessage = context.getString(R.string.saf_persist_failed)
                }
            }
        }

    // Launcher for picking auto backup location
    val backupLocationLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                if (SafPermissionHelper.takePersistableWritePermission(context, it)) {
                    hasBackupPermission = true
                    viewModel.setAutoBackupLocation(it.toString())
                } else {
                    errorDialogMessage = context.getString(R.string.saf_persist_failed)
                }
            }
        }

    LaunchedEffect(Unit) {
        viewModel.exportResultFlow.collectLatest { result ->
            when (result) {
                is RequestResult.Success -> {
                    Toast.makeText(context, result.data, Toast.LENGTH_LONG).show()
                }

                is RequestResult.Error -> {
                    errorDialogMessage = result.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncResultFlow.collectLatest { result ->
            when (result) {
                is RequestResult.Success -> {
                    Toast.makeText(context, result.data, Toast.LENGTH_LONG).show()
                }

                is RequestResult.Error -> {
                    errorDialogMessage = result.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.autoBackupResultFlow.collectLatest { result ->
            when (result) {
                is RequestResult.Success -> {
                    Toast.makeText(context, result.data, Toast.LENGTH_LONG).show()
                }

                is RequestResult.Error -> {
                    errorDialogMessage = result.message
                }
            }
        }
    }

    errorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = {
                Text(stringResource(R.string.backup_error_dialog_title))
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(message)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { errorDialogMessage = null }) {
                    Text(stringResource(R.string.backup_error_dismiss))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Backup")
                    },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

                        IconButton(onClick = {
                            backStack.removeLast()
                        }) {
                            Icon(
                                TablerIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.back),
                                modifier =
                                    if (isRtl) {
                                        Modifier.graphicsLayer(scaleX = -1f)
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    },
                )
                ServerStatusBar(
                    onServerStatusClick = {
                        if (backStack.getLast() !is LocalNetworkServer) {
                            backStack.add(LocalNetworkServer)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Export") {
                SettingsItem(
                    TablerIcons.Upload,
                    title = stringResource(R.string.export_deeplinks),
                    description = stringResource(R.string.export_deeplinks_description),
                    onClick = {
                        val timeStamp =
                            SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.US,
                            ).format(Date())
                        csvExportLauncher.launch("deepr_export_$timeStamp.csv")
                    },
                )
            }

            SettingsSection("Local File Sync") {
                SettingsItem(
                    TablerIcons.Refresh,
                    title = stringResource(R.string.sync_to_file),
                    description = stringResource(R.string.sync_to_file_description),
                    onClick = {
                        viewModel.setSyncEnabled(!syncEnabled)
                    },
                    trailing = {
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = { viewModel.setSyncEnabled(it) },
                        )
                    },
                )

                AnimatedVisibility(syncEnabled) {
                    Column {
                        if (syncFilePath.isNotEmpty() && !hasSyncPermission) {
                            SettingsItem(
                                TablerIcons.AlertTriangle,
                                title = stringResource(R.string.permission_required_banner),
                                description = stringResource(R.string.sync_permission_expired),
                                isDestructive = true,
                                onClick = {
                                    syncFileLauncher.launch("deeplinks.md")
                                },
                            )
                        }

                        SettingsItem(
                            TablerIcons.FileText,
                            title = stringResource(R.string.select_sync_file),
                            description =
                                if (syncFilePath.isNotEmpty()) {
                                    SafPermissionHelper.formatUriForDisplay(syncFilePath)
                                } else {
                                    stringResource(R.string.select_sync_file_description)
                                },
                            onClick = {
                                syncFileLauncher.launch("deeplinks.md")
                            },
                        )

                        SettingsItem(
                            TablerIcons.InfoCircle,
                            title = stringResource(R.string.last_sync_time),
                            description =
                                if (lastSyncTime > 0) {
                                    val formatter =
                                        SimpleDateFormat(
                                            "MMM dd, yyyy 'at' HH:mm",
                                            Locale.getDefault(),
                                        )
                                    stringResource(
                                        R.string.last_sync_time_format,
                                        formatter.format(Date(lastSyncTime)),
                                    )
                                } else {
                                    stringResource(R.string.last_sync_time_never)
                                },
                        )

                        AnimatedVisibility(syncFilePath.isNotEmpty()) {
                            SettingsItem(
                                TablerIcons.Upload,
                                title = stringResource(R.string.sync_now),
                                description = stringResource(R.string.sync_now_description),
                                onClick = {
                                    viewModel.syncToMarkdown()
                                },
                            )
                        }
                    }
                }
            }

            SettingsSection("Auto Backup") {
                SettingsItem(
                    TablerIcons.Upload,
                    title = stringResource(R.string.auto_backup),
                    description = stringResource(R.string.auto_backup_description),
                    onClick = {
                        viewModel.setAutoBackupEnabled(!autoBackupEnabled)
                    },
                    trailing = {
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                        )
                    },
                )

                AnimatedVisibility(autoBackupEnabled) {
                    Column {
                        if (autoBackupLocation.isNotEmpty() && !hasBackupPermission) {
                            SettingsItem(
                                TablerIcons.AlertTriangle,
                                title = stringResource(R.string.permission_required_banner),
                                description = stringResource(R.string.backup_permission_expired),
                                isDestructive = true,
                                onClick = {
                                    backupLocationLauncher.launch(null)
                                },
                            )
                        }

                        if (lastBackupError.isNotBlank()) {
                            SettingsItem(
                                TablerIcons.AlertTriangle,
                                title = stringResource(R.string.backup_error_dialog_title),
                                description = lastBackupError,
                                isDestructive = true,
                                onClick = {
                                    errorDialogMessage = lastBackupError
                                },
                            )
                        }

                        SettingsItem(
                            TablerIcons.FileText,
                            title = stringResource(R.string.select_backup_location),
                            description =
                                if (autoBackupLocation.isNotEmpty()) {
                                    SafPermissionHelper.formatUriForDisplay(autoBackupLocation)
                                } else {
                                    stringResource(R.string.select_backup_location_description)
                                },
                            onClick = {
                                backupLocationLauncher.launch(null)
                            },
                        )

                        SettingsItem(
                            TablerIcons.InfoCircle,
                            title = stringResource(R.string.last_backup_time),
                            description =
                                if (lastBackupTime > 0) {
                                    val formatter =
                                        SimpleDateFormat(
                                            "MMM dd, yyyy 'at' HH:mm",
                                            Locale.getDefault(),
                                        )
                                    stringResource(
                                        R.string.last_backup_time_format,
                                        formatter.format(Date(lastBackupTime)),
                                    )
                                } else {
                                    stringResource(R.string.last_backup_time_never)
                                },
                        )

                        AnimatedVisibility(autoBackupLocation.isNotEmpty()) {
                            SettingsItem(
                                TablerIcons.Upload,
                                title = stringResource(R.string.backup_now),
                                description = stringResource(R.string.backup_now_description),
                                onClick = {
                                    viewModel.backupNow()
                                },
                            )
                        }
                    }
                }
            }

            SettingsSection("Transfer Links") {
                SettingsItem(
                    TablerIcons.Upload,
                    title = stringResource(R.string.transfer_link_to_another_device),
                    description = "",
                    onClick = {
                        backStack.add(TransferLinkLocalNetworkServer)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
