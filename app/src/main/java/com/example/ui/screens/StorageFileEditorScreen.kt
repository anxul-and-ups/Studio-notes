package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.util.FileManagerStore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import java.io.File
import java.io.FileOutputStream
import java.util.Date

data class RealStorageItem(
    val name: String,
    val file: File? = null,
    val uri: Uri? = null,
    val sizeString: String,
    val path: String,
    val lastModified: Long,
    val isPdf: Boolean = false,
    val isDirectory: Boolean = false
)

@Composable
fun StorageFileEditorScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit = {},
    onOpenFileInEditor: (title: String, content: String, category: String) -> Unit
) {
    val context = LocalContext.current
    var hasUnderstoodIntro by remember {
        val prefs = context.getSharedPreferences("au_file_editor_prefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("intro_understood", false))
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFileItem by remember { mutableStateOf<RealStorageItem?>(null) }
    var fileContentEdit by remember { mutableStateOf("") }
    var isEditingInPlace by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var fileList by remember { mutableStateOf<List<RealStorageItem>>(emptyList()) }
    var showDetailsDialog by remember { mutableStateOf<RealStorageItem?>(null) }

    // ---- file management: folders, move, rename, lock (persisted by FileManagerStore) ----
    val store = remember { FileManagerStore(context) }
    var storeVersion by remember { mutableStateOf(0) } // bump to re-read store-backed state
    var activeFolder by remember { mutableStateOf("All") }
    var menuItemPath by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf<RealStorageItem?>(null) }
    var renameTarget by remember { mutableStateOf<RealStorageItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RealStorageItem?>(null) }

    fun refreshFiles() {
        val list = mutableListOf<RealStorageItem>()
        // App internal docs and storage files
        val internalDocsDir = File(context.filesDir, "documents").apply { mkdirs() }
        internalDocsDir.listFiles()?.forEach { f ->
            list.add(
                RealStorageItem(
                    name = f.name,
                    file = f,
                    sizeString = "${(f.length() / 1024).coerceAtLeast(1)} KB",
                    path = f.absolutePath,
                    lastModified = f.lastModified(),
                    isPdf = f.name.endsWith(".pdf", ignoreCase = true),
                    isDirectory = f.isDirectory
                )
            )
        }

        // Attachments directory
        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        attachmentsDir.listFiles()?.forEach { f ->
            list.add(
                RealStorageItem(
                    name = f.name,
                    file = f,
                    sizeString = "${(f.length() / 1024).coerceAtLeast(1)} KB",
                    path = f.absolutePath,
                    lastModified = f.lastModified(),
                    isPdf = f.name.endsWith(".pdf", ignoreCase = true),
                    isDirectory = f.isDirectory
                )
            )
        }

        // External Downloads directory if accessible
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                downloadsDir.listFiles()?.filter { !it.name.startsWith(".") && (it.extension in listOf("txt", "pdf", "py", "json", "html", "md", "csv", "log") || it.isDirectory) }?.take(25)?.forEach { f ->
                    list.add(
                        RealStorageItem(
                            name = f.name,
                            file = f,
                            sizeString = "${(f.length() / 1024).coerceAtLeast(1)} KB",
                            path = f.absolutePath,
                            lastModified = f.lastModified(),
                            isPdf = f.name.endsWith(".pdf", ignoreCase = true),
                            isDirectory = f.isDirectory
                        )
                    )
                }
            }
        } catch (ignored: Exception) {}

        fileList = list.distinctBy { it.path }.sortedByDescending { it.lastModified }
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var displayName = "Document"
                var sizeBytes = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }

                val isPdf = displayName.endsWith(".pdf", ignoreCase = true)
                if (isPdf) {
                    // Safe import: a byte-for-byte copy lives in app storage, so it can be moved,
                    // locked and re-opened later even after the picker permission is gone.
                    val target = if (activeFolder != "All" && activeFolder != "Unfiled") activeFolder else null
                    val copy = store.importIntoAppStorage(uri, target)
                    if (copy != null) {
                        refreshFiles()
                        storeVersion++
                        Toast.makeText(context, "Imported ${copy.name}", Toast.LENGTH_SHORT).show()
                        onOpenPdf(Uri.fromFile(copy))
                    } else {
                        Toast.makeText(context, "Could not import this PDF", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val item = RealStorageItem(
                        name = displayName,
                        uri = uri,
                        sizeString = "${(sizeBytes / 1024).coerceAtLeast(1)} KB",
                        path = uri.toString(),
                        lastModified = System.currentTimeMillis(),
                        isPdf = false
                    )
                    selectedFileItem = item
                    fileContentEdit = content
                    isEditingInPlace = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val storeFolders = remember(storeVersion) { store.folders() }
    val filteredList = remember(fileList, searchQuery, activeFolder, storeVersion) {
        fileList.filter {
            (it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)) &&
                when (activeFolder) {
                    "All" -> true
                    "Unfiled" -> store.folderOf(it.path) == null
                    else -> store.folderOf(it.path) == activeFolder
                }
        }
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
                    .padding(16.dp)
            ) {
                // Top App Bar
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
                            size = 38.dp,
                            iconSize = 18.dp,
                            tint = if (isDarkMode) Color.White else Color.Black,
                            onClick = onBack
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "File Editor",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color.White else Color(0xFF111111)
                            )
                            Text(
                                text = "Device Storage & File Extractor",
                                fontSize = 11.sp,
                                color = CrimsonPrimary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Import/Extract File",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create New File",
                                tint = if (isDarkMode) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PART B Item 4: Stylish Intro Card (Explaining what File Editor does)
                if (!hasUnderstoodIntro) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        isDarkMode = isDarkMode,
                        elevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_file_editor),
                                    contentDescription = null,
                                    tint = CrimsonPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Welcome to File Editor",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color.White else Color(0xFF111111)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "File Editor allows you to directly explore device files, preview PDFs, edit text & code files (.txt, .py, .html, .json), and extract content seamlessly into AU Notes.",
                                fontSize = 13.sp,
                                color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF444444),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                GlassCard(
                                    shape = RoundedCornerShape(10.dp),
                                    isDarkMode = isDarkMode,
                                    onClick = {
                                        hasUnderstoodIntro = true
                                        context.getSharedPreferences("au_file_editor_prefs", Context.MODE_PRIVATE)
                                            .edit().putBoolean("intro_understood", true).apply()
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Brush.linearGradient(listOf(CrimsonPrimary, Color(0xFFFF5E7E))))
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Understood", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // In-Place Editor Mode
                if (isEditingInPlace && selectedFileItem != null) {
                    val item = selectedFileItem!!
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        isDarkMode = isDarkMode
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = CrimsonPrimary
                                    )
                                    Text(
                                        text = item.sizeString,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                Row {
                                    IconButton(onClick = {
                                        // Save back to file
                                        try {
                                            if (store.isLocked(item.path)) {
                                                Toast.makeText(context, "File is locked — unlock to save changes", Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            if (item.file != null) {
                                                item.file.writeText(fileContentEdit)
                                                Toast.makeText(context, "File Saved!", Toast.LENGTH_SHORT).show()
                                            } else if (item.uri != null) {
                                                context.contentResolver.openOutputStream(item.uri, "wt")?.use { out ->
                                                    out.write(fileContentEdit.toByteArray())
                                                }
                                                Toast.makeText(context, "File Saved to Storage!", Toast.LENGTH_SHORT).show()
                                            }
                                            refreshFiles()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF4CAF50))
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val ext = item.name.substringAfterLast(".", "")
                                            val category = when (ext.lowercase()) {
                                                "py", "kt", "js", "html", "css", "java", "json" -> "Code"
                                                else -> "General"
                                            }
                                            onOpenFileInEditor(item.name, fileContentEdit, category)
                                        }
                                    ) {
                                        Text("Open in Note Editor", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = fileContentEdit,
                                onValueChange = { if (!store.isLocked(item.path)) fileContentEdit = it },
                                readOnly = store.isLocked(item.path),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (isDarkMode) Color.White else Color.Black
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrimsonPrimary,
                                    unfocusedBorderColor = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(onClick = { isEditingInPlace = false }) {
                                Text("Close In-Place Editor", color = CrimsonPrimary)
                            }
                        }
                    }
                } else {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search storage files...", fontSize = 13.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CrimsonPrimary) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // File folders (app-managed). "All" shows everything, "Unfiled" what has no folder.
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (listOf("All", "Unfiled") + storeFolders).forEach { name ->
                            val selected = activeFolder == name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) CrimsonPrimary else (if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000)))
                                    .clickable { activeFolder = name }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else (if (isDarkMode) Color.White else Color(0xFF222222))
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CrimsonPrimary.copy(alpha = 0.15f))
                                .clickable { showNewFolderDialog = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text("+ Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // File List
                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_file_editor),
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No files found", color = Color.Gray, fontSize = 14.sp)
                                Text("Tap '+' to create a new file or folder icon to import.", color = CrimsonPrimary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList) { item ->
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    isDarkMode = isDarkMode,
                                    elevation = 2.dp,
                                    onClick = {
                                        if (item.isPdf) {
                                            if (item.file != null) {
                                                onOpenPdf(Uri.fromFile(item.file))
                                            } else if (item.uri != null) {
                                                onOpenPdf(item.uri)
                                            }
                                        } else if (item.isDirectory) {
                                            Toast.makeText(context, "Folders can't be opened here", Toast.LENGTH_SHORT).show()
                                        } else {
                                            try {
                                                val text = if (item.file != null) {
                                                    item.file.readText()
                                                } else if (item.uri != null) {
                                                    context.contentResolver.openInputStream(item.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                                                } else ""

                                                selectedFileItem = item
                                                fileContentEdit = text
                                                isEditingInPlace = true
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isPdf) Icons.Default.PictureAsPdf else if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (item.isPdf) Color(0xFFFF5252) else if (item.isDirectory) Color(0xFFFFCA28) else CrimsonPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isDarkMode) Color.White else Color(0xFF111111)
                                                )
                                                Text(
                                                    text = "${item.sizeString} • ${Date(item.lastModified).toLocaleString()}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        val locked = remember(storeVersion, item.path) { store.isLocked(item.path) }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (locked) {
                                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                                            }
                                            Box {
                                                IconButton(onClick = { menuItemPath = item.path }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                                }
                                                DropdownMenu(expanded = menuItemPath == item.path, onDismissRequest = { menuItemPath = null }) {
                                                    // Open — every file type
                                                    DropdownMenuItem(
                                                        text = { Text("Open") },
                                                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                                        onClick = {
                                                            menuItemPath = null
                                                            if (item.isPdf) {
                                                                if (item.file != null) onOpenPdf(Uri.fromFile(item.file)) else item.uri?.let(onOpenPdf)
                                                            } else if (!item.isDirectory) {
                                                                try {
                                                                    val text = item.file?.readText()
                                                                        ?: item.uri?.let { u -> context.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } }
                                                                        ?: ""
                                                                    selectedFileItem = item
                                                                    fileContentEdit = text
                                                                    isEditingInPlace = true
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    )
                                                    // Edit — only text-like files that are not locked
                                                    if (!item.isPdf && !item.isDirectory && !locked) {
                                                        DropdownMenuItem(
                                                            text = { Text("Edit") },
                                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                            onClick = {
                                                                menuItemPath = null
                                                                try {
                                                                    val text = item.file?.readText() ?: ""
                                                                    selectedFileItem = item
                                                                    fileContentEdit = text
                                                                    isEditingInPlace = true
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        )
                                                    }
                                                    if (!item.isDirectory) {
                                                        // Move — blocked while locked
                                                        DropdownMenuItem(
                                                            text = { Text(if (locked) "Move (locked)" else "Move to folder") },
                                                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                                            enabled = !locked,
                                                            onClick = { menuItemPath = null; moveTarget = item }
                                                        )
                                                        // Rename — only files inside app storage
                                                        if (item.file != null && store.isAppManaged(item.file)) {
                                                            DropdownMenuItem(
                                                                text = { Text(if (locked) "Rename (locked)" else "Rename") },
                                                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                                                enabled = !locked,
                                                                onClick = { menuItemPath = null; renameInput = item.name; renameTarget = item }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text(if (locked) "Unlock" else "Lock") },
                                                            leadingIcon = { Icon(if (locked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                                                            onClick = {
                                                                menuItemPath = null
                                                                store.setLocked(item.path, !locked)
                                                                storeVersion++
                                                                Toast.makeText(context, if (!locked) "Locked ${item.name}" else "Unlocked ${item.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text("Details") },
                                                        leadingIcon = { Icon(Icons.Default.Info, null) },
                                                        onClick = { menuItemPath = null; showDetailsDialog = item }
                                                    )
                                                    if (item.file != null && !item.isDirectory) {
                                                        DropdownMenuItem(
                                                            text = { Text(if (locked) "Delete (locked)" else "Delete", color = if (locked) Color.Gray else Color.Red) },
                                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = if (locked) Color.Gray else Color.Red) },
                                                            enabled = !locked,
                                                            onClick = { menuItemPath = null; deleteTarget = item }
                                                        )
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

    // ---- Move dialog: destination list of available folders ----
    moveTarget?.let { item ->
        val outside = item.file != null && !store.isAppManaged(item.file)
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move \"${item.name}\"", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (outside || item.file == null) {
                        Text(
                            "This file belongs to other storage, so Android doesn't allow moving it. " +
                                "A copy will be placed in the chosen folder inside AU Notes; the original stays where it is.",
                            fontSize = 12.sp, color = Color.Gray
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val options = listOf<Pair<String, String?>>("Unfiled (no folder)" to null) + storeFolders.map { it to it }
                    options.forEach { (label, target) ->
                        val current = store.folderOf(item.path)
                        TextButton(
                            onClick = {
                                var path = item.path
                                if (outside) {
                                    val copy = item.file?.let { store.importFile(it, target) }
                                    if (copy == null) {
                                        Toast.makeText(context, "Could not copy file", Toast.LENGTH_SHORT).show()
                                        moveTarget = null
                                        return@TextButton
                                    }
                                    path = copy.absolutePath
                                } else if (item.uri != null) {
                                    val copy = store.importIntoAppStorage(item.uri, target)
                                    if (copy == null) {
                                        Toast.makeText(context, "Could not copy file", Toast.LENGTH_SHORT).show()
                                        moveTarget = null
                                        return@TextButton
                                    }
                                    path = copy.absolutePath
                                } else {
                                    store.assign(path, target)
                                }
                                moveTarget = null
                                refreshFiles()
                                storeVersion++
                                Toast.makeText(context, if (target == null) "Moved to Unfiled" else "Moved to $target", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    label,
                                    fontWeight = if (current == target) FontWeight.Bold else FontWeight.Normal,
                                    color = if (current == target) CrimsonPrimary else Color.Unspecified
                                )
                            }
                        }
                    }
                    if (storeFolders.isEmpty()) {
                        Text("No folders yet — close this and tap \"+ Folder\" to create one.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename file", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("File name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    onClick = {
                        val clean = renameInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val f = item.file
                        if (f == null || clean.isEmpty() || store.isLocked(item.path)) {
                            renameTarget = null
                        } else {
                            val dest = File(f.parentFile, clean)
                            if (dest.exists()) {
                                Toast.makeText(context, "A file with that name already exists", Toast.LENGTH_SHORT).show()
                            } else if (f.renameTo(dest)) {
                                store.onRenamed(f.absolutePath, dest.absolutePath)
                                renameTarget = null
                                refreshFiles()
                                storeVersion++
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false; newFolderInput = "" },
            title = { Text("New file folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderInput,
                    onValueChange = { newFolderInput = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    onClick = {
                        if (store.createFolder(newFolderInput)) {
                            activeFolder = newFolderInput.trim()
                            storeVersion++
                            showNewFolderDialog = false
                            newFolderInput = ""
                        } else {
                            Toast.makeText(context, "Enter a new, unique folder name", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false; newFolderInput = "" }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete file?") },
            text = { Text("\"${item.name}\" will be deleted permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!store.isLocked(item.path)) {
                        item.file?.delete()
                        store.onDeleted(item.path)
                        refreshFiles()
                        storeVersion++
                        Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                    deleteTarget = null
                }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // Create File Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create File in Storage", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Filename (e.g. script.py, notes.txt)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
                            val newFile = File(docsDir, newFileName.trim())
                            newFile.writeText("")
                            showCreateDialog = false
                            newFileName = ""
                            refreshFiles()
                            Toast.makeText(context, "Created ${newFile.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Details Dialog
    showDetailsDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDetailsDialog = null },
            title = { Text("File Details", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Name: ${item.name}", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Path: ${item.path}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Size: ${item.sizeString}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Modified: ${Date(item.lastModified).toLocaleString()}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Folder: ${store.folderOf(item.path) ?: "Unfiled"}", fontSize = 12.sp)
                    Text("Status: ${if (store.isLocked(item.path)) "Locked" else "Unlocked"}", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = null }) { Text("Close") }
            }
        )
    }
}
