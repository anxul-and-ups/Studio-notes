package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.components.AppBackdropState
import com.example.ui.components.LocalAppBackdrop
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.screens.ThemeSettingsScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.db.AppDatabase
import com.example.data.model.NoteEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.NoteRepository
import com.example.ui.components.GlassSidebar
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.ApiRoomScreen
import com.example.ui.screens.CommandModeScreen
import com.example.ui.screens.HtmlViewerScreen
import com.example.ui.screens.MainWorkspaceScreen
import com.example.ui.screens.NameGeneratorScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ReadNoteScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SecurityAreaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StorageFileEditorScreen
import com.example.ui.screens.TableEditorScreen
import com.example.ui.screens.TempMailScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

sealed class Screen {
    data object Splash : Screen()
    data class MainWorkspace(val initialFolder: String = "All Notes", val pickForHide: Boolean = false) : Screen()
    data class ReadNote(val note: NoteEntity) : Screen()
    data class EditNote(val note: NoteEntity?) : Screen()
    data object Settings : Screen()
    data object SecurityArea : Screen()
    data object SecuritySettings : Screen()
    data object ThemeSettings : Screen()
    data object ApiRoom : Screen()
    data object NameGenerator : Screen()
    data object HtmlViewer : Screen()
    data object CommandMode : Screen()
    data object TempMail : Screen()
    data class TableEditor(val initialTableData: String, val onSaved: (String) -> Unit) : Screen()
    data object RecycleBin : Screen()
    data object StorageEditor : Screen()
    data class PdfViewer(val uri: android.net.Uri, val returnTo: Screen) : Screen()
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: shows the system splash instantly on
        // launch so there is no 2-3s blank gap before the Compose splash appears.
        com.example.ui.util.CrashReporter.install(applicationContext)
        val lastCrash = com.example.ui.util.CrashReporter.consume(applicationContext)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.ui.util.SirenAudioPlayer.init(applicationContext)

        val database = AppDatabase.getInstance(applicationContext)
        val repository = NoteRepository(database.noteDao())
        val preferences = AppPreferences(applicationContext)

        setContent {
            var crashText by remember { mutableStateOf(lastCrash) }
            val crashToShow = crashText
            if (crashToShow != null) {
                com.example.ui.screens.CrashReportScreen(
                    crashText = crashToShow,
                    onContinue = { crashText = null }
                )
                return@setContent
            }
            val storedDark by preferences.isDarkMode.collectAsState()
            val themeMode by preferences.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDarkMode = when (themeMode) {
                AppPreferences.THEME_SYSTEM -> systemDark
                AppPreferences.THEME_DAY -> false
                AppPreferences.THEME_DARK -> true
                else -> storedDark
            }

            // Custom wallpaper (Theme Settings): decoded off the main thread, reloaded on change.
            val bgPath by preferences.bgImagePath.collectAsState()
            val bgVersion by preferences.bgImageVersion.collectAsState()
            val bgDim by preferences.bgDim.collectAsState()
            val bgImage by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null, bgPath, bgVersion
            ) {
                value = if (bgPath.isBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.ui.util.ThemeImageProcessor.loadFile(bgPath)?.asImageBitmap()
                }
            }

            MyApplicationTheme(darkTheme = isDarkMode) {
                CompositionLocalProvider(LocalAppBackdrop provides AppBackdropState(bgImage, bgDim)) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AuNotesApp(
                            activity = this,
                            repository = repository,
                            preferences = preferences,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = {
                                preferences.setThemeMode(if (isDarkMode) AppPreferences.THEME_DAY else AppPreferences.THEME_DARK)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuNotesApp(
    activity: FragmentActivity,
    repository: NoteRepository,
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var screenBeforeRecycleBin by remember { mutableStateOf<Screen>(Screen.MainWorkspace()) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var pendingDeleteNoteId by remember { mutableStateOf<Long?>(null) }
    var isAiChatOpen by remember { mutableStateOf(false) }

    // Security Vault unlocked state, hoisted here (rather than local to
    // SecurityAreaScreen) so "When app is closed" / "When screen is locked off"
    // auto-lock modes can keep it open across a Settings<->Security round-trip
    // within one app session, while still being force-cleared below on the
    // matching lifecycle event (Item 4: Security Auto Lock).
    var isSecurityVaultUnlocked by remember { mutableStateOf(false) }
    val securityAutoLockMode by preferences.securityAutoLockMode.collectAsState()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, securityAutoLockMode) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Activity going to background covers "app is closed" and is the
                // best same-process approximation available for "screen locked
                // off" without extra broadcast-receiver permissions.
                if (securityAutoLockMode == AppPreferences.AUTO_LOCK_ON_APP_CLOSE ||
                    securityAutoLockMode == AppPreferences.AUTO_LOCK_ON_SCREEN_OFF ||
                    securityAutoLockMode == AppPreferences.AUTO_LOCK_IMMEDIATE
                ) {
                    isSecurityVaultUnlocked = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Request Storage and Notification Permissions on first launch (PART B Item 3)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Leaving the Security Area re-locks it for the Immediately / When-leaving modes.
    fun leaveSecurityArea() {
        if (securityAutoLockMode == AppPreferences.AUTO_LOCK_IMMEDIATE ||
            securityAutoLockMode == AppPreferences.AUTO_LOCK_ON_LEAVE_AREA
        ) isSecurityVaultUnlocked = false
        currentScreen = Screen.Settings
    }

    // Intercept Back Press
    BackHandler(enabled = isAiChatOpen || isSidebarOpen || currentScreen !is Screen.MainWorkspace || (currentScreen as? Screen.MainWorkspace)?.pickForHide == true) {
        val screenNow = currentScreen
        when {
            isAiChatOpen -> isAiChatOpen = false
            isSidebarOpen -> isSidebarOpen = false
            screenNow is Screen.RecycleBin -> currentScreen = screenBeforeRecycleBin
            screenNow is Screen.PdfViewer -> currentScreen = screenNow.returnTo
            screenNow is Screen.ApiRoom -> currentScreen = Screen.Settings
            screenNow is Screen.ThemeSettings -> currentScreen = Screen.Settings
            screenNow is Screen.SecuritySettings -> currentScreen = Screen.SecurityArea
            screenNow is Screen.MainWorkspace && screenNow.pickForHide -> currentScreen = Screen.SecurityArea
            screenNow is Screen.SecurityArea -> leaveSecurityArea()
            screenNow is Screen.Splash -> { /* Let splash finish */ }
            currentScreen !is Screen.MainWorkspace -> currentScreen = Screen.MainWorkspace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screen-to-screen transitions: fade + gentle slide + subtle scale on springs. Forward
        // navigation enters from the right, going back to the main screen enters from the left.
        // Typing, caret scrolling and folder switching never touch this — it only reacts to a
        // change of the top-level screen.
        androidx.compose.animation.AnimatedContent(
            targetState = currentScreen,
            contentKey = { it::class },
            transitionSpec = {
                val toMain = targetState is Screen.MainWorkspace
                val splash = initialState is Screen.Splash || targetState is Screen.Splash
                if (splash) {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250))
                } else {
                    val dir = if (toMain) -1 else 1
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, delayMillis = 40)) +
                        androidx.compose.animation.slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { full -> dir * full / 8 }
                        ) +
                        androidx.compose.animation.scaleIn(
                            initialScale = 0.96f,
                            animationSpec = androidx.compose.animation.core.tween(280)
                        )) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                            androidx.compose.animation.slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(220),
                                targetOffsetX = { full -> -dir * full / 12 }
                            ))
                }
            },
            label = "screen_transition"
        ) { screen ->
        when (screen) {
            is Screen.Splash -> {
                SplashScreen(
                    isDarkMode = isDarkMode,
                    onSplashFinished = {
                        currentScreen = Screen.MainWorkspace()
                    }
                )
            }

            is Screen.MainWorkspace -> {
                MainWorkspaceScreen(
                    repository = repository,
                    preferences = preferences,
                    initialFolder = screen.initialFolder,
                    pickForHide = screen.pickForHide,
                    onPickForHideDone = { chosen ->
                        coroutineScope.launch {
                            repository.hideNotes(chosen)
                            android.widget.Toast.makeText(activity, "${chosen.size} note(s) hidden", android.widget.Toast.LENGTH_SHORT).show()
                            currentScreen = Screen.SecurityArea
                        }
                    },
                    onPickForHideCancel = { currentScreen = Screen.SecurityArea },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenSidebar = { isSidebarOpen = true },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onOpenAiChat = { isAiChatOpen = true },
                    onOpenNote = { note ->
                        currentScreen = Screen.ReadNote(note)
                    },
                    onCreateNote = {
                        currentScreen = Screen.EditNote(null)
                    }
                )
            }

            is Screen.ReadNote -> {
                ReadNoteScreen(
                    note = screen.note,
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() },
                    onEditNote = { note ->
                        currentScreen = Screen.EditNote(note)
                    },
                    onDeleteNote = { noteId ->
                        val doDelete = {
                            coroutineScope.launch {
                                repository.moveToTrash(noteId)
                                currentScreen = Screen.MainWorkspace()
                            }
                            Unit
                        }
                        if (com.example.ui.components.needsDeleteVerification(preferences)) pendingDeleteNoteId = noteId
                        else doDelete()
                    },
                    onToggleLock = { noteId, isUnlocked ->
                        coroutineScope.launch {
                            repository.toggleLock(noteId, !isUnlocked)
                        }
                    }
                )
            }

            is Screen.EditNote -> {
                NoteEditorScreen(
                    initialNote = screen.note,
                    isDarkMode = isDarkMode,
                    repository = repository,
                    preferences = preferences,
                    onBack = { currentScreen = Screen.MainWorkspace() },
                    onOpenTableEditor = { initialData, onResult ->
                        currentScreen = Screen.TableEditor(initialData, onResult)
                    },
                    onSaveNote = { id, title, content, category, isBold, isItalic, isUnderline, isStrikethrough, isCodeFormat, fontSize, fontColorHex, alignment, listType, tableData, styleSpansJson, attachmentsJson, chosenFolder, onSaved ->
                        coroutineScope.launch {
                            val finalTitle = title.ifBlank { "Untitled Note" }
                            val existing = screen.note
                            val targetFolder = when (category) {
                                "API" -> "APIs Keys"
                                "Code" -> "Code"
                                "Media" -> "Media"
                                "Personal" -> "Personal"
                                else -> existing?.folder ?: "All Notes"
                            }.let { auto ->
                                when {
                                    existing?.isHidden == true -> existing.folder // never move a hidden note out of the vault
                                    chosenFolder.isNotBlank() -> chosenFolder      // explicit "Save in" choice
                                    else -> auto
                                }
                            }
                            val entity = NoteEntity(
                                id = id,
                                title = finalTitle,
                                content = content,
                                category = category,
                                folder = targetFolder,
                                isFavorite = existing?.isFavorite ?: false,
                                isPinned = existing?.isPinned ?: false,
                                isLocked = existing?.isLocked ?: false,
                                isHidden = existing?.isHidden ?: false,
                                originalFolder = existing?.originalFolder ?: "All Notes",
                                isTrash = existing?.isTrash ?: false,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                isStrikethrough = isStrikethrough,
                                isCodeFormat = isCodeFormat,
                                fontSize = fontSize,
                                fontColorHex = fontColorHex,
                                alignment = alignment,
                                listType = listType,
                                tableData = tableData,
                                styleSpansJson = styleSpansJson,
                                attachmentsJson = attachmentsJson,
                                updatedAt = System.currentTimeMillis()
                            )
                            val savedId = repository.saveNote(entity)
                            onSaved(savedId)
                        }
                    }
                )
            }

            is Screen.TableEditor -> {
                TableEditorScreen(
                    initialTableData = screen.initialTableData,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.EditNote(null) },
                    onSaveTable = { savedData ->
                        screen.onSaved(savedData)
                        currentScreen = Screen.EditNote(null)
                    }
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() },
                    // Settings already verified the PIN: mark the vault open so the
                    // Security Area does not ask a second time.
                    onOpenSecurityArea = {
                        isSecurityVaultUnlocked = true
                        currentScreen = Screen.SecurityArea
                    },
                    onOpenApiRoom = { currentScreen = Screen.ApiRoom },
                    onOpenThemeSettings = { currentScreen = Screen.ThemeSettings },
                    onOpenRecycleBin = {
                        screenBeforeRecycleBin = Screen.Settings
                        currentScreen = Screen.RecycleBin
                    }
                )
            }

            is Screen.ApiRoom -> {
                ApiRoomScreen(
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.Settings }
                )
            }

            is Screen.SecurityArea -> {
                SecurityAreaScreen(
                    repository = repository,
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { leaveSecurityArea() },
                    onOpenNote = { note -> currentScreen = Screen.ReadNote(note) },
                    onOpenSettings = { currentScreen = Screen.SecuritySettings },
                    onAddHiddenNotes = { currentScreen = Screen.MainWorkspace("All Notes", pickForHide = true) },
                    vaultUnlockedExternal = isSecurityVaultUnlocked,
                    onVaultUnlockedChange = { isSecurityVaultUnlocked = it }
                )
            }

            is Screen.SecuritySettings -> {
                SecuritySettingsScreen(
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.SecurityArea }
                )
            }

            is Screen.ThemeSettings -> {
                ThemeSettingsScreen(
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.Settings }
                )
            }

            is Screen.HtmlViewer -> {
                HtmlViewerScreen(
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() }
                )
            }

            is Screen.NameGenerator -> {
                NameGeneratorScreen(
                    repository = repository,
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() }
                )
            }

            is Screen.TempMail -> {
                TempMailScreen(
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() }
                )
            }

            is Screen.CommandMode -> {
                CommandModeScreen(
                    repository = repository,
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() },
                    onCreateNewNote = { currentScreen = Screen.EditNote(null) },
                    onOpenNote = { note -> currentScreen = Screen.ReadNote(note) },
                    onOpenFolder = { folder -> currentScreen = Screen.MainWorkspace(folder) }
                )
            }

            is Screen.RecycleBin -> {
                RecycleBinScreen(
                    repository = repository,
                    preferences = preferences,
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = screenBeforeRecycleBin }
                )
            }

            is Screen.StorageEditor -> {
                StorageFileEditorScreen(
                    isDarkMode = isDarkMode,
                    onBack = { currentScreen = Screen.MainWorkspace() },
                    onOpenPdf = { uri ->
                        currentScreen = Screen.PdfViewer(uri, returnTo = Screen.StorageEditor)
                    },
                    onOpenFileInEditor = { fileTitle, fileContent, category ->
                        val virtualNote = NoteEntity(
                            id = 0L,
                            title = fileTitle,
                            content = fileContent,
                            category = category,
                            isCodeFormat = category == "Code"
                        )
                        currentScreen = Screen.EditNote(virtualNote)
                    }
                )
            }

            is Screen.PdfViewer -> {
                PdfViewerScreen(
                    pdfUri = screen.uri,
                    isDarkMode = isDarkMode,
                    onClose = { currentScreen = screen.returnTo }
                )
            }
        }
        }

        pendingDeleteNoteId?.let { noteId ->
            com.example.ui.components.DeletePinDialog(
                preferences = preferences,
                isDarkMode = isDarkMode,
                onVerified = {
                    pendingDeleteNoteId = null
                    coroutineScope.launch {
                        repository.moveToTrash(noteId)
                        currentScreen = Screen.MainWorkspace()
                    }
                },
                onDismiss = { pendingDeleteNoteId = null }
            )
        }

        // Navigation Drawer / Sidebar
        GlassSidebar(
            isOpen = isSidebarOpen,
            isDarkMode = isDarkMode,
            preferences = preferences,
            onClose = { isSidebarOpen = false },
            onNavigateHome = {
                isSidebarOpen = false
                currentScreen = Screen.MainWorkspace("All Notes")
            },
            onNavigateFolder = { folder ->
                isSidebarOpen = false
                val targetFolder = when (folder) {
                    "Code Snippets" -> "Code"
                    else -> folder
                }
                currentScreen = Screen.MainWorkspace(targetFolder)
            },
            onOpenFileEditor = {
                isSidebarOpen = false
                currentScreen = Screen.StorageEditor
            },
            onOpenHtmlViewer = {
                isSidebarOpen = false
                currentScreen = Screen.HtmlViewer
            },
            onOpenNameGenerator = {
                isSidebarOpen = false
                currentScreen = Screen.NameGenerator
            },
            onOpenCommandMode = {
                isSidebarOpen = false
                currentScreen = Screen.CommandMode
            },
            onOpenTempMail = {
                isSidebarOpen = false
                currentScreen = Screen.TempMail
            },
            onOpenSettings = {
                isSidebarOpen = false
                currentScreen = Screen.Settings
            },
            onOpenRecycleBin = {
                isSidebarOpen = false
                screenBeforeRecycleBin = Screen.MainWorkspace()
                currentScreen = Screen.RecycleBin
            },
            onOpenFeaturesModal = {
                isSidebarOpen = false
                isAiChatOpen = true
            },
            onOpenNoteById = { noteId ->
                coroutineScope.launch {
                    repository.getNoteByIdOnce(noteId)?.let { note ->
                        currentScreen = Screen.ReadNote(note)
                    }
                }
            }
        )

        // AI Chat Engine Drawer
        AiChatScreen(
            isOpen = isAiChatOpen,
            isDarkMode = isDarkMode,
            preferences = preferences,
            repository = repository,
            currentNoteContent = (currentScreen as? Screen.ReadNote)?.note?.content
                ?: (currentScreen as? Screen.EditNote)?.note?.content,
            onClose = { isAiChatOpen = false },
            onCreateNoteFromAi = { title, content, category ->
                coroutineScope.launch {
                    val newEntity = NoteEntity(
                        id = 0L,
                        title = title,
                        content = content,
                        category = category,
                        isCodeFormat = category == "Code"
                    )
                    repository.saveNote(newEntity)
                }
            },
            onOpenNoteFromAi = { note ->
                isAiChatOpen = false
                currentScreen = Screen.ReadNote(note)
            },
            onOpenFolderFromAi = { folder ->
                isAiChatOpen = false
                currentScreen = Screen.MainWorkspace(folder)
            }
        )
    }
}
