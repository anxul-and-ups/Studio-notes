package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppPreferences
import com.example.data.repository.NoteRepository
import com.example.ui.components.DeletePinDialog
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.components.PinLockDialog
import com.example.ui.components.needsDeleteVerification
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.launch

private enum class BinSection { HOME, NOTES, FOLDERS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    repository: NoteRepository,
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashNotes by repository.trashNotes.collectAsState(initial = emptyList())
    val trashFolders by preferences.trashedFolders.collectAsState()
    val binLocked by preferences.recycleBinLocked.collectAsState()
    val lockPin by preferences.lockPin.collectAsState()
    val hasCustomPin by preferences.hasCustomPin.collectAsState()
    val secQuestion by preferences.securityQuestion.collectAsState()
    val secAnswer by preferences.securityAnswer.collectAsState()

    // The bin asks for the PIN once per visit while it is locked.
    var passed by remember { mutableStateOf(!binLocked) }
    var section by remember { mutableStateOf(BinSection.HOME) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<Long>()) }
    var selectedFolders by remember { mutableStateOf(setOf<String>()) }

    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingForeverDelete by remember { mutableStateOf<(() -> Unit)?>(null) } // needs confirm dialog
    var pendingDeleteVerify by remember { mutableStateOf<(() -> Unit)?>(null) }   // needs PIN
    var showUnlockBinVerify by remember { mutableStateOf(false) }
    var showLockSetup by remember { mutableStateOf(false) }

    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val subColor = if (isDarkMode) Color.White.copy(0.6f) else Color.Gray

    fun exitSelect() { selectMode = false; selectedNotes = emptySet(); selectedFolders = emptySet() }

    /** Runs [action] after PIN verification when "Verify PIN while deleting" is on. */
    fun guardDelete(action: () -> Unit) {
        if (needsDeleteVerification(preferences)) pendingDeleteVerify = action else action()
    }

    BackHandler(enabled = selectMode || section != BinSection.HOME) {
        if (selectMode) exitSelect() else section = BinSection.HOME
    }

    if (!passed) {
        GlassBackground(isDarkMode = isDarkMode) { Box(Modifier.fillMaxSize()) }
        PinLockDialog(
            correctPin = lockPin,
            title = "Recycle Bin Locked",
            subtitle = "Enter 4-digit PIN or use Fingerprint to open",
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = false,
            onDismiss = onBack,
            onUnlocked = { passed = true },
            onPinReset = { preferences.setLockPin(it) }
        )
        return
    }

    GlassBackground(isDarkMode = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    NeuIconButton(
                        icon = if (selectMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        isDarkMode = isDarkMode,
                        size = 38.dp, iconSize = 18.dp,
                        tint = textColor,
                        onClick = {
                            when {
                                selectMode -> exitSelect()
                                section != BinSection.HOME -> section = BinSection.HOME
                                else -> onBack()
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when {
                            selectMode -> "${selectedNotes.size + selectedFolders.size} selected"
                            section == BinSection.NOTES -> "Deleted Notes"
                            section == BinSection.FOLDERS -> "Deleted Folders"
                            else -> "Recycle Bin"
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (section != BinSection.HOME && !selectMode) {
                        val hasItems = if (section == BinSection.NOTES) trashNotes.isNotEmpty() else trashFolders.isNotEmpty()
                        if (hasItems) {
                            NeuIconButton(
                                icon = Icons.Default.ChecklistRtl,
                                contentDescription = "Select",
                                isDarkMode = isDarkMode,
                                size = 38.dp, iconSize = 18.dp,
                                tint = CrimsonPrimary,
                                onClick = { selectMode = true }
                            )
                        }
                    }
                    if (!selectMode) {
                        // Lock / unlock the whole recycle bin
                        NeuIconButton(
                            icon = if (binLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (binLocked) "Unlock recycle bin" else "Lock recycle bin",
                            isDarkMode = isDarkMode,
                            size = 38.dp, iconSize = 18.dp,
                            tint = if (binLocked) CrimsonPrimary else Color.Gray,
                            onClick = {
                                if (binLocked) showUnlockBinVerify = true
                                else if (!hasCustomPin) showLockSetup = true
                                else {
                                    preferences.setRecycleBinLocked(true)
                                    Toast.makeText(context, "Recycle Bin locked", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (section) {
                BinSection.HOME -> {
                    Text(
                        "Deleted items stay here until you restore or erase them.",
                        fontSize = 12.sp, color = subColor, modifier = Modifier.padding(bottom = 14.dp)
                    )
                    BinFolderTile(Icons.Default.StickyNote2, "Notes", "${trashNotes.size} deleted note(s)", isDarkMode) {
                        section = BinSection.NOTES
                    }
                    Spacer(Modifier.height(12.dp))
                    BinFolderTile(Icons.Default.Folder, "Folders", "${trashFolders.size} deleted folder(s)", isDarkMode) {
                        section = BinSection.FOLDERS
                    }
                }

                BinSection.NOTES -> {
                    if (trashNotes.isEmpty()) {
                        EmptyBin("No deleted notes", isDarkMode)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(trashNotes, key = { it.id }) { note ->
                                val selected = note.id in selectedNotes
                                BinRow(
                                    title = note.title,
                                    subtitle = "Deleted • ${note.category}",
                                    selectMode = selectMode,
                                    selected = selected,
                                    isDarkMode = isDarkMode,
                                    onClick = {
                                        if (selectMode) selectedNotes = if (selected) selectedNotes - note.id else selectedNotes + note.id
                                    },
                                    onLongClick = {
                                        selectMode = true
                                        selectedNotes = selectedNotes + note.id
                                    },
                                    onRestore = {
                                        scope.launch {
                                            repository.restoreFromTrash(note.id)
                                            Toast.makeText(context, "Restored note", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDeleteForever = {
                                        pendingForeverDelete = {
                                            guardDelete {
                                                scope.launch {
                                                    repository.permanentDelete(note.id)
                                                    Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        if (!selectMode) {
                            TextButton(onClick = { showEmptyConfirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Empty all deleted notes", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                BinSection.FOLDERS -> {
                    if (trashFolders.isEmpty()) {
                        EmptyBin("No deleted folders", isDarkMode)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(trashFolders, key = { it }) { name ->
                                val selected = name in selectedFolders
                                BinRow(
                                    title = name,
                                    subtitle = "Deleted folder",
                                    selectMode = selectMode,
                                    selected = selected,
                                    isDarkMode = isDarkMode,
                                    onClick = {
                                        if (selectMode) selectedFolders = if (selected) selectedFolders - name else selectedFolders + name
                                    },
                                    onLongClick = {
                                        selectMode = true
                                        selectedFolders = selectedFolders + name
                                    },
                                    onRestore = {
                                        preferences.restoreTrashedFolder(name)
                                        Toast.makeText(context, "Folder restored", Toast.LENGTH_SHORT).show()
                                    },
                                    onDeleteForever = {
                                        pendingForeverDelete = {
                                            guardDelete {
                                                preferences.deleteTrashedFolderForever(name)
                                                Toast.makeText(context, "Folder erased", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Multi-select action bar ----
            if (selectMode) {
                val count = selectedNotes.size + selectedFolders.size
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionPill(
                        modifier = Modifier.weight(1f),
                        label = if (section == BinSection.NOTES && selectedNotes.size == trashNotes.size ||
                            section == BinSection.FOLDERS && selectedFolders.size == trashFolders.size) "Clear" else "Select all",
                        color = Color.Gray, isDarkMode = isDarkMode
                    ) {
                        if (section == BinSection.NOTES) {
                            selectedNotes = if (selectedNotes.size == trashNotes.size) emptySet() else trashNotes.map { it.id }.toSet()
                        } else {
                            selectedFolders = if (selectedFolders.size == trashFolders.size) emptySet() else trashFolders.toSet()
                        }
                    }
                    ActionPill(
                        modifier = Modifier.weight(1f),
                        label = "Restore ($count)",
                        color = Color(0xFF2CB552), isDarkMode = isDarkMode
                    ) {
                        if (count == 0) return@ActionPill
                        scope.launch {
                            repository.restoreFromTrash(selectedNotes)
                            selectedFolders.forEach { preferences.restoreTrashedFolder(it) }
                            Toast.makeText(context, "Restored $count item(s)", Toast.LENGTH_SHORT).show()
                            exitSelect()
                        }
                    }
                    ActionPill(
                        modifier = Modifier.weight(1f),
                        label = "Delete ($count)",
                        color = Color(0xFFDC2626), isDarkMode = isDarkMode
                    ) {
                        if (count == 0) return@ActionPill
                        val notesToDelete = selectedNotes
                        val foldersToDelete = selectedFolders
                        pendingForeverDelete = {
                            guardDelete {
                                scope.launch {
                                    repository.permanentDelete(notesToDelete)
                                    foldersToDelete.forEach { preferences.deleteTrashedFolderForever(it) }
                                    Toast.makeText(context, "Deleted ${notesToDelete.size + foldersToDelete.size} item(s) forever", Toast.LENGTH_SHORT).show()
                                    exitSelect()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Dialogs ----
    pendingForeverDelete?.let { run ->
        AlertDialog(
            onDismissRequest = { pendingForeverDelete = null },
            title = { Text("Delete Forever?") },
            text = { Text("This will be permanently erased and cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = { pendingForeverDelete = null; run() }) {
                    Text("Delete Forever", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingForeverDelete = null }) { Text("Cancel") } }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty deleted notes?") },
            text = { Text("All deleted notes will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyConfirm = false
                    guardDelete {
                        scope.launch {
                            repository.emptyTrash()
                            Toast.makeText(context, "Deleted notes erased", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete All", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") } }
        )
    }

    pendingDeleteVerify?.let { action ->
        DeletePinDialog(
            preferences = preferences,
            isDarkMode = isDarkMode,
            onVerified = { pendingDeleteVerify = null; action() },
            onDismiss = { pendingDeleteVerify = null }
        )
    }

    if (showUnlockBinVerify) {
        PinLockDialog(
            correctPin = lockPin,
            title = "Verify PIN to Unlock Bin",
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            onDismiss = { showUnlockBinVerify = false },
            onUnlocked = {
                showUnlockBinVerify = false
                preferences.setRecycleBinLocked(false)
                Toast.makeText(context, "Recycle Bin unlocked", Toast.LENGTH_SHORT).show()
            },
            onPinReset = { preferences.setLockPin(it) }
        )
    }

    if (showLockSetup) {
        PinLockDialog(
            correctPin = lockPin,
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = true,
            onDismiss = { showLockSetup = false },
            onUnlocked = {
                showLockSetup = false
                preferences.setRecycleBinLocked(true)
                Toast.makeText(context, "Recycle Bin locked", Toast.LENGTH_SHORT).show()
            },
            onSetupComplete = { pin, q, a -> preferences.setSecurityDetails(pin, q, a) }
        )
    }
}

@Composable
private fun EmptyBin(message: String, isDarkMode: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DeleteSweep, null, tint = CrimsonPrimary.copy(alpha = 0.6f), modifier = Modifier.size(60.dp))
        Spacer(Modifier.height(14.dp))
        Text(message, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkMode) Color.White else Color.Black)
    }
}

@Composable
private fun BinFolderTile(icon: ImageVector, title: String, subtitle: String, isDarkMode: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        isDarkMode = isDarkMode,
        elevation = 3.dp,
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(CrimsonPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = CrimsonPrimary, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinRow(
    title: String,
    subtitle: String,
    selectMode: Boolean,
    selected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) CrimsonPrimary.copy(alpha = 0.16f)
                else (if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000))
            )
            .border(1.dp, if (selected) CrimsonPrimary else (if (isDarkMode) Color(0x26FFFFFF) else Color(0x1F718096)), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) CrimsonPrimary else Color.Transparent)
                    .border(1.5.dp, if (selected) CrimsonPrimary else Color.Gray, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, fontSize = 11.sp, color = CrimsonPrimary)
        }
        if (!selectMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuIconButton(
                    icon = Icons.Default.Restore, contentDescription = "Restore", isDarkMode = isDarkMode,
                    size = 36.dp, iconSize = 18.dp, tint = Color(0xFF2CF95F), onClick = onRestore
                )
                NeuIconButton(
                    icon = Icons.Default.DeleteForever, contentDescription = "Delete Forever", isDarkMode = isDarkMode,
                    size = 36.dp, iconSize = 18.dp, tint = Color(0xFFFF5252), onClick = onDeleteForever
                )
            }
        }
    }
}

@Composable
private fun ActionPill(modifier: Modifier, label: String, color: Color, isDarkMode: Boolean, onClick: () -> Unit) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(14.dp), isDarkMode = isDarkMode, elevation = 3.dp, onClick = onClick) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
    }
}
