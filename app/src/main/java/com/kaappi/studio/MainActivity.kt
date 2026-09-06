package com.kaappi.studio

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaappi.studio.bridge.KaappiBridge
import com.kaappi.studio.bridge.decodeCodeResult
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.FileRepositoryException
import com.kaappi.studio.data.SchemeFileNames
import com.kaappi.studio.data.SettingsRepository
import com.kaappi.studio.domain.ThemeMode
import com.kaappi.studio.runtime.SchemeRunner
import com.kaappi.studio.ui.screens.EditorScreen
import com.kaappi.studio.ui.screens.ExamplesScreen
import com.kaappi.studio.ui.screens.FileBrowserScreen
import com.kaappi.studio.ui.screens.SettingsScreen
import com.kaappi.studio.ui.screens.applyEditorAppearance
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

    // ViewModels live in the ViewModelStore so they (and their state, and any
    // run in progress) survive configuration changes (issue #9).
    private val settingsVM: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer { SettingsViewModel(SettingsRepository(applicationContext)) }
        }
    }

    private val fileBrowserVM: FileBrowserViewModel by viewModels {
        viewModelFactory {
            initializer { FileBrowserViewModel(FileRepository(applicationContext)) }
        }
    }

    private val editorVM: EditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                // The parsed WASM module is shared; every run gets a fresh
                // SchemeRunner with its own working directory (issues #9, #11).
                val moduleCache = SchemeRunner.ModuleCache {
                    assets.open("kaappi.wasm").use { it.readBytes() }
                }
                EditorViewModel { SchemeRunner(applicationContext, moduleCache) }
            }
        }
    }

    private val bridge = KaappiBridge(object : KaappiBridge.BridgeListener {
        override fun onReady() {
            editorVM.onReady()
        }

        override fun onReadyWithWebView(webView: WebView) {
            // Fires once per page load — i.e. when a (re)created WebView is
            // ready. Queued code (opened file/example) wins over the restored
            // draft; the draft is only ever re-injected here, never on a
            // plain pause/resume, which would reset cursor and undo history.
            val code = editorVM.consumePendingCode() ?: editorVM.consumeDraft() ?: return
            setEditorCode(webView, code)
        }
    })

    // Single editor WebView for the whole Activity lifetime (issue #3): created
    // on first use and reused across drawer section switches instead of being
    // leaked and recreated on every re-entry into composition.
    private var editorWebView: WebView? = null

    private fun getOrCreateEditorWebView(): WebView {
        editorWebView?.let { return it }
        return WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(bridge, "KaappiBridge")
            bridge.webView = this
            // The old readiness (if any) belonged to a previous page; Play and
            // Save stay disabled until this page posts its `ready` event.
            editorVM.onWebViewReset()
            loadUrl("file:///android_asset/webview/index.html")
        }.also { editorWebView = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by settingsVM.themeMode.collectAsState()
            KaappiStudioTheme(themeMode = themeMode) {
                KaappiStudioApp(
                    settingsVM = settingsVM,
                    editorVM = editorVM,
                    fileBrowserVM = fileBrowserVM,
                    themeMode = themeMode,
                    getEditorWebView = ::getOrCreateEditorWebView,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveEditorDraft()
    }

    override fun onDestroy() {
        editorWebView?.let { webView ->
            // Remove from the Compose hierarchy before destroying, per the
            // WebView.destroy() contract.
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        editorWebView = null
        bridge.webView = null
        editorVM.onWebViewReset()
        super.onDestroy()
    }

    /**
     * Pulls the current editor content into the ViewModel so it can be
     * re-injected if the Activity (and its WebView) is recreated. Skipped when
     * a code load is still queued — that one wins.
     */
    private fun saveEditorDraft() {
        val webView = editorWebView ?: return
        webView.evaluateJavascript("window.kaappiAPI?.getCode()") { raw ->
            // A queued load wins over the draft; a null result (page not ready
            // yet) must not become the draft's content.
            if (editorVM.pendingCode.value == null) {
                decodeCodeResult(raw)?.let { editorVM.saveDraft(it) }
            }
        }
    }
}

private fun setEditorCode(webView: WebView, code: String) {
    val b64 = android.util.Base64.encodeToString(
        code.toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP,
    )
    webView.evaluateJavascript(
        "window.kaappiAPI?.setCodeBase64('$b64')", null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaappiStudioApp(
    settingsVM: SettingsViewModel,
    editorVM: EditorViewModel,
    fileBrowserVM: FileBrowserViewModel,
    themeMode: ThemeMode,
    getEditorWebView: () -> WebView,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentSection by remember { mutableStateOf(NavSection.EDITOR) }
    val isRunning by editorVM.isRunning.collectAsState()
    val isReady by editorVM.isReady.collectAsState()
    val fontSize by settingsVM.fontSize.collectAsState()
    val currentFileName by editorVM.currentFileName.collectAsState()
    val pendingCode by editorVM.pendingCode.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveFileName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Issue #10: set when a new file would overwrite an existing one; shows
    // the confirmation dialog instead of silently truncating the file.
    var overwriteTarget by remember { mutableStateOf<String?>(null) }

    fun showFileError(prefix: String, e: Exception) {
        scope.launch {
            snackbarHostState.showSnackbar("$prefix: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    val createEmptyFile = { name: String ->
        try {
            val saved = fileBrowserVM.saveFile(name, "")
            editorVM.setCurrentFile(saved.name)
            editorVM.setPendingCode("")
            currentSection = NavSection.EDITOR
        } catch (e: FileRepositoryException) {
            showFileError("Could not create file", e)
        } catch (e: IllegalArgumentException) {
            showFileError("Could not create file", e)
        }
    }

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Push queued editor content (opened file, picked example, restored draft)
    // into the live WebView once it is showing and ready. Theme and font size
    // are re-sent here too: the AndroidView update block can fire before the
    // page has committed and lose them against the blank initial page, so the
    // ready state must trigger a retry (issue #15).
    LaunchedEffect(currentSection, pendingCode, isReady, isDark, fontSize) {
        if (currentSection == NavSection.EDITOR && isReady) {
            val webView = getEditorWebView()
            editorVM.consumePendingCode()?.let { code ->
                setEditorCode(webView, code)
            }
            applyEditorAppearance(webView, isDark, fontSize)
        }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            if (isRunning) {
                                IconButton(
                                    onClick = { editorVM.stopRun() },
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        getEditorWebView().evaluateJavascript(
                                            "window.kaappiAPI?.getCode()",
                                        ) { rawCode ->
                                            decodeCodeResult(rawCode)?.let(editorVM::runCode)
                                        }
                                    },
                                    enabled = isReady,
                                ) {
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
                        getWebView = getEditorWebView,
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
                            try {
                                if (fileBrowserVM.fileExists(name)) {
                                    overwriteTarget = SchemeFileNames.sanitize(name)
                                } else {
                                    createEmptyFile(name)
                                }
                            } catch (e: IllegalArgumentException) {
                                showFileError("Could not create file", e)
                            }
                        },
                        onFileDeleted = { file ->
                            // SchemeFile.name and currentFileName are both
                            // stored as base names without the .scm extension,
                            // so they compare directly. Resetting the pending
                            // code queues an empty document that replaces the
                            // deleted file's content next time the editor is
                            // shown, and clearing the name drops it from the
                            // title and the save-dialog prefill (issue #15).
                            if (file.name == editorVM.currentFileName.value) {
                                editorVM.setPendingCode("")
                                editorVM.setCurrentFile(null)
                            }
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
                            getEditorWebView().evaluateJavascript(
                                "window.kaappiAPI?.getCode()",
                            ) { rawCode ->
                                // Skip the save entirely when there is no
                                // content to save (page not ready) rather than
                                // clobbering the target file with "null".
                                decodeCodeResult(rawCode)?.let { code ->
                                    try {
                                        val saved = fileBrowserVM.saveFile(saveFileName, code)
                                        editorVM.setCurrentFile(saved.name)
                                    } catch (e: FileRepositoryException) {
                                        showFileError("Could not save file", e)
                                    } catch (e: IllegalArgumentException) {
                                        showFileError("Could not save file", e)
                                    }
                                }
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

    overwriteTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { overwriteTarget = null },
            title = { Text("Replace $target.scm?") },
            text = {
                Text(
                    "A file with this name already exists. " +
                        "Creating a new file will overwrite it with empty content.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        overwriteTarget = null
                        createEmptyFile(target)
                    },
                ) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { overwriteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
