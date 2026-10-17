package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.NoteEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.NoteRepository
import com.example.ui.components.ExportDialog
import com.example.ui.components.GlassBackground
import dev.chrisbanes.haze.HazeState
import com.example.ui.components.HazeGlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.components.PinLockDialog
import com.example.ui.theme.CategoryApiColor
import com.example.ui.theme.CategoryCodeColor
import com.example.ui.theme.CategoryGeneralColor
import com.example.ui.theme.CategoryMediaColor
import com.example.ui.theme.CategoryPersonalColor
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainWorkspaceScreen(
    repository: NoteRepository,
    preferences: AppPreferences,
    initialFolder: String = "All Notes",
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    // Security Area "+" flow: user picks notes here, they are moved to Hidden Notes
    pickForHide: Boolean = false,
    onPickForHideDone: (List<NoteEntity>) -> Unit = {},
    onPickForHideCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val blurApis by preferences.blurApis.collectAsState()
    val lockPin by preferences.lockPin.collectAsState()
    val lockedFolders by preferences.lockedFolders.collectAsState()
    val hasCustomPin by preferences.hasCustomPin.collectAsState()
    val secQuestion by preferences.securityQuestion.collectAsState()
    val secAnswer by preferences.securityAnswer.collectAsState()
    val customFolders by preferences.customFolders.collectAsState()
    val folderOrder by preferences.folderOrder.collectAsState()
    val hiddenFolders by preferences.hiddenFolders.collectAsState()
    val noteAlarms by preferences.noteAlarms.collectAsState()
    val blinkOnAlarmActive by preferences.blinkOnAlarmActive.collectAsState()
    val hasActiveAlarmAnywhere = noteAlarms.isNotEmpty()
    var pickedIds by remember { mutableStateOf(setOf<Long>()) }
    // Delete actions wait here for PIN verification when "Verify PIN while deleting" is on
    var pendingDeleteVerify by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun guardDelete(action: () -> Unit) {
        if (com.example.ui.components.needsDeleteVerification(preferences)) pendingDeleteVerify = action else action()
    }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    // Pending action to run once first-time PIN setup finishes (locking a note/folder
    // for the very first time forces Create PIN -> Confirm PIN -> Security Question
    // before the lock is actually applied — Item 1 "Direct Lock Setup")
    var pendingSetupThenAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Pending action that requires the EXISTING correct PIN before running
    // (used to gate unlocking an already-locked note/folder from the menu)
    var pendingVerifyThenAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isDeepSearch by remember { mutableStateOf(false) } // PART A Item 5: Deep Search toggle
    var selectedFolder by remember(initialFolder) { mutableStateOf(initialFolder) }

    // Telegram Long-Press Action Sheet state (PART A Item 2)
    var longPressedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var longPressedFolder by remember { mutableStateOf<String?>(null) }
    var showFolderReorderSheet by remember { mutableStateOf(false) }
    var noteToExport by remember { mutableStateOf<NoteEntity?>(null) }

    // PIN lock prompt for protected notes
    var pendingLockedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var unlockedFoldersThisSession by remember { mutableStateOf(setOf<String>()) }
    var pendingFolderToOpen by remember { mutableStateOf<String?>(null) }

    fun openFolder(folderName: String) {
        val requiresLock = preferences.isFolderLocked(folderName)
        if (requiresLock && folderName !in unlockedFoldersThisSession) {
            pendingFolderToOpen = folderName
        } else {
            selectedFolder = folderName
        }
    }

    androidx.activity.compose.BackHandler(enabled = pickForHide) { onPickForHideCancel() }

    // Observe active notes
    val allNotes by repository.allActiveNotes.collectAsState(initial = emptyList())

    // A folder counts as "closed" while it is locked and not yet unlocked this session.
    fun isFolderClosed(name: String): Boolean = name in lockedFolders && name !in unlockedFoldersThisSession

    // Item 2/40: the contents of a locked folder must not leak through All Notes,
    // Favorites or search, and the folder itself can never be shown without its PIN
    // (this also covers folders opened via sidebar / Command Mode / chatbot).
    androidx.compose.runtime.LaunchedEffect(selectedFolder, lockedFolders, unlockedFoldersThisSession) {
        if (isFolderClosed(selectedFolder)) {
            val wanted = selectedFolder
            selectedFolder = "All Notes"
            pendingFolderToOpen = wanted
        }
    }

    val filteredNotes = remember(allNotes, selectedFolder, searchQuery, isDeepSearch, lockedFolders, unlockedFoldersThisSession) {
        allNotes.filter { note ->
            if (note.isTrash) return@filter false

            val noteFolderNames = listOfNotNull(
                note.folder,
                when (note.category) {
                    "API" -> "APIs Keys"
                    "Code" -> "Code"
                    "Media" -> "Media"
                    "Personal" -> "Personal"
                    else -> null
                }
            )
            if (noteFolderNames.any { isFolderClosed(it) }) return@filter false

            // Folder Filter
            val matchesFolder = when (selectedFolder) {
                "All Notes" -> note.folder !in hiddenFolders
                "Favorites" -> note.isFavorite
                "APIs Keys" -> note.category == "API"
                "Code" -> note.category == "Code"
                "Media" -> note.category == "Media"
                "Personal" -> note.category == "Personal"
                else -> note.folder == selectedFolder
            }

            if (!matchesFolder) return@filter false

            // Search Query Filter
            if (searchQuery.isBlank()) {
                true
            } else {
                if (isDeepSearch) {
                    // Deep search = Title or Content
                    note.title.contains(searchQuery, ignoreCase = true) ||
                            note.content.contains(searchQuery, ignoreCase = true)
                } else {
                    // Normal search = Title only
                    note.title.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    val systemFolderNames = setOf("All Notes", "Favorites", "APIs Keys", "Code", "Media", "Personal")
    val folderChips = remember(folderOrder, customFolders, hiddenFolders) {
        val all = buildList {
            addAll(folderOrder)
            customFolders.forEach { if (it !in this) add(it) }
        }
        all.distinct().filter { it !in hiddenFolders }
    }

    // Live note count per folder (All Notes is global, so it counts every visible note)
    val folderCounts = remember(allNotes, lockedFolders) {
        fun count(folder: String) = allNotes.count {
            when (folder) {
                "All Notes" -> true
                "Favorites" -> it.isFavorite
                "APIs Keys" -> it.category == "API"
                "Code" -> it.category == "Code"
                "Media" -> it.category == "Media"
                "Personal" -> it.category == "Personal"
                else -> it.folder == folder
            }
        }
        (listOf("All Notes", "Favorites", "APIs Keys", "Code", "Media", "Personal") + customFolders)
            .distinct().associateWith { count(it) }
    }

    fun exportFolderAsZip(folderName: String) {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val notesInFolder = allNotes.filter {
                    when (folderName) {
                        "All Notes" -> true
                        "Favorites" -> it.isFavorite
                        "APIs Keys" -> it.category == "API"
                        "Code" -> it.category == "Code"
                        "Media" -> it.category == "Media"
                        "Personal" -> it.category == "Personal"
                        else -> it.folder == folderName
                    }
                }

                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val zipName = "${folderName.replace(" ", "_")}_notes.zip"
                val zipFile = File(exportDir, zipName)
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    notesInFolder.forEachIndexed { index, note ->
                        val fileName = "${index + 1}_${note.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.txt"
                        val entry = ZipEntry(fileName)
                        zos.putNextEntry(entry)
                        val noteBody = "TITLE: ${note.title}\nCATEGORY: ${note.category}\nDATE: ${Date(note.updatedAt)}\n\n${com.example.ui.util.ImageMarkers.strip(note.content)}"
                        zos.write(noteBody.toByteArray())
                        zos.closeEntry()
                    }
                }

                // Item 28/36: save into shared storage (Downloads/AU Notes), not app-private files.
                val saved = com.example.ui.util.NoteExporter.saveToDevice(context, zipFile, zipName, "application/zip")
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (saved != null) {
                        Toast.makeText(context, "Exported ${notesInFolder.size} notes to ${saved.displayPath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Export failed: could not write to device storage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    GlassBackground(isDarkMode = isDarkMode, hazeState = hazeState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Item 16: small red blinking light on the main screen's
                        // sidebar/menu icon while any alarm is active.
                        Box {
                            NeuIconButton(
                                icon = Icons.Default.Menu,
                                contentDescription = "Open Sidebar",
                                isDarkMode = isDarkMode,
                                size = 40.dp,
                                iconSize = 20.dp,
                                tint = if (isDarkMode) Color.White else Color(0xFF222222),
                                onClick = onOpenSidebar
                            )
                            if (hasActiveAlarmAnywhere && blinkOnAlarmActive) {
                                val infiniteTransition = rememberInfiniteTransition(label = "menuBlink")
                                val blinkAlpha by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600),
                                        repeatMode = RepeatMode.Reverse
                                    ), label = "menuBlinkAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744).copy(alpha = blinkAlpha))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = if (pickForHide) "SELECT NOTES TO HIDE" else "AU NOTES",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CrimsonPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (pickForHide) "${pickedIds.size} selected" else "${filteredNotes.size} notes in $selectedFolder",
                                fontSize = 11.sp,
                                color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF666666),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pickForHide) {
                            androidx.compose.material3.TextButton(onClick = onPickForHideCancel) {
                                Text("Cancel", color = if (isDarkMode) Color.White else Color.DarkGray, fontWeight = FontWeight.SemiBold)
                            }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val chosen = allNotes.filter { it.id in pickedIds }
                                    if (chosen.isEmpty()) {
                                        Toast.makeText(context, "Select at least one note", Toast.LENGTH_SHORT).show()
                                    } else onPickForHideDone(chosen)
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                            ) { Text("Done", fontWeight = FontWeight.Bold) }
                        } else {
                            NeuIconButton(
                                painter = painterResource(R.drawable.ic_settings_gear),
                                contentDescription = "Settings",
                                isDarkMode = isDarkMode,
                                size = 38.dp,
                                iconSize = 18.dp,
                                tint = CrimsonPrimary,
                                onClick = onOpenSettings
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar with Deep Search Toggle (PART A Item 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (isDeepSearch) "Deep Search (content & title)..." else "Search title...",
                                fontSize = 13.sp,
                                color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Gray
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            // Deep Search Toggle Chip
                            Row(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDeepSearch) CrimsonPrimary else (if (isDarkMode) Color(0x33FFFFFF) else Color(0x1F000000)))
                                    .clickable { isDeepSearch = !isDeepSearch }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Deep",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDeepSearch) Color.White else (if (isDarkMode) Color.White.copy(0.7f) else Color.DarkGray)
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = if (isDarkMode) Color(0x22FFFFFF) else Color(0x1F718096),
                            focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                            unfocusedTextColor = if (isDarkMode) Color.White else Color.Black
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Horizontal Folder Filter Chips — long-press a chip, then drag it left/right to reorder
                // (Telegram style). A long-press that is released without moving opens the folder menu.
                val chipListState = rememberLazyListState()
                var chipOrder by remember { mutableStateOf(folderChips) }
                var draggingFolder by remember { mutableStateOf<String?>(null) }
                var dragOffsetX by remember { mutableFloatStateOf(0f) }
                var dragMoved by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(folderChips) {
                    if (draggingFolder == null) chipOrder = folderChips
                }
                val chipSpacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }

                fun finishChipDrag(folder: String) {
                    val moved = dragMoved
                    draggingFolder = null
                    dragOffsetX = 0f
                    dragMoved = false
                    if (moved) {
                        // keep hidden folders' relative order too, then persist
                        val keep = (folderOrder + customFolders).distinct().filter { it !in chipOrder }
                        preferences.setFolderOrder(chipOrder + keep)
                    } else {
                        longPressedFolder = folder
                    }
                }

                LazyRow(
                    state = chipListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chipOrder, key = { it }) { folder ->
                        val isSelected = selectedFolder == folder
                        val isFolderLocked = lockedFolders.contains(folder)

                        // Liquid spring press-scale for folder chips
                        val chipInteraction = remember(folder) { MutableInteractionSource() }
                        val chipPressed by chipInteraction.collectIsPressedAsState()
                        val chipScale by animateFloatAsState(
                            targetValue = if (chipPressed) 0.94f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "folder_chip_press"
                        )

                        val folderIconRes = when (folder) {
                            "All Notes" -> R.drawable.ic_custom_folder
                            "Favorites" -> R.drawable.ic_svg_favorite
                            "APIs Keys" -> R.drawable.ic_vpn_api
                            "Code" -> R.drawable.ic_code_snippet
                            "Media" -> R.drawable.ic_media_play
                            else -> R.drawable.ic_custom_folder
                        }

                        val iconTint = when (folder) {
                            "All Notes" -> Color(0xFFFFB300)
                            "Favorites" -> Color(0xFFFF5252)
                            "APIs Keys" -> CategoryApiColor
                            "Code" -> CategoryCodeColor
                            "Media" -> CategoryMediaColor
                            "Personal" -> CategoryPersonalColor
                            else -> CrimsonPrimary
                        }

                        val isDragging = draggingFolder == folder
                        Box(
                            modifier = Modifier
                                .then(
                                    // neighbours glide out of the way with a spring; the dragged chip follows the finger
                                    if (isDragging) Modifier.zIndex(1f)
                                    else Modifier.animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                )
                                .graphicsLayer {
                                    val lift = if (isDragging) 1.08f else 1f
                                    scaleX = chipScale * lift
                                    scaleY = chipScale * lift
                                    translationX = if (isDragging) dragOffsetX else 0f
                                    shadowElevation = if (isDragging) 18f else 0f
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) CrimsonPrimary
                                    else (if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CrimsonPrimary else (if (isDarkMode) Color(0x26FFFFFF) else Color(0x1F718096)),
                                    RoundedCornerShape(12.dp)
                                )
                                .pointerInput(folder) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingFolder = folder
                                            dragOffsetX = 0f
                                            dragMoved = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetX += amount.x
                                            if (kotlin.math.abs(dragOffsetX) > 14f) dragMoved = true
                                            val infos = chipListState.layoutInfo.visibleItemsInfo
                                            val me = infos.firstOrNull { it.key == folder }
                                            if (me != null) {
                                                val centre = me.offset + dragOffsetX + me.size / 2f
                                                val hit = infos.firstOrNull {
                                                    it.key != folder && it.key in chipOrder &&
                                                        centre >= it.offset && centre <= it.offset + it.size
                                                }
                                                if (hit != null) {
                                                    val from = chipOrder.indexOf(folder)
                                                    val to = chipOrder.indexOf(hit.key as String)
                                                    if (from >= 0 && to >= 0 && from != to) {
                                                        chipOrder = chipOrder.toMutableList().apply { add(to, removeAt(from)) }
                                                        // the chip's slot moved by the neighbour's width: compensate so it stays under the finger
                                                        val shift = hit.size + chipSpacingPx
                                                        dragOffsetX -= if (to > from) shift else -shift
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = { finishChipDrag(folder) },
                                        onDragCancel = { finishChipDrag(folder) }
                                    )
                                }
                                .clickable(
                                    interactionSource = chipInteraction,
                                    indication = null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openFolder(folder)
                                    }
                                )
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (folder == "Personal") {
                                    Icon(
                                        imageVector = Icons.Default.FolderSpecial,
                                        contentDescription = folder,
                                        tint = if (isSelected) Color.White else iconTint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(folderIconRes),
                                        contentDescription = folder,
                                        tint = if (isSelected) Color.White else iconTint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isFolderLocked) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked Folder",
                                        tint = if (isSelected) Color.White else CrimsonPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = folder,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else (if (isDarkMode) Color.White.copy(0.85f) else Color(0xFF333333))
                                )
                                // Locked folders never reveal how many notes they hold
                                if (!isFolderLocked) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${folderCounts[folder] ?: 0}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White.copy(0.85f) else CrimsonPrimary
                                    )
                                }
                            }
                        }
                    }

                    // "Add Folder" chip — Item 7: always the last item in the row.
                    item(key = "__add_folder__") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isDarkMode) Color(0x26FFFFFF) else Color(0x1F718096),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showAddFolderDialog = true
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Folder",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Folder",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CrimsonPrimary
                            )
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notes List with Compact Balanced Size (PART A Item 1)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sticky_note),
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No notes found in $selectedFolder",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+' to create your first note",
                                color = CrimsonPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Plain list: no per-folder replay animation (the old staggered "wave" made folder
                    // switching lag because every card was rebuilt and animated at once).
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            CompactNoteCard(
                                note = note,
                                hazeState = hazeState,
                                isDarkMode = isDarkMode,
                                blurApis = blurApis,
                                selectionMode = pickForHide,
                                selected = note.id in pickedIds,
                                onOpen = {
                                    if (pickForHide) {
                                        pickedIds = if (note.id in pickedIds) pickedIds - note.id else pickedIds + note.id
                                    } else if (note.isLocked) {
                                        pendingLockedNote = note
                                    } else {
                                        onOpenNote(note)
                                    }
                                },
                                onLongPress = {
                                    if (pickForHide) {
                                        pickedIds = pickedIds + note.id
                                    } else longPressedNote = note
                                },
                                onCopy = {}
                            )
                        }
                    }
                }
                }
            }

            // Floating Buttons Row: Floating AU Bot + Create Note FAB
            if (!pickForHide) Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Floating AU AI Bot with custom sphere mascot icon (PART F Item 2)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .clickable { onOpenAiChat() }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.au_bot_icon_1790271144581),
                            contentDescription = "AU AI Assistant",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Create Note FAB
                    FloatingActionButton(
                        onClick = onCreateNote,
                        containerColor = CrimsonPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Note", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    // Long-Press Action Sheet for Notes
    longPressedNote?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { longPressedNote = null },
            containerColor = if (isDarkMode) Color(0xFF1E222B) else Color(0xFFF6F8FB),
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = note.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Quick Actions",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = if (isDarkMode) Color(0x22FFFFFF) else Color(0x1F000000))
                Spacer(modifier = Modifier.height(10.dp))

                // 1. Reorder / Pin Note
                // 0. Favorite toggle — moved here from the card's icon button (Item 6)
                TelegramActionItem(
                    icon = painterResource(if (note.isFavorite) R.drawable.ic_svg_favorite else R.drawable.ic_svg_favorite),
                    title = if (note.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    isDarkMode = isDarkMode,
                    onClick = {
                        coroutineScope.launch {
                            repository.toggleFavorite(note.id, note.isFavorite)
                            longPressedNote = null
                            Toast.makeText(context, if (note.isFavorite) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                TelegramActionItem(
                    icon = painterResource(R.drawable.ic_edit_note),
                    title = if (note.isPinned) "Unpin Note" else "Reorder / Pin to Top",
                    isDarkMode = isDarkMode,
                    onClick = {
                        coroutineScope.launch {
                            repository.togglePin(note.id, note.isPinned)
                            longPressedNote = null
                            Toast.makeText(context, if (note.isPinned) "Unpinned" else "Pinned to top", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 2. Lock / Unlock Note with SVG — unlocking always requires PIN
                // verification; locking for the very first time forces PIN setup.
                TelegramActionItem(
                    icon = painterResource(if (note.isLocked) R.drawable.ic_security_unlock else R.drawable.ic_security_lock),
                    title = if (note.isLocked) "Unlock Note" else "Lock Note with Passcode",
                    isDarkMode = isDarkMode,
                    onClick = {
                        longPressedNote = null
                        val performToggle = {
                            coroutineScope.launch {
                                repository.toggleLock(note.id, note.isLocked)
                                Toast.makeText(context, if (note.isLocked) "Note Unlocked" else "Note Locked", Toast.LENGTH_SHORT).show()
                            }
                            Unit
                        }
                        when {
                            note.isLocked -> pendingVerifyThenAction = performToggle
                            !hasCustomPin -> pendingSetupThenAction = performToggle
                            else -> performToggle()
                        }
                        Unit
                    }
                )

                // 3. Export as ZIP / File
                TelegramActionItem(
                    icon = painterResource(R.drawable.ic_export_download),
                    title = "Export Note",
                    isDarkMode = isDarkMode,
                    onClick = {
                        noteToExport = note
                        longPressedNote = null
                    }
                )

                // 4. Delete Note
                TelegramActionItem(
                    icon = painterResource(R.drawable.ic_recycle_bin),
                    title = "Delete (Move to Recycle Bin)",
                    isDarkMode = isDarkMode,
                    isDestructive = true,
                    onClick = {
                        longPressedNote = null
                        guardDelete {
                            coroutineScope.launch {
                                repository.moveToTrash(note.id)
                                Toast.makeText(context, "Moved '${note.title}' to Recycle Bin", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Long-Press Action Sheet for Folders
    longPressedFolder?.let { folder ->
        ModalBottomSheet(
            onDismissRequest = { longPressedFolder = null },
            containerColor = if (isDarkMode) Color(0xFF1E222B) else Color(0xFFF6F8FB),
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Folder: $folder",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonPrimary
                )
                Text(
                    text = "Folder Management Options",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = if (isDarkMode) Color(0x22FFFFFF) else Color(0x1F000000))
                Spacer(modifier = Modifier.height(10.dp))

                // Hold any folder to open this menu; Reorder gives the Telegram-like
                // long-press-and-drag ordering flow without changing folder contents.
                TelegramActionItem(
                    icon = painterResource(R.drawable.ic_custom_folder),
                    title = "Reorder Folders",
                    isDarkMode = isDarkMode,
                    onClick = {
                        longPressedFolder = null
                        showFolderReorderSheet = true
                    }
                )

                val isLocked = preferences.isFolderLocked(folder)
                TelegramActionItem(
                    icon = painterResource(if (isLocked) R.drawable.ic_security_unlock else R.drawable.ic_security_lock),
                    title = if (isLocked) "Unlock Folder" else "Lock Folder with PIN",
                    isDarkMode = isDarkMode,
                    onClick = {
                        longPressedFolder = null
                        val performToggle = {
                            preferences.toggleFolderLock(folder)
                            unlockedFoldersThisSession = unlockedFoldersThisSession - folder
                            if (selectedFolder == folder) selectedFolder = "All Notes"
                            Toast.makeText(context, if (isLocked) "Folder Unlocked" else "Folder Locked with PIN", Toast.LENGTH_SHORT).show()
                            Unit
                        }
                        when {
                            isLocked -> pendingVerifyThenAction = performToggle
                            !hasCustomPin -> pendingSetupThenAction = performToggle
                            else -> performToggle()
                        }
                        Unit
                    }
                )

                // 2. Export Folder as ZIP (.txt extension)
                TelegramActionItem(
                    icon = painterResource(R.drawable.ic_export_download),
                    title = "Export as ZIP (${folder})",
                    isDarkMode = isDarkMode,
                    onClick = {
                        exportFolderAsZip(folder)
                        longPressedFolder = null
                    }
                )

                // 3. Hide Folder into Security Area — only for custom folders (Item 3)
                if (folder !in systemFolderNames) {
                    TelegramActionItem(
                        icon = painterResource(R.drawable.ic_security_lock),
                        title = "Hide Folder",
                        isDarkMode = isDarkMode,
                        onClick = {
                            val doHide = {
                                preferences.hideFolder(folder)
                                if (selectedFolder == folder) selectedFolder = "All Notes"
                                longPressedFolder = null
                                Toast.makeText(context, "Folder hidden — find it in Settings > Security Area", Toast.LENGTH_SHORT).show()
                                Unit
                            }
                            if (!hasCustomPin) pendingSetupThenAction = doHide else doHide()
                        }
                    )
                }

                // 4. Delete Folder — only for user-created custom folders (Item 7)
                if (folder !in systemFolderNames) {
                    TelegramActionItem(
                        icon = painterResource(R.drawable.ic_svg_delete),
                        title = "Delete Folder",
                        isDarkMode = isDarkMode,
                        onClick = {
                            longPressedFolder = null
                            guardDelete {
                                preferences.deleteCustomFolder(folder)
                                if (selectedFolder == folder) selectedFolder = "All Notes"
                                Toast.makeText(context, "Folder moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Folder reorder sheet — hold and drag a row to change the persisted folder order.
    if (showFolderReorderSheet) {
        FolderReorderSheet(
            initialOrder = folderOrder + customFolders.filter { it !in folderOrder },
            isDarkMode = isDarkMode,
            onDismiss = { showFolderReorderSheet = false },
            onSave = { newOrder ->
                preferences.setFolderOrder(newOrder)
                showFolderReorderSheet = false
                Toast.makeText(context, "Folder order saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // "Add Folder" dialog — Item 7
    if (showAddFolderDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddFolderDialog = false; newFolderNameInput = "" }) {
            HazeGlassCard(
                hazeState = hazeState,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                isDarkMode = isDarkMode,
                strong = true
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "New Folder",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showAddFolderDialog = false; newFolderNameInput = "" }) {
                            Text("Cancel")
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                if (newFolderNameInput.isNotBlank()) {
                                    preferences.addCustomFolder(newFolderNameInput)
                                    showAddFolderDialog = false
                                    newFolderNameInput = ""
                                } else {
                                    Toast.makeText(context, "Enter a folder name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    pendingDeleteVerify?.let { action ->
        com.example.ui.components.DeletePinDialog(
            preferences = preferences,
            isDarkMode = isDarkMode,
            onVerified = { pendingDeleteVerify = null; action() },
            onDismiss = { pendingDeleteVerify = null }
        )
    }

    // Export Dialog (PART G)
    noteToExport?.let { note ->
        ExportDialog(
            note = note,
            isDarkMode = isDarkMode,
            onDismiss = { noteToExport = null }
        )
    }

    // PIN dialog for unlocking note
    pendingLockedNote?.let { note ->
        PinLockDialog(
            correctPin = lockPin,
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = !hasCustomPin,
            onDismiss = { pendingLockedNote = null },
            onUnlocked = {
                pendingLockedNote = null
                onOpenNote(note)
            },
            onSetupComplete = { pin, question, answer ->
                preferences.setSecurityDetails(pin, question, answer)
            },
            onPinReset = { newPin -> preferences.setLockPin(newPin) }
        )
    }

    // PIN dialog for unlocking folder
    pendingFolderToOpen?.let { folder ->
        PinLockDialog(
            correctPin = lockPin,
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = !hasCustomPin,
            onDismiss = { pendingFolderToOpen = null },
            onUnlocked = {
                unlockedFoldersThisSession = unlockedFoldersThisSession + folder
                selectedFolder = folder
                pendingFolderToOpen = null
            },
            onSetupComplete = { pin, question, answer ->
                preferences.setSecurityDetails(pin, question, answer)
            },
            onPinReset = { newPin -> preferences.setLockPin(newPin) }
        )
    }

    // PIN dialog gating any action that needs the EXISTING PIN re-verified
    // (e.g. unlocking a note/folder from the long-press menu)
    pendingVerifyThenAction?.let { action ->
        PinLockDialog(
            correctPin = lockPin,
            title = "Verify PIN to Unlock",
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = !hasCustomPin,
            onDismiss = { pendingVerifyThenAction = null },
            onUnlocked = {
                action()
                pendingVerifyThenAction = null
            },
            onSetupComplete = { pin, question, answer ->
                preferences.setSecurityDetails(pin, question, answer)
            },
            onPinReset = { newPin -> preferences.setLockPin(newPin) }
        )
    }

    // PIN setup dialog triggered the very first time the user locks a note/folder
    // without ever having configured a PIN yet (Item 1 "Direct Lock Setup")
    pendingSetupThenAction?.let { action ->
        PinLockDialog(
            correctPin = lockPin,
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = true,
            onDismiss = { pendingSetupThenAction = null },
            onUnlocked = {
                action()
                pendingSetupThenAction = null
            },
            onSetupComplete = { pin, question, answer ->
                preferences.setSecurityDetails(pin, question, answer)
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactNoteCard(
    note: NoteEntity,
    hazeState: HazeState,
    isDarkMode: Boolean,
    blurApis: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    val categoryColor = when (note.category) {
        "API" -> CategoryApiColor
        "Code" -> CategoryCodeColor
        "Media" -> CategoryMediaColor
        "Personal" -> CategoryPersonalColor
        else -> CategoryGeneralColor
    }

    val displayContent = if (note.category == "API" && blurApis) {
        "•••••••••••••••••••• (API Key Blurred)"
    } else {
        com.example.ui.util.ImageMarkers.strip(note.content).lines().firstOrNull { it.isNotBlank() } ?: ""
    }

    val formattedTime = remember(note.updatedAt) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    }

    // Liquid spring press animation: 1.0 -> 0.94 -> ~1.02 -> 1.0
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember(note.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "note_card_press"
    )

    // Compact Card — Item 6: reduced from 10dp/44dp to 7dp/36dp so cards read
    // as smaller/denser while keeping the existing glass design language.
    HazeGlassCard(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpen()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            ),
        shape = RoundedCornerShape(14.dp),
        isDarkMode = isDarkMode
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) CrimsonPrimary else Color.Transparent)
                        .border(1.5.dp, if (selected) CrimsonPrimary else Color.Gray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            // Category Indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Main Title & Subtitle Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = CrimsonPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (note.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = note.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color(0xFF111111),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = displayContent,
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.65f) else Color(0xFF555555),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${note.category}",
                        fontSize = 10.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Copy Icon Button — Item 6: replaces the old Favorite star.
            // Favorite toggling moved into the long-press menu so the
            // Favorites folder keeps working.
            val context = LocalContext.current
            val clipboard = LocalClipboardManager.current
            IconButton(
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(com.example.ui.util.ImageMarkers.strip(note.content)))
                    Toast.makeText(context, "Note content copied", Toast.LENGTH_SHORT).show()
                    onCopy()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy note",
                    tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderReorderSheet(
    initialOrder: List<String>,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var order by remember(initialOrder) { mutableStateOf(initialOrder.distinct()) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkMode) Color(0xFF1E222B) else Color(0xFFF6F8FB)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text("Reorder Folders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
            Text("Hold a folder and drag it up or down", fontSize = 11.5.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                itemsIndexed(order, key = { _, item -> item }) { index, folder ->
                    val isDragging = draggingIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f; alpha = if (isDragging) 0.86f else 1f }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDragging) CrimsonPrimary.copy(.12f) else Color.Transparent)
                            .pointerInput(order, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIndex = index; dragOffset = 0f },
                                    onDragCancel = { draggingIndex = null; dragOffset = 0f },
                                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val current = draggingIndex
                                        if (current != null) {
                                            dragOffset += amount.y
                                            val target = ((current * 56f + dragOffset + 28f) / 56f).toInt().coerceIn(0, order.lastIndex)
                                            if (target != current) {
                                                val mutable = order.toMutableList()
                                                val moved = mutable.removeAt(current)
                                                mutable.add(target, moved)
                                                order = mutable
                                                draggingIndex = target
                                                dragOffset = 0f
                                            }
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SwapVert, null, tint = if (isDragging) CrimsonPrimary else Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(folder, modifier = Modifier.weight(1f), fontSize = 14.sp, color = if (isDarkMode) Color.White else Color(0xFF222222))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                androidx.compose.material3.Button(onClick = { onSave(order) }, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)) { Text("Save", color = Color.White) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TelegramActionItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    isDarkMode: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = title,
            tint = if (isDestructive) Color.Red else CrimsonPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDestructive) Color.Red else (if (isDarkMode) Color.White else Color(0xFF222222))
        )
    }
}
