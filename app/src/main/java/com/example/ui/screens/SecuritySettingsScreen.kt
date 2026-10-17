package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.preferences.AppPreferences
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary

private enum class SecDialog { NONE, QUESTION, PIN, AUTOLOCK }

/** Everything that used to be scattered in the Security Area / Settings lives here. */
@Composable
fun SecuritySettingsScreen(
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storedPin by preferences.lockPin.collectAsState()
    val question by preferences.securityQuestion.collectAsState()
    val autoLockMode by preferences.securityAutoLockMode.collectAsState()
    val verifyOnDelete by preferences.verifyPinOnDelete.collectAsState()
    var dialog by remember { mutableStateOf(SecDialog.NONE) }

    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)

    GlassBackground(isDarkMode = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    Text("Security Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("Vault, PIN & deletion rules", fontSize = 11.sp, color = CrimsonPrimary)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("CREDENTIALS")
            SettingRect(Icons.Default.Pin, "Change PIN", "Set a new 4-digit PIN", isDarkMode) { dialog = SecDialog.PIN }
            Spacer(Modifier.height(10.dp))
            SettingRect(Icons.Default.QuestionAnswer, "Change security question", question, isDarkMode) { dialog = SecDialog.QUESTION }

            Spacer(Modifier.height(20.dp))
            SectionLabel("LOCKING")
            SettingRect(
                Icons.Default.Timer, "Lock immediately / auto lock",
                when (autoLockMode) {
                    AppPreferences.AUTO_LOCK_ON_LEAVE_AREA -> "When leaving Security Area"
                    AppPreferences.AUTO_LOCK_ON_APP_CLOSE -> "When app is closed"
                    AppPreferences.AUTO_LOCK_ON_SCREEN_OFF -> "When screen is locked / turned off"
                    else -> "Immediately"
                },
                isDarkMode
            ) { dialog = SecDialog.AUTOLOCK }

            Spacer(Modifier.height(20.dp))
            SectionLabel("DELETION")
            SettingRect(
                Icons.Default.DeleteSweep, "Verify PIN while deleting",
                if (verifyOnDelete) "ON — PIN asked before deleting any note or folder" else "OFF — items delete without a PIN",
                isDarkMode,
                trailing = {
                    Switch(
                        checked = verifyOnDelete,
                        onCheckedChange = { preferences.setVerifyPinOnDelete(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                    )
                }
            ) { preferences.setVerifyPinOnDelete(!verifyOnDelete) }

            Spacer(Modifier.height(30.dp))
        }
    }

    when (dialog) {
        SecDialog.PIN -> ChangeDialog(isDarkMode, "Change PIN", onDismiss = { dialog = SecDialog.NONE }) { close ->
            var cur by remember { mutableStateOf("") }
            var new1 by remember { mutableStateOf("") }
            var new2 by remember { mutableStateOf("") }
            PinField("Current PIN", cur) { cur = it }
            PinField("New 4-digit PIN", new1) { new1 = it }
            PinField("Confirm new PIN", new2) { new2 = it }
            ConfirmRow(onCancel = close) {
                when {
                    cur != storedPin -> Toast.makeText(context, "Current PIN is wrong", Toast.LENGTH_SHORT).show()
                    new1.length != 4 -> Toast.makeText(context, "New PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                    new1 != new2 -> Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                    else -> {
                        preferences.setLockPin(new1)
                        Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
                        close()
                    }
                }
            }
        }

        SecDialog.QUESTION -> ChangeDialog(isDarkMode, "Change security question", onDismiss = { dialog = SecDialog.NONE }) { close ->
            var cur by remember { mutableStateOf("") }
            var q by remember { mutableStateOf(question) }
            var a by remember { mutableStateOf("") }
            PinField("Current PIN", cur) { cur = it }
            OutlinedTextField(
                value = q, onValueChange = { q = it }, label = { Text("New question") },
                colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = a, onValueChange = { a = it }, label = { Text("Answer") }, singleLine = true,
                colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            ConfirmRow(onCancel = close) {
                when {
                    cur != storedPin -> Toast.makeText(context, "Current PIN is wrong", Toast.LENGTH_SHORT).show()
                    q.isBlank() || a.isBlank() -> Toast.makeText(context, "Fill both question and answer", Toast.LENGTH_SHORT).show()
                    else -> {
                        preferences.setSecurityDetails(storedPin, q.trim(), a)
                        Toast.makeText(context, "Security question updated", Toast.LENGTH_SHORT).show()
                        close()
                    }
                }
            }
        }

        SecDialog.AUTOLOCK -> Dialog(onDismissRequest = { dialog = SecDialog.NONE }) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp), isDarkMode = isDarkMode, strong = true) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Security Auto Lock", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(Modifier.height(4.dp))
                    Text("Choose when the Security Area should lock again.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        AppPreferences.AUTO_LOCK_IMMEDIATE to "Immediately",
                        AppPreferences.AUTO_LOCK_ON_LEAVE_AREA to "When leaving Security Area",
                        AppPreferences.AUTO_LOCK_ON_APP_CLOSE to "When app is closed",
                        AppPreferences.AUTO_LOCK_ON_SCREEN_OFF to "When screen is locked / turned off"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { preferences.setSecurityAutoLockMode(value); dialog = SecDialog.NONE }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = autoLockMode == value,
                                onClick = { preferences.setSecurityAutoLockMode(value); dialog = SecDialog.NONE },
                                colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, color = textColor)
                        }
                    }
                }
            }
        }

        SecDialog.NONE -> {}
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CrimsonPrimary, focusedLabelColor = CrimsonPrimary
)

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConfirmRow(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)) { Text("Save") }
    }
}

@Composable
private fun ChangeDialog(
    isDarkMode: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp), isDarkMode = isDarkMode, strong = true) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                content(onDismiss)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary, letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp))
}

/** Rounded-rectangle setting row used across the security settings. */
@Composable
fun SettingRect(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDarkMode: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        isDarkMode = isDarkMode,
        elevation = 3.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CrimsonPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = CrimsonPrimary, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
            }
            if (trailing != null) trailing() else Icon(Icons.Default.ChevronRight, null, tint = CrimsonPrimary)
        }
    }
}
