package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AutoClassifier
import com.example.data.model.NoteEntity
import com.example.data.preferences.AppPreferences
import com.example.ui.components.ExportDialog
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.InteractiveChecklistView
import com.example.ui.components.NeuIconButton
import com.example.ui.components.PinLockDialog
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.util.AttachmentStorage
import com.example.ui.util.AlarmScheduler
import com.example.ui.util.RichTextFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReadNoteScreen(
    note: NoteEntity,
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onToggleLock: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showExportDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var revealApi by remember { mutableStateOf(false) }
    var pdfAttachmentToView by remember { mutableStateOf<Uri?>(null) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    val noteAlarms by preferences.noteAlarms.collectAsState()
    val activeAlarm = noteAlarms[note.id]

    val allAttachments = remember(note.attachmentsJson) {
        RichTextFormatter.deserializeAttachments(note.attachmentsJson)
    }
    // Inline images are drawn inside the text (at their "[img:id]" line); only the rest is a gallery.
    val attachments = remember(allAttachments) { allAttachments.filter { it.id.isBlank() } }

    val spans = remember(note.styleSpansJson) {
        RichTextFormatter.deserializeSpans(note.styleSpansJson)
    }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    }

    GlassBackground(isDarkMode = isDarkMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Compact header: back | title + category/date | alarm | edit.
                // (The old separate title card used a full extra row of screen height.)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        isDarkMode = isDarkMode,
                        size = 38.dp,
                        iconSize = 18.dp,
                        tint = if (isDarkMode) Color.White else Color.Black,
                        onClick = onBack
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title.ifBlank { "Untitled Note" },
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkMode) Color.White else Color(0xFF111111),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.category, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                            Text(
                                text = "  •  " + dateFormatter.format(Date(note.updatedAt)),
                                fontSize = 10.sp,
                                color = if (isDarkMode) Color.White.copy(.55f) else Color.Gray,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Alarm / Reminder
                    NeuIconButton(
                        icon = Icons.Default.Alarm,
                        contentDescription = "Set Alarm Reminder",
                        isDarkMode = isDarkMode,
                        size = 38.dp,
                        iconSize = 18.dp,
                        tint = if (activeAlarm != null) CrimsonPrimary else (if (isDarkMode) Color.White else Color.Black),
                        onClick = { showAlarmDialog = true }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Edit button
                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        isDarkMode = isDarkMode,
                        onClick = { onEditNote(note) }
                    ) {
                        Row(
                            modifier = Modifier
                                .background(CrimsonPrimary)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit_note),
                                contentDescription = "Edit Note",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Edit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Only the note body scrolls; the navigation and title bars remain pinned.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                // Active Alarm Details — Item 17: note name, time, countdown,
                // ringtone, status, and an edit (pencil) icon to reopen the dialog.
                if (activeAlarm != null) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        isDarkMode = isDarkMode,
                        elevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(activeAlarm.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val remainingMs = activeAlarm.triggerMillis - System.currentTimeMillis()
                                val remainingText = if (remainingMs <= 0) "Due now" else {
                                    val totalMinutes = remainingMs / 60000
                                    val h = totalMinutes / 60
                                    val m = totalMinutes % 60
                                    if (h > 0) "in ${h}h ${m}m" else "in ${m}m"
                                }
                                val timeCal = java.util.Calendar.getInstance().apply { timeInMillis = activeAlarm.triggerMillis }
                                Text(
                                    text = "%02d:%02d %s • %s".format(
                                        timeCal.get(java.util.Calendar.HOUR_OF_DAY),
                                        timeCal.get(java.util.Calendar.MINUTE),
                                        remainingText,
                                        activeAlarm.ringtoneName
                                    ),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Status: Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            IconButton(onClick = { showAlarmDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Alarm", tint = CrimsonPrimary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Read-mode content uses the available screen area directly; keep the action bar intact.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Action Toolbar Card (Lock | Export | Delete | TTS | Copy)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            isDarkMode = isDarkMode,
                            isInset = true,
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Lock Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            if (note.isLocked) {
                                                showPinDialog = true
                                            } else {
                                                onToggleLock(note.id, false)
                                                Toast.makeText(context, "Note Locked", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(if (note.isLocked) R.drawable.ic_security_lock else R.drawable.ic_security_unlock),
                                        contentDescription = "Lock",
                                        tint = if (note.isLocked) CrimsonPrimary else if (isDarkMode) Color.White.copy(0.7f) else Color.DarkGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (note.isLocked) "Locked" else "Lock",
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color.White.copy(0.85f) else Color.Black
                                    )
                                }

                                Text("|", color = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000), fontSize = 12.sp)

                                // Export Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showExportDialog = true }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_export_download),
                                        contentDescription = "Export",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Export",
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color.White.copy(0.85f) else Color.Black
                                    )
                                }

                                Text("|", color = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000), fontSize = 12.sp)

                                // Delete Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            onDeleteNote(note.id)
                                            Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_recycle_bin),
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF5252)
                                    )
                                }

                                Text("|", color = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000), fontSize = 12.sp)

                                // TTS Button (Speaker)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            if (isSpeaking) {
                                                tts?.stop()
                                                isSpeaking = false
                                            } else {
                                                val toSpeak = "${note.title}. ${com.example.ui.util.ImageMarkers.strip(note.content)}"
                                                tts?.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "NoteTTS")
                                                isSpeaking = true
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(if (isSpeaking) R.drawable.ic_mute_tts else R.drawable.ic_speaker_tts),
                                        contentDescription = "TTS",
                                        tint = Color(0xFF2CF95F),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSpeaking) "Stop" else "Listen",
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color.White.copy(0.85f) else Color.Black
                                    )
                                }

                                Text("|", color = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000), fontSize = 12.sp)

                                // Copy Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            clipboard.setText(AnnotatedString(com.example.ui.util.ImageMarkers.strip(note.content)))
                                            Toast.makeText(context, "Note copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_copy_content),
                                        contentDescription = "Copy",
                                        tint = Color(0xFFFFD93D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color.White.copy(0.85f) else Color.Black
                                    )
                                }
                            }
                        }

                        // Scroll the note body independently so the title and action toolbar remain fixed.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 10.dp)
                        ) {
                        // Render Attached Images and Files in Read Mode (PART F Item 6 Fix)
                        if (attachments.isNotEmpty()) {
                            Text(
                                text = "Attached Media & Files (${attachments.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                attachments.forEach { attachment ->
                                    val isImage = attachment.mimeType.startsWith("image")
                                    if (isImage) {
                                        // Real Image Rendering in Read Mode
                                        GlassCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            isDarkMode = isDarkMode,
                                            elevation = 2.dp
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                com.example.ui.components.AttachmentFrame(
                                                    att = attachment,
                                                    modifier = Modifier.fillMaxWidth(attachment.widthFraction),
                                                    cornerRadius = 8
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = attachment.fileName,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isDarkMode) Color.White.copy(0.7f) else Color.DarkGray
                                                )
                                            }
                                        }
                                    } else {
                                        ReadAttachmentCard(
                                            attachment = attachment,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                try {
                                                    val shareUri = AttachmentStorage.getShareableUri(context, attachment.uri)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(shareUri, attachment.mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open attachment", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Content Area with DAY MODE CONTRAST FIX
                        val displayContent = if (note.category == "API" && preferences.blurApis.value && !revealApi) {
                            AutoClassifier.maskApiKey(note.content)
                        } else {
                            note.content
                        }

                        // Day mode contrast fix: Ensure text is dark and readable
                        val currentFontColor = if (!isDarkMode && (note.fontColorHex.equals("#FFFFFF", ignoreCase = true) || note.fontColorHex.equals("#FFF", ignoreCase = true))) {
                            Color(0xFF111111)
                        } else {
                            try {
                                Color(android.graphics.Color.parseColor(note.fontColorHex))
                            } catch (e: Exception) {
                                if (isDarkMode) Color.White.copy(0.92f) else Color(0xFF111111)
                            }
                        }

                        if (note.category == "Code" || note.isCodeFormat) {
                            // Syntax colours are computed in the background so big code never freezes the screen.
                            val coloredCode by androidx.compose.runtime.produceState(
                                initialValue = AnnotatedString(displayContent),
                                displayContent
                            ) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    com.example.ui.util.CodeHighlight.highlight(displayContent, isDarkMode)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = coloredCode,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = com.example.ui.util.CodeHighlight.baseColor(isDarkMode),
                                    lineHeight = 19.sp
                                )
                            }
                        } else {
                            val styledText = remember(displayContent, spans, currentFontColor, note.fontSize) {
                                if (spans.isNotEmpty()) {
                                    RichTextFormatter.buildStyledText(
                                        text = displayContent,
                                        spans = spans,
                                        defaultColor = currentFontColor,
                                        fontSize = note.fontSize.toFloat()
                                    )
                                } else {
                                    AnnotatedString(displayContent)
                                }
                            }

                            val hasRichLines = remember(displayContent) {
                                displayContent.contains("[img:") ||
                                    Regex("(?m)^[ \\t]*\\[( |x|!)] ").containsMatchIn(displayContent)
                            }
                            if (!hasRichLines) {
                                Text(
                                    text = styledText,
                                    fontSize = note.fontSize.sp,
                                    color = currentFontColor,
                                    lineHeight = (note.fontSize + 6).sp
                                )
                            } else {
                                ReadRichBody(
                                    content = displayContent,
                                    styled = styledText,
                                    attachments = allAttachments,
                                    fontSize = note.fontSize,
                                    color = currentFontColor,
                                    isDarkMode = isDarkMode
                                )
                            }
                        }

                        // Render Table if table data exists
                        if (note.tableData.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Embedded Table:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderTable(note.tableData, isDarkMode)
                        }
                        }
                    }
                }
            }
        }
    }
    }

    if (showExportDialog) {
        ExportDialog(
            note = note,
            isDarkMode = isDarkMode,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showPinDialog) {
        val storedPin by preferences.lockPin.collectAsState()
        PinLockDialog(
            correctPin = storedPin,
            isDarkMode = isDarkMode,
            onDismiss = { showPinDialog = false },
            onUnlocked = {
                showPinDialog = false
                onToggleLock(note.id, true)
                Toast.makeText(context, "Note Unlocked", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Alarm / Reminder dialog — Item 14 (moved here from Write Mode), with
    // Item 14's native time picker and Item 15's real ringtone/device-file
    // selection lists (instead of always defaulting to the first item).
    if (showAlarmDialog) {
        var alarmTitleInput by remember {
            mutableStateOf(activeAlarm?.title ?: if (note.title.isNotBlank()) "Reminder: ${note.title}" else "Note Reminder")
        }
        val initialCal = remember {
            Calendar.getInstance().apply {
                if (activeAlarm != null) {
                    timeInMillis = activeAlarm.triggerMillis
                } else {
                    add(Calendar.MINUTE, 5)
                }
            }
        }
        var selectedHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
        var selectedMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
        var ringtoneType by remember { mutableStateOf("system") }
        var showRingtoneList by remember { mutableStateOf(false) }

        val systemRingtones = remember { AlarmScheduler.getSystemRingtones(context) }
        var selectedSystemRingtone by remember { mutableStateOf(systemRingtones.firstOrNull()) }

        val deviceMusicFiles = remember { AlarmScheduler.getDeviceMusicFiles(context) }
        var selectedDeviceMusic by remember { mutableStateOf<com.example.ui.util.DeviceAudioFile?>(null) }

        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = CrimsonPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (activeAlarm != null) "Edit Alarm" else "Set Alarm", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = alarmTitleInput,
                        onValueChange = { alarmTitleInput = it },
                        label = { Text("Alarm Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Item 14: proper native time picker instead of +1hr/+10min buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        selectedHour = hour
                                        selectedMinute = minute
                                    },
                                    selectedHour,
                                    selectedMinute,
                                    true
                                ).show()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Alarm Time", fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)
                        Text(
                            "%02d:%02d".format(selectedHour, selectedMinute),
                            fontWeight = FontWeight.Bold,
                            color = CrimsonPrimary,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Ringtone Source", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = ringtoneType == "system",
                            onClick = { ringtoneType = "system"; showRingtoneList = true },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary)
                        )
                        Text("System Ringtones", fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)

                        Spacer(modifier = Modifier.width(12.dp))

                        RadioButton(
                            selected = ringtoneType == "device",
                            onClick = { ringtoneType = "device"; showRingtoneList = true },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary)
                        )
                        Text("Device Files", fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)
                    }

                    // Item 15: real tap-to-open list of the actual available files,
                    // not just a static "first found" label.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRingtoneList = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ringtoneType == "system")
                                (selectedSystemRingtone?.title ?: "Choose a ringtone")
                            else
                                (selectedDeviceMusic?.title ?: "Choose a music file"),
                            fontSize = 12.sp,
                            color = CrimsonPrimary
                        )
                        Text("Change", fontSize = 11.sp, color = Color.Gray)
                    }

                    if (showRingtoneList) {
                        val list: List<Pair<String, () -> Unit>> = if (ringtoneType == "system") {
                            systemRingtones.map { item ->
                                item.title to { selectedSystemRingtone = item; showRingtoneList = false }
                            }
                        } else {
                            if (deviceMusicFiles.isEmpty()) {
                                listOf("No audio files found on this device" to { showRingtoneList = false })
                            } else {
                                deviceMusicFiles.map { item ->
                                    "${item.title} — ${item.artist}" to { selectedDeviceMusic = item; showRingtoneList = false }
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            items(list) { (label, onPick) ->
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isDarkMode) Color.White.copy(0.85f) else Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick() }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }

                        // Replace any previous alarm for this note first
                        activeAlarm?.let { AlarmScheduler.cancelAlarm(context, it.triggerMillis, note.id) }

                        val chosenUri = if (ringtoneType == "device") selectedDeviceMusic?.uri else selectedSystemRingtone?.uri
                        val chosenName = if (ringtoneType == "device") (selectedDeviceMusic?.title ?: "Default") else (selectedSystemRingtone?.title ?: "Default")
                        val scheduled = AlarmScheduler.scheduleAlarm(
                            context = context,
                            triggerTimeMillis = cal.timeInMillis,
                            title = alarmTitleInput,
                            noteId = note.id,
                            ringtoneUri = chosenUri
                        )

                        if (scheduled) {
                            preferences.setNoteAlarm(
                                noteId = note.id,
                                triggerMillis = cal.timeInMillis,
                                title = alarmTitleInput,
                                ringtoneUri = chosenUri?.toString() ?: "",
                                ringtoneName = chosenName
                            )
                            Toast.makeText(context, "Alarm set for %02d:%02d".format(selectedHour, selectedMinute), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Could not schedule alarm", Toast.LENGTH_SHORT).show()
                        }
                        showAlarmDialog = false
                    }
                ) {
                    Text("Save Alarm")
                }
            },
            dismissButton = {
                Row {
                    if (activeAlarm != null) {
                        TextButton(onClick = {
                            AlarmScheduler.cancelAlarm(context, activeAlarm.triggerMillis, note.id)
                            preferences.clearNoteAlarm(note.id)
                            showAlarmDialog = false
                            Toast.makeText(context, "Alarm removed", Toast.LENGTH_SHORT).show()
                        }) { Text("Remove", color = Color(0xFFFF5252)) }
                    }
                    TextButton(onClick = { showAlarmDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun ReadAttachmentCard(
    attachment: RichTextFormatter.AttachmentInfo,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        isDarkMode = isDarkMode,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CrimsonPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (attachment.mimeType == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkMode) Color.White else Color.Black,
                    maxLines = 1
                )
                Text(
                    text = "${(attachment.sizeBytes / 1024).coerceAtLeast(1)} KB • Tap to open",
                    fontSize = 11.sp,
                    color = if (isDarkMode) Color.White.copy(0.5f) else Color.Gray
                )
            }
        }
    }
}

@Composable
fun RenderTable(tableData: String, isDarkMode: Boolean) {
    val rows = androidx.compose.runtime.remember(tableData) { com.example.ui.util.TableFormat.parse(tableData) }
    if (rows.isEmpty()) return
    val borderColor = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Column {
            rows.forEachIndexed { rowIndex, cells ->
                Row(
                    modifier = Modifier
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                        .background(
                            if (rowIndex == 0) {
                                if (isDarkMode) Color(0x33FF2D55) else Color(0x14FF2D55)
                            } else if (rowIndex % 2 == 0) {
                                if (isDarkMode) Color(0x14FFFFFF) else Color(0x0A000000)
                            } else {
                                Color.Transparent
                            }
                        )
                ) {
                    cells.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cell,
                                fontSize = 12.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDarkMode) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Read-mode body for notes containing checkbox lines or inline images. Plain lines stay grouped in
 * one Text (keeping all styling); checkbox lines get a real box; "[img:id]" lines draw the picture
 * with its saved size / crop / rotation / zoom.
 */
@Composable
private fun ReadRichBody(
    content: String,
    styled: AnnotatedString,
    attachments: List<RichTextFormatter.AttachmentInfo>,
    fontSize: Int,
    color: Color,
    isDarkMode: Boolean
) {
    val checkRegex = remember { Regex("^([ \\t]*)(\\[ ]|\\[x]|\\[!]) (.*)$") }
    val lines = content.split("\n")
    // absolute start offset of every line inside [content]
    val starts = IntArray(lines.size)
    var acc = 0
    lines.forEachIndexed { i, l -> starts[i] = acc; acc += l.length + 1 }

    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        while (i < lines.size) {
            val imgId = com.example.ui.util.ImageMarkers.idOf(lines[i])
            val check = checkRegex.matchEntire(lines[i])
            when {
                imgId != null -> {
                    val att = attachments.firstOrNull { it.id == imgId }
                    if (att != null) {
                        Spacer(Modifier.height(6.dp))
                        com.example.ui.components.AttachmentFrame(
                            att = att,
                            modifier = Modifier.fillMaxWidth(att.widthFraction)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    i++
                }
                check != null -> {
                    val state = check.groupValues[2]
                    val bodyStart = starts[i] + check.groupValues[1].length + 4
                    val bodyEnd = starts[i] + lines[i].length
                    val tint = when (state) {
                        "[x]" -> Color(0xFF35B65B)
                        "[!]" -> Color(0xFFE05252)
                        else -> if (isDarkMode) Color.White.copy(.75f) else Color(0xFF555555)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (state == "[ ]") Color.Transparent else tint.copy(alpha = .18f))
                                .border(1.6.dp, tint, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (state) {
                                "[x]" -> Icon(Icons.Default.Check, null, tint = tint, modifier = Modifier.size(14.dp))
                                "[!]" -> Icon(Icons.Default.Close, null, tint = tint, modifier = Modifier.size(14.dp))
                                else -> Unit
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (bodyStart <= bodyEnd && bodyEnd <= styled.length) styled.subSequence(bodyStart, bodyEnd) else AnnotatedString(check.groupValues[3]),
                            fontSize = fontSize.sp,
                            color = if (state == "[x]") Color.Gray else color,
                            textDecoration = if (state == "[x]") androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            lineHeight = (fontSize + 6).sp
                        )
                    }
                    i++
                }
                else -> {
                    // group consecutive plain lines into one Text so styling/wrapping stay natural
                    var j = i
                    while (j < lines.size &&
                        com.example.ui.util.ImageMarkers.idOf(lines[j]) == null &&
                        !checkRegex.matches(lines[j])
                    ) j++
                    val from = starts[i]
                    val to = (starts[j - 1] + lines[j - 1].length).coerceAtMost(styled.length)
                    if (from <= to) {
                        Text(
                            text = styled.subSequence(from, to),
                            fontSize = fontSize.sp,
                            color = color,
                            lineHeight = (fontSize + 6).sp
                        )
                    }
                    i = j
                }
            }
        }
    }
}
