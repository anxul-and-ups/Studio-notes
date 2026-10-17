package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.util.SirenAudioPlayer

/**
 * Unified PIN gate used for the Security Area, locked notes, locked folders,
 * the API Room, and any other protected feature.
 *
 * When [requireSetup] is true (i.e. the user has never configured a real PIN),
 * this shows a 3-step first-time wizard instead of the unlock keypad:
 *   Create PIN -> Confirm PIN -> Security Question & Answer
 * Completing the wizard calls [onSetupComplete] to persist the new credentials
 * and then [onUnlocked] to grant access for this one attempt.
 *
 * When [requireSetup] is false, the normal 4-digit keypad / biometric unlock
 * is shown. 3 consecutive wrong PIN attempts trigger the air-raid siren and
 * a hard "Access Denied" message, matching the same behaviour used for
 * Security Area, locked notes/folders, the API Room, and protected chatbot access.
 */
@Composable
fun PinLockDialog(
    correctPin: String,
    title: String = "Security Passcode",
    subtitle: String = "Enter 4-digit PIN or use Fingerprint to unlock",
    securityQuestion: String = "What was your first project?",
    securityAnswer: String = "AUNotes",
    isDarkMode: Boolean = true,
    requireSetup: Boolean = false,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    onPinReset: ((String) -> Unit)? = null,
    onSetupComplete: ((pin: String, question: String, answer: String) -> Unit)? = null,
    // Optional "?" hint shown under the keypad (e.g. where to switch off delete-verification)
    helpText: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wrongAttempts by remember { mutableStateOf(0) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var enteredAnswer by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }

    // First-time setup wizard state
    var setupStep by remember { mutableStateOf(0) } // 0=create, 1=confirm, 2=question
    var setupPin by remember { mutableStateOf("") }
    var setupConfirmPin by remember { mutableStateOf("") }
    var setupQuestion by remember { mutableStateOf(securityQuestion) }
    var setupAnswer by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }

    fun registerWrongAttempt() {
        wrongAttempts += 1
        if (wrongAttempts >= 3) {
            SirenAudioPlayer.playAirRaidSiren(scope)
            errorMessage = "Access Denied! Too many failed attempts."
            wrongAttempts = 0
        } else {
            errorMessage = "Incorrect PIN. Try again."
        }
        enteredPin = ""
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            isDarkMode = isDarkMode,
            strong = true
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CrimsonPrimary
                        )
                        Text(
                            text = if (requireSetup) {
                                when (setupStep) {
                                    0 -> "Create Security PIN"
                                    1 -> "Confirm Security PIN"
                                    else -> "Set Recovery Question"
                                }
                            } else title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDarkMode) Color.White else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (requireSetup) {
                        when (setupStep) {
                            0 -> "This is your first time here. Set a 4-digit PIN to protect this content."
                            1 -> "Re-enter the same 4-digit PIN to confirm it."
                            else -> "Set a recovery question in case you forget your PIN."
                        }
                    } else subtitle,
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.DarkGray
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (requireSetup && setupStep == 2) {
                    // Security question + answer step
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = setupQuestion,
                            onValueChange = { setupQuestion = it },
                            label = { Text("Security Question") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                focusedLabelColor = CrimsonPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = setupAnswer,
                            onValueChange = { setupAnswer = it; setupError = null },
                            label = { Text("Your Answer") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                focusedLabelColor = CrimsonPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (setupError != null) {
                            Text(setupError!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (setupQuestion.isBlank() || setupAnswer.isBlank()) {
                                    setupError = "Please fill in both fields."
                                } else {
                                    onSetupComplete?.invoke(setupPin, setupQuestion.trim(), setupAnswer.trim())
                                    onUnlocked()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Finish Setup & Continue")
                        }
                    }
                } else {
                    // 4 Neumorphic Inset PIN Wells / Extruded Indicators
                    val displayedPin = if (!requireSetup) enteredPin else if (setupStep == 0) setupPin else setupConfirmPin

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            val isFilled = index < displayedPin.length
                            GlassCard(
                                modifier = Modifier.size(18.dp),
                                shape = CircleShape,
                                isDarkMode = isDarkMode,
                                isInset = !isFilled,
                                elevation = if (isFilled) 3.dp else 0.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (isFilled) {
                                                Modifier.background(
                                                    Brush.radialGradient(listOf(CrimsonPrimary, Color(0xFFCC1F41)))
                                                )
                                            } else Modifier
                                        )
                                )
                            }
                        }
                    }

                    val currentError = if (requireSetup) setupError else errorMessage
                    if (currentError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = currentError,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Tactile Numeric keypad with real dual shadows
                    val digits = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(if (requireSetup) "" else "FP", "0", "DEL")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        digits.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                row.forEach { digit ->
                                    if (digit == "" ) {
                                        Spacer(modifier = Modifier.size(58.dp))
                                    } else {
                                    GlassCard(
                                        modifier = Modifier.size(58.dp),
                                        shape = CircleShape,
                                        isDarkMode = isDarkMode,
                                        elevation = 5.dp,
                                        onClick = {
                                            if (requireSetup) {
                                                when (digit) {
                                                    "DEL" -> {
                                                        if (setupStep == 0) setupPin = setupPin.dropLast(1)
                                                        else setupConfirmPin = setupConfirmPin.dropLast(1)
                                                        setupError = null
                                                    }
                                                    else -> {
                                                        if (setupStep == 0 && setupPin.length < 4) {
                                                            setupPin += digit
                                                            if (setupPin.length == 4) setupStep = 1
                                                        } else if (setupStep == 1 && setupConfirmPin.length < 4) {
                                                            setupConfirmPin += digit
                                                            if (setupConfirmPin.length == 4) {
                                                                if (setupConfirmPin == setupPin) {
                                                                    setupStep = 2
                                                                } else {
                                                                    setupError = "PINs didn't match. Try again."
                                                                    setupPin = ""
                                                                    setupConfirmPin = ""
                                                                    setupStep = 0
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                when (digit) {
                                                    "DEL" -> {
                                                        if (enteredPin.isNotEmpty()) {
                                                            enteredPin = enteredPin.dropLast(1)
                                                            errorMessage = null
                                                        }
                                                    }
                                                    "FP" -> {
                                                        val activity = context as? androidx.fragment.app.FragmentActivity
                                                        if (activity == null) {
                                                            Toast.makeText(context, "Biometric unavailable here — use your PIN", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val availability = com.example.ui.util.BiometricAuthHelper.checkAvailability(activity)
                                                            when (availability) {
                                                                com.example.ui.util.BiometricAuthHelper.Availability.AVAILABLE -> {
                                                                    com.example.ui.util.BiometricAuthHelper.authenticate(activity) { success, error ->
                                                                        if (success) {
                                                                            onUnlocked()
                                                                        } else if (error != null) {
                                                                            errorMessage = error
                                                                        }
                                                                    }
                                                                }
                                                                com.example.ui.util.BiometricAuthHelper.Availability.NOT_ENROLLED -> {
                                                                    Toast.makeText(context, "No fingerprint/face set up on this device yet", Toast.LENGTH_SHORT).show()
                                                                }
                                                                else -> {
                                                                    Toast.makeText(context, "This device has no biometric hardware — use your PIN", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                    else -> {
                                                        if (enteredPin.length < 4) {
                                                            val next = enteredPin + digit
                                                            enteredPin = next
                                                            if (next.length == 4) {
                                                                if (next == correctPin) {
                                                                    wrongAttempts = 0
                                                                    onUnlocked()
                                                                } else {
                                                                    registerWrongAttempt()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (digit == "FP") {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Fingerprint",
                                                    tint = CrimsonPrimary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            } else if (digit == "DEL") {
                                                Text(
                                                    text = "⌫",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDarkMode) Color.White else Color.Black
                                                )
                                            } else {
                                                Text(
                                                    text = digit,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isDarkMode) Color.White else Color.Black
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

                Spacer(modifier = Modifier.height(14.dp))

                // Forgot PIN / Security Question link (only relevant once a PIN already exists)
                if (!requireSetup) {
                    TextButton(onClick = { showForgotDialog = true }) {
                        Text(
                            text = "Forgot PIN? Reset with Security Question",
                            fontSize = 11.sp,
                            color = CrimsonPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (helpText != null && !requireSetup) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showHelp = !showHelp }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                        }
                        Text(
                            text = if (showHelp) "Hide help" else "Why am I asked for PIN?",
                            fontSize = 11.sp,
                            color = if (isDarkMode) Color.White.copy(0.7f) else Color.DarkGray
                        )
                    }
                    if (showHelp) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = helpText,
                            fontSize = 11.5.sp,
                            color = if (isDarkMode) Color.White.copy(0.75f) else Color(0xFF444444),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000))
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }

    // Security Question Recovery Dialog
    if (showForgotDialog) {
        Dialog(onDismissRequest = { showForgotDialog = false }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                isDarkMode = isDarkMode,
                strong = true
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Reset PIN via Security Question",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonPrimary
                    )

                    Text(
                        text = "Question: $securityQuestion",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color.White else Color.Black
                    )

                    OutlinedTextField(
                        value = enteredAnswer,
                        onValueChange = { enteredAnswer = it },
                        placeholder = { Text("Your Answer...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                        placeholder = { Text("New 4-digit PIN (e.g. 1234)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showForgotDialog = false }) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (enteredAnswer.trim().equals(securityAnswer.trim(), ignoreCase = true)) {
                                    if (newPinInput.length == 4) {
                                        onPinReset?.invoke(newPinInput)
                                        Toast.makeText(context, "PIN successfully reset!", Toast.LENGTH_SHORT).show()
                                        showForgotDialog = false
                                        onUnlocked()
                                    } else {
                                        Toast.makeText(context, "Enter a 4-digit new PIN", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Incorrect answer. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Text("Verify & Unlock")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared PIN gate for destructive actions (delete note / folder / delete forever).
 * Shows the normal PIN keypad plus a "?" hint that explains where the user can turn
 * this check off. Callers should use [needsDeleteVerification] first and call the
 * action directly when it returns false.
 */
@Composable
fun DeletePinDialog(
    preferences: com.example.data.preferences.AppPreferences,
    isDarkMode: Boolean,
    title: String = "Verify PIN to Delete",
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val lockPin by preferences.lockPin.collectAsState()
    val question by preferences.securityQuestion.collectAsState()
    val answer by preferences.securityAnswer.collectAsState()
    PinLockDialog(
        correctPin = lockPin,
        title = title,
        subtitle = "Enter your 4-digit PIN to confirm deletion",
        securityQuestion = question,
        securityAnswer = answer,
        isDarkMode = isDarkMode,
        requireSetup = false,
        onDismiss = onDismiss,
        onUnlocked = onVerified,
        onPinReset = { preferences.setLockPin(it) },
        helpText = "This check appears because \"Verify PIN while deleting\" is ON.\n" +
            "To turn it off: Settings → Security Area → ⚙ Settings icon → Verify PIN while deleting."
    )
}

fun needsDeleteVerification(preferences: com.example.data.preferences.AppPreferences): Boolean =
    preferences.verifyPinOnDelete.value && preferences.hasCustomPin.value
