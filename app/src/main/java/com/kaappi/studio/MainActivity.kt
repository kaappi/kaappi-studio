package com.kaappi.studio

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.SettingsRepository
import com.kaappi.studio.domain.ThemeMode
import com.kaappi.studio.runtime.SchemeRunner
import com.kaappi.studio.ui.screens.EditorScreen
import com.kaappi.studio.ui.screens.ExamplesScreen
import com.kaappi.studio.ui.screens.FileBrowserScreen
import com.kaappi.studio.ui.screens.SettingsScreen
import com.kaappi.studio.ui.theme.KaappiStudioTheme
import com.kaappi.studio.viewmodel.EditorViewModel
import com.kaappi.studio.viewmodel.FileBrowserViewModel
import com.kaappi.studio.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

enum class NavSection(val label: String) {
    EDITOR("Editor"),
    EXAMPLES("Examples"),
    FILES("Files"),
    SETTINGS("Settings"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(this)
        val fileRepo = FileRepository(this)
        val schemeRunner = SchemeRunner(this)
        val settingsVM = SettingsViewModel(settingsRepo)
        val editorVM = EditorViewModel(schemeRunner)
        val fileBrowserVM = FileBrowserViewModel(fileRepo)

        setContent {
            val themeMode by settingsVM.themeMode.collectAsState()
            KaappiStudioTheme(themeMode = themeMode) {
                KaappiStudioApp(
                    settingsVM = settingsVM,
                    editorVM = editorVM,
                    fileBrowserVM = fileBrowserVM,
                    themeMode = themeMode,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaappiStudioApp(
    settingsVM: SettingsViewModel,
    editorVM: EditorViewModel,
    fileBrowserVM: FileBrowserViewModel,
    themeMode: ThemeMode,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentSection by remember { mutableStateOf(NavSection.EDITOR) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val isRunning by editorVM.isRunning.collectAsState()
    val isReady by editorVM.isReady.collectAsState()
    val fontSize by settingsVM.fontSize.collectAsState()
    val currentFileName by editorVM.currentFileName.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveFileName by remember { mutableStateOf("") }

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Kaappi Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                NavSection.entries.forEach { section ->
                    NavigationDrawerItem(
                        label = { Text(section.label) },
                        selected = currentSection == section,
                        onClick = {
                            currentSection = section
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                when (section) {
                                    NavSection.EDITOR -> Icons.Default.Code
                                    NavSection.EXAMPLES -> Icons.AutoMirrored.Filled.MenuBook
                                    NavSection.FILES -> Icons.Default.FolderOpen
                                    NavSection.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = section.label,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentSection) {
                                NavSection.EDITOR -> currentFileName?.let { "$it.scm" } ?: "Editor"
                                else -> currentSection.label
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                if (drawerState.isOpen) Icons.AutoMirrored.Filled.MenuOpen
                                else Icons.Default.Menu,
                                contentDescription = "Menu",
                            )
                        }
                    },
                    actions = {
                        if (currentSection == NavSection.EDITOR) {
                            IconButton(
                                onClick = {
                                    saveFileName = currentFileName ?: ""
                                    showSaveDialog = true
                                },
                                enabled = isReady,
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save")
                            }
                            IconButton(
                                onClick = {
                                    webView?.evaluateJavascript(
                                        "window.kaappiAPI?.getCode()",
                                    ) { rawCode ->
                                        if (rawCode == null || rawCode == "null") return@evaluateJavascript
                                        val code = try {
                                            kotlinx.serialization.json.Json.decodeFromString<String>(rawCode)
                                        } catch (_: Exception) {
                                            rawCode.removeSurrounding("\"")
                                        }
                                        editorVM.runCode(code)
                                    }
                                },
                                enabled = isReady && !isRunning,
                            ) {
                                if (isRunning) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            when (currentSection) {
                NavSection.EDITOR -> {
                    EditorScreen(
                        editorViewModel = editorVM,
                        isDark = isDark,
                        fontSize = fontSize,
                        onWebViewReady = { webView = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                NavSection.EXAMPLES -> {
                    ExamplesScreen(
                        onExampleSelected = { example ->
                            editorVM.setPendingCode(example.code)
                            editorVM.setCurrentFile(null)
                            editorVM.clearOutput()
                            currentSection = NavSection.EDITOR
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                NavSection.FILES -> {
                    FileBrowserScreen(
                        viewModel = fileBrowserVM,
                        onFileSelected = { file ->
                            editorVM.setPendingCode(file.content)
                            editorVM.setCurrentFile(file.name)
                            editorVM.clearOutput()
                            currentSection = NavSection.EDITOR
                        },
                        onNewFile = { name ->
                            fileBrowserVM.saveFile(name, "")
                            editorVM.setCurrentFile(name)
                            editorVM.setPendingCode("")
                            currentSection = NavSection.EDITOR
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                NavSection.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsVM,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save File") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = saveFileName,
                    onValueChange = { saveFileName = it },
                    label = { Text("File name") },
                    singleLine = true,
                    suffix = { Text(".scm") },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (saveFileName.isNotBlank()) {
                            webView?.evaluateJavascript(
                                "window.kaappiAPI?.getCode()",
                            ) { rawCode ->
                                val code = try {
                                    kotlinx.serialization.json.Json.decodeFromString<String>(rawCode ?: "\"\"")
                                } catch (_: Exception) {
                                    rawCode?.removeSurrounding("\"") ?: ""
                                }
                                fileBrowserVM.saveFile(saveFileName, code)
                                editorVM.setCurrentFile(saveFileName)
                            }
                            showSaveDialog = false
                        }
                    },
                    enabled = saveFileName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun setEditorCode(webView: WebView?, code: String) {
    if (webView == null) return
    val b64 = android.util.Base64.encodeToString(
        code.toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP,
    )
    webView.evaluateJavascript(
        "window.kaappiAPI?.setCodeBase64('$b64')", null,
    )
}
