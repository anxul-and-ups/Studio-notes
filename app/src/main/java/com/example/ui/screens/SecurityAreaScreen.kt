package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.NoteEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.NoteRepository
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.launch

/**
 * Security Area. The user already passed the PIN gate (Settings → Security Area, or the
 * dialog below when the vault re-locked), so there is NO second lock inside any tab.
 */
@Composable
fun SecurityAreaScreen(
    repository: NoteRepository,
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onAddHiddenNotes: () -> Unit,
    vaultUnlockedExternal: Boolean = false,
    onVaultUnlockedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hiddenNotes by repository.hiddenNotes.collectAsState(initial = emptyList())
    val storedPin by preferences.lockPin.collectAsState()
    val secQuestion by preferences.securityQuestion.collectAsState()
    val secAnswer by preferences.securityAnswer.collectAsState()
    val lockedFolders by preferences.lockedFolders.collectAsState()
    val customFolders by preferences.customFolders.collectAsState()
    val hiddenFolders by preferences.hiddenFolders.collectAsState()
    val hasCustomPin by preferences.hasCustomPin.collectAsState()
    var showHideFolderPicker by remember { mutableStateOf(false) }

    var isVaultUnlocked by remember { mutableStateOf(vaultUnlockedExternal) }
    var showAuthPrompt by remember { mutableStateOf(true) }

    if (showAuthPrompt && !isVaultUnlocked) {
        com.example.ui.components.PinLockDialog(
            correctPin = storedPin,
            title = "Security Area Locked",
            subtitle = "Enter 4-digit PIN or use Fingerprint to unlock",
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = !hasCustomPin,
            onDismiss = {
                showAuthPrompt = false
                if (!isVaultUnlocked) onBack()
            },
            onUnlocked = {
                isVaultUnlocked = true
                onVaultUnlockedChange(true)
                showAuthPrompt = false
            },
            onSetupComplete = { pin, question, answer ->
                preferences.setSecurityDetails(pin, question, answer)
                Toast.makeText(context, "Security PIN created successfully!", Toast.LENGTH_SHORT).show()
            },
            onPinReset = { newPin ->
                preferences.setLockPin(newPin)
                isVaultUnlocked = true
                onVaultUnlockedChange(true)
                showAuthPrompt = false
                Toast.makeText(context, "PIN Reset Successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 Hidden Notes, 1 Protected, 2 Hidden Folders
    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)

    GlassBackground(isDarkMode = isDarkMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeuIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            isDarkMode = isDarkMode,
                            size = 38.dp, iconSize = 18.dp,
                            tint = textColor,
                            onClick = onBack
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Security Area", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("PIN & Biometric Protected Vault", fontSize = 11.sp, color = CrimsonPrimary)
                        }
                    }
                    if (isVaultUnlocked) {
                        NeuIconButton(
                            icon = Icons.Default.Settings,
                            contentDescription = "Security settings",
                            isDarkMode = isDarkMode,
                            size = 38.dp, iconSize = 18.dp,
                            tint = CrimsonPrimary,
                            onClick = onOpenSettings
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (!isVaultUnlocked) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, "Locked", tint = CrimsonPrimary, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Security Area is Locked", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(Modifier.height(20.dp))
                            GlassCard(
                                modifier = Modifier.padding(horizontal = 32.dp),
                                shape = RoundedCornerShape(14.dp),
                                isDarkMode = isDarkMode,
                                onClick = { showAuthPrompt = true }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Brush.linearGradient(listOf(CrimsonPrimary, Color(0xFFFF5E7E))))
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Unlock with PIN / Biometrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                }
                            }
                        }
                    }
                } else {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = CrimsonPrimary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = CrimsonPrimary
                            )
                        },
                        divider = {}
                    ) {
                        listOf("Hidden Notes", "Protected", "Hidden Folders").forEachIndexed { i, label ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    when (selectedTab) {
                        0 -> {
                            // Header with "+" — sends the user to the main screen to pick notes
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Hidden Notes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    Text("${hiddenNotes.size} note(s) hidden", fontSize = 11.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CrimsonPrimary)
                                        .clickable { onAddHiddenNotes() },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Add, "Add notes to hidden", tint = Color.White) }
                            }

                            if (hiddenNotes.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painterResource(R.drawable.ic_svg_eye_off), null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text("No hidden notes yet.", color = Color.Gray, fontSize = 14.sp)
                                        Text("Tap + to pick notes from the main screen.", color = CrimsonPrimary, fontSize = 11.5.sp)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(hiddenNotes, key = { it.id }) { note ->
                                        GlassCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            isDarkMode = isDarkMode,
                                            elevation = 3.dp,
                                            onClick = { onOpenNote(note) }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(painterResource(R.drawable.ic_svg_eye_off), null, tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            note.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                                                            color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        com.example.ui.util.ImageMarkers.strip(note.content).take(60), fontSize = 12.sp,
                                                        color = if (isDarkMode) Color.White.copy(0.6f) else Color.DarkGray,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                GlassCard(
                                                    shape = RoundedCornerShape(10.dp),
                                                    isDarkMode = isDarkMode,
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            repository.unhideNote(note)
                                                            Toast.makeText(context, "Note moved back to main screen", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(painterResource(R.drawable.ic_svg_eye), null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Unhide", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Protected folders — same rounded-rectangle look as the main screen chips
                            val allFolders = listOf("All Notes", "Favorites", "APIs Keys", "Code", "Media", "Personal") + customFolders
                            Text("Tap a folder to protect or unprotect it with your PIN.", fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.padding(bottom = 10.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(allFolders) { folder ->
                                    val locked = lockedFolders.contains(folder)
                                    FolderTile(
                                        name = folder,
                                        iconRes = R.drawable.ic_security_lock,
                                        active = locked,
                                        isDarkMode = isDarkMode,
                                        onClick = {
                                            preferences.toggleFolderLock(folder)
                                            Toast.makeText(context, if (!locked) "Protected $folder" else "Unprotected $folder", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hidden Folders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CrimsonPrimary)
                                        .clickable { showHideFolderPicker = true },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Add, "Hide a folder", tint = Color.White) }
                            }
                            if (hiddenFolders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No hidden folders. Tap + to move a folder here.", fontSize = 12.sp, color = Color.Gray)
                                }
                            } else {
                                Text("Tap a folder to bring it back to the main screen.", fontSize = 11.sp, color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 10.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(hiddenFolders.toList()) { folderName ->
                                        FolderTile(
                                            name = folderName,
                                            iconRes = R.drawable.ic_svg_eye_off,
                                            active = true,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                preferences.unhideFolder(folderName)
                                                Toast.makeText(context, "Folder restored to main screen", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            if (showHideFolderPicker) {
                                val hidable = customFolders.filter { it !in hiddenFolders }
                                Dialog(onDismissRequest = { showHideFolderPicker = false }) {
                                    GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp), isDarkMode = isDarkMode, strong = true) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Hide a Folder", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            Spacer(Modifier.height(8.dp))
                                            if (hidable.isEmpty()) {
                                                Text("No custom folders available. Create one from the main screen's \"Add Folder\" first.",
                                                    fontSize = 12.sp, color = Color.Gray)
                                            } else {
                                                hidable.forEach { folderName ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .clickable { preferences.hideFolder(folderName); showHideFolderPicker = false }
                                                            .padding(vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(painterResource(R.drawable.ic_svg_eye_off), null, tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(10.dp))
                                                        Text(folderName, fontSize = 13.sp, color = textColor)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rounded-rectangle folder tile, same visual language as the folder chips on the main screen. */
@Composable
private fun FolderTile(
    name: String,
    iconRes: Int,
    active: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (active) CrimsonPrimary.copy(alpha = 0.16f)
                else (if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000))
            )
            .border(
                1.dp,
                if (active) CrimsonPrimary else (if (isDarkMode) Color(0x26FFFFFF) else Color(0x1F718096)),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (active) CrimsonPrimary else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color(0xFF222222),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (iconRes == R.drawable.ic_security_lock) {
                Text(if (active) "Protected" else "Not protected", fontSize = 10.sp, color = if (active) CrimsonPrimary else Color.Gray)
            }
        }
    }
}
