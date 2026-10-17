package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ApiConnectivityChecker
import com.example.data.ai.ConnectivityResult
import com.example.data.preferences.AppPreferences
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.components.PinLockDialog
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.launch

private val BUILT_IN_PROVIDERS = listOf("Gemini", "OpenAI", "Anthropic", "DeepSeek", "Kimi", "OpenCode", "Hugging Face")

@Composable
fun ApiRoomScreen(
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val apiRoomLocked by preferences.apiRoomLocked.collectAsState()
    val hasCustomPin by preferences.hasCustomPin.collectAsState()
    val storedPin by preferences.lockPin.collectAsState()
    val secQuestion by preferences.securityQuestion.collectAsState()
    val secAnswer by preferences.securityAnswer.collectAsState()

    var isUnlocked by remember { mutableStateOf(!apiRoomLocked) }
    var showAuthPrompt by remember { mutableStateOf(apiRoomLocked) }

    val useInbuiltApi by preferences.useInbuiltApi.collectAsState()
    val userApiKey by preferences.userApiKey.collectAsState()
    val selectedModel by preferences.selectedModel.collectAsState()
    val providerEntries by preferences.apiProviderEntries.collectAsState()
    val detectedModels by preferences.detectedModels.collectAsState()

    // Item 27: nothing below (keys, providers, models) is even composed
    // until the PIN is verified, so it can't leak while the dialog is up.
    if (showAuthPrompt && !isUnlocked) {
        GlassBackground(isDarkMode = isDarkMode) { Box(modifier = Modifier.fillMaxSize()) }
        PinLockDialog(
            correctPin = storedPin,
            title = "API Room Locked",
            subtitle = "Enter your PIN to view API keys and providers",
            securityQuestion = secQuestion,
            securityAnswer = secAnswer,
            isDarkMode = isDarkMode,
            requireSetup = !hasCustomPin,
            onDismiss = { onBack() },
            onUnlocked = { isUnlocked = true; showAuthPrompt = false },
            onSetupComplete = { pin, question, answer -> preferences.setSecurityDetails(pin, question, answer) },
            onPinReset = { newPin -> preferences.setLockPin(newPin) }
        )
        return
    }

    var expandedProvider by remember { mutableStateOf<String?>(null) }
    var showAddProviderDialog by remember { mutableStateOf(false) }

    GlassBackground(isDarkMode = isDarkMode) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cable, contentDescription = null, tint = CrimsonPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("API Room", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                }
                // Item 27: lock toggle for the whole screen
                IconButton(onClick = {
                    val newLocked = !apiRoomLocked
                    preferences.setApiRoomLocked(newLocked)
                    Toast.makeText(context, if (newLocked) "API Room locked" else "API Room unlocked", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = if (apiRoomLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Toggle lock",
                        tint = CrimsonPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Gemini card — reuses the existing working prefs so nothing
                // that already calls Gemini elsewhere in the app breaks.
                item {
                    GeminiProviderCard(
                        isDarkMode = isDarkMode,
                        expanded = expandedProvider == "Gemini",
                        onToggleExpand = { expandedProvider = if (expandedProvider == "Gemini") null else "Gemini" },
                        useInbuiltApi = useInbuiltApi,
                        userApiKey = userApiKey,
                        selectedModel = selectedModel,
                        effectiveKey = preferences.getEffectiveApiKey(),
                        detectedModels = detectedModels["Gemini"] ?: emptyList(),
                        onModelsDetected = { preferences.setDetectedModels("Gemini", it) },
                        onSetUseInbuilt = preferences::setUseInbuiltApi,
                        onSetApiKey = preferences::setUserApiKey,
                        onSetModel = preferences::setSelectedModel
                    )
                }

                items(BUILT_IN_PROVIDERS.filter { it != "Gemini" }) { providerName ->
                    val saved = providerEntries[providerName]
                    ProviderCard(
                        name = providerName,
                        isDarkMode = isDarkMode,
                        expanded = expandedProvider == providerName,
                        onToggleExpand = { expandedProvider = if (expandedProvider == providerName) null else providerName },
                        savedKey = saved?.apiKey ?: "",
                        savedBaseUrl = saved?.baseUrl ?: "",
                        savedModel = saved?.model ?: "",
                        detectedModels = detectedModels[providerName] ?: emptyList(),
                        onModelsDetected = { preferences.setDetectedModels(providerName, it) },
                        onSelectModel = { m -> preferences.saveApiProvider(providerName, saved?.apiKey ?: "", saved?.baseUrl ?: "", m) },
                        needsBaseUrl = providerName == "OpenCode",
                        onSave = { key, baseUrl -> preferences.saveApiProvider(providerName, key, baseUrl) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddProviderDialog = true }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Provider", color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (providerEntries.values.any { it.isCustom }) {
                    item {
                        Text("Custom Providers", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
                items(providerEntries.values.filter { it.isCustom }.toList()) { entry ->
                    ProviderCard(
                        name = entry.name,
                        isDarkMode = isDarkMode,
                        expanded = expandedProvider == entry.name,
                        onToggleExpand = { expandedProvider = if (expandedProvider == entry.name) null else entry.name },
                        savedKey = entry.apiKey,
                        savedBaseUrl = entry.baseUrl,
                        savedModel = entry.model,
                        detectedModels = detectedModels[entry.name] ?: emptyList(),
                        onModelsDetected = { preferences.setDetectedModels(entry.name, it) },
                        onSelectModel = { m -> preferences.saveApiProvider(entry.name, entry.apiKey, entry.baseUrl, m, isCustom = true) },
                        needsBaseUrl = true,
                        onSave = { key, baseUrl -> preferences.saveApiProvider(entry.name, key, baseUrl, isCustom = true) },
                        onDelete = { preferences.deleteApiProvider(entry.name) }
                    )
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }

    if (showAddProviderDialog) {
        var newProviderName by remember { mutableStateOf("") }
        var newProviderKey by remember { mutableStateOf("") }
        var newProviderUrl by remember { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddProviderDialog = false }) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp), isDarkMode = isDarkMode, strong = true) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Add Custom Provider", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newProviderName,
                        onValueChange = { newProviderName = it },
                        label = { Text("Provider name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProviderUrl,
                        onValueChange = { newProviderUrl = it },
                        label = { Text("Base URL / endpoint") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProviderKey,
                        onValueChange = { newProviderKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddProviderDialog = false }) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (newProviderName.isBlank()) {
                                    Toast.makeText(context, "Enter a provider name", Toast.LENGTH_SHORT).show()
                                } else {
                                    preferences.saveApiProvider(newProviderName.trim(), newProviderKey, newProviderUrl, isCustom = true)
                                    showAddProviderDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiProviderCard(
    isDarkMode: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    useInbuiltApi: Boolean,
    userApiKey: String,
    selectedModel: String,
    effectiveKey: String,
    detectedModels: List<String>,
    onModelsDetected: (List<String>) -> Unit,
    onSetUseInbuilt: (Boolean) -> Unit,
    onSetApiKey: (String) -> Unit,
    onSetModel: (String) -> Unit
) {
    var keyInput by remember(userApiKey) { mutableStateOf(userApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var detectorResult by remember { mutableStateOf<ConnectivityResult?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), isDarkMode = isDarkMode, elevation = 2.dp) {
        Column(modifier = Modifier.padding(14.dp)) {
            ProviderHeader("Gemini", "Google AI Studio", isDarkMode, expanded, onToggleExpand)
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetUseInbuilt(true) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = useInbuiltApi, onClick = { onSetUseInbuilt(true) }, colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary))
                    Text("Inbuilt API (Auto-Configured)", fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetUseInbuilt(false) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !useInbuiltApi, onClick = { onSetUseInbuilt(false) }, colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary))
                    Text("Use your own API key", fontSize = 13.sp, color = if (isDarkMode) Color.White else Color.Black)
                }
                if (!useInbuiltApi) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it; onSetApiKey(it) },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ModelDetectorRow(
                    isDetecting = isDetecting,
                    result = detectorResult,
                    models = detectedModels.ifEmpty { listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro") },
                    isRealList = detectedModels.isNotEmpty(),
                    selectedModel = selectedModel,
                    onSelectModel = onSetModel,
                    isDarkMode = isDarkMode,
                    onDetect = {
                        isDetecting = true
                        coroutineScope.launch {
                            val r = ApiConnectivityChecker.check("Gemini", effectiveKey)
                            detectorResult = r
                            if (r.isActive && r.availableModels.isNotEmpty()) onModelsDetected(r.availableModels)
                            isDetecting = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    name: String,
    isDarkMode: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    savedKey: String,
    savedBaseUrl: String,
    savedModel: String,
    detectedModels: List<String>,
    onModelsDetected: (List<String>) -> Unit,
    onSelectModel: (String) -> Unit,
    needsBaseUrl: Boolean,
    onSave: (key: String, baseUrl: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var urlInput by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var keyVisible by remember { mutableStateOf(false) }
    var detectorResult by remember { mutableStateOf<ConnectivityResult?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), isDarkMode = isDarkMode, elevation = 2.dp) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ProviderHeader(name, if (savedKey.isNotBlank()) "Configured" else "Not configured", isDarkMode, expanded, onToggleExpand, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete provider", tint = Color(0xFFFF5252))
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (needsBaseUrl) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it; onSave(keyInput, it) },
                        label = { Text("Base URL / endpoint") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; onSave(it, urlInput) },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
                ModelDetectorRow(
                    isDetecting = isDetecting,
                    result = detectorResult,
                    models = detectedModels,
                    isRealList = detectedModels.isNotEmpty(),
                    selectedModel = savedModel,
                    onSelectModel = onSelectModel,
                    isDarkMode = isDarkMode,
                    onDetect = {
                        isDetecting = true
                        coroutineScope.launch {
                            val r = ApiConnectivityChecker.check(name, keyInput, urlInput)
                            detectorResult = r
                            if (r.isActive && r.availableModels.isNotEmpty()) onModelsDetected(r.availableModels)
                            isDetecting = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderHeader(
    name: String,
    subtitle: String,
    isDarkMode: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = CrimsonPrimary
        )
    }
}

@Composable
private fun ModelDetectorRow(
    isDetecting: Boolean,
    result: ConnectivityResult?,
    models: List<String>,
    isRealList: Boolean,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    isDarkMode: Boolean,
    onDetect: () -> Unit
) {
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onDetect,
        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
        modifier = Modifier.fillMaxWidth(),
        enabled = !isDetecting
    ) {
        if (isDetecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text("Model Detector")
        }
    }
    result?.let { r ->
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (r.isActive) Color(0x1F4CAF50) else Color(0x1FFF5252),
                    RoundedCornerShape(10.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = if (r.isActive) "● ACTIVE" else "● INACTIVE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (r.isActive) Color(0xFF4CAF50) else Color(0xFFFF5252)
            )
            Text(r.statusMessage, fontSize = 11.sp, color = Color.Gray)
            if (r.isActive) {
                Text("${r.availableModels.size} models found", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
    if (models.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isRealList) "Detected models — tap to select" else "Default models (run Model Detector for your real list)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDarkMode) Color.White else Color.Black
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            models.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectModel(m) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModel == m,
                        onClick = { onSelectModel(m) },
                        colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary)
                    )
                    Text(m, fontSize = 12.sp, color = if (isDarkMode) Color.White else Color.Black)
                }
            }
        }
    }
}
