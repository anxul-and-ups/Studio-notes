package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val MAIL_API = "https://api.mail.tm"
private val mailClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
private val jsonType = "application/json".toMediaType()

private data class MailAccount(
    val address: String,
    val password: String,
    val token: String,
    val deleted: Boolean = false
)

private data class MailMessage(
    val id: String,
    val from: String,
    val fromName: String = "",
    val to: String = "",
    val subject: String,
    val intro: String,
    val seen: Boolean,
    val body: String = "",
    val htmlBody: String = "",
    val createdAt: String = ""
)

private object TempMailStore {
    private const val PREF = "au_temp_mail_store"
    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun accounts(context: Context): MutableList<MailAccount> {
        val array = runCatching { JSONArray(prefs(context).getString("accounts", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                array.getJSONObject(i).let {
                    MailAccount(
                        it.getString("address"),
                        it.getString("password"),
                        it.optString("token", ""),
                        it.optBoolean("deleted")
                    )
                }
            }.getOrNull()
        }.toMutableList()
    }

    fun save(context: Context, list: List<MailAccount>, active: String? = null) {
        val array = JSONArray()
        list.forEach { a ->
            array.put(
                JSONObject()
                    .put("address", a.address)
                    .put("password", a.password)
                    .put("token", a.token)
                    .put("deleted", a.deleted)
            )
        }
        prefs(context).edit().putString("accounts", array.toString()).apply()
        if (active != null) prefs(context).edit().putString("active", active).apply()
    }

    fun active(context: Context, list: List<MailAccount>) =
        list.firstOrNull { it.address == prefs(context).getString("active", null) && !it.deleted }
            ?: list.firstOrNull { !it.deleted }

    fun deletedMessages(context: Context): MutableSet<String> =
        prefs(context).getStringSet("deleted_messages", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun saveDeletedMessages(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet("deleted_messages", ids).apply()
    }

    fun knownIds(context: Context, address: String): Set<String> =
        prefs(context).getStringSet("known_$address", emptySet())?.toSet() ?: emptySet()

    fun saveKnownIds(context: Context, address: String, ids: Set<String>) {
        prefs(context).edit().putStringSet("known_$address", ids).apply()
    }

    fun cachedMessages(context: Context): List<MailMessage> {
        val array = runCatching { JSONArray(prefs(context).getString("cached_messages", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                array.getJSONObject(i).let {
                    MailMessage(
                        id = it.getString("id"),
                        from = it.getString("from"),
                        fromName = it.optString("fromName", ""),
                        to = it.optString("to", ""),
                        subject = it.getString("subject"),
                        intro = it.optString("intro", ""),
                        seen = it.optBoolean("seen"),
                        body = it.optString("body", ""),
                        htmlBody = it.optString("htmlBody", ""),
                        createdAt = it.optString("createdAt", "")
                    )
                }
            }.getOrNull()
        }
    }

    fun saveMessages(context: Context, messages: List<MailMessage>) {
        val all = (cachedMessages(context).associateBy { it.id } + messages.associateBy { it.id }).values
        val array = JSONArray()
        all.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("from", it.from)
                    .put("fromName", it.fromName)
                    .put("to", it.to)
                    .put("subject", it.subject)
                    .put("intro", it.intro)
                    .put("seen", it.seen)
                    .put("body", it.body)
                    .put("htmlBody", it.htmlBody)
                    .put("createdAt", it.createdAt)
            )
        }
        prefs(context).edit().putString("cached_messages", array.toString()).apply()
    }
}

private suspend fun api(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(MAIL_API + path).header("Accept", "application/json")
    if (token != null) builder.header("Authorization", "Bearer $token")
    when (method) {
        "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(jsonType))
        "DELETE" -> builder.delete()
        else -> builder.get()
    }
    mailClient.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = runCatching {
                val errorJson = JSONObject(text.ifBlank { "{}" })
                errorJson.optString("detail").ifBlank { errorJson.optString("message") }
            }.getOrNull().orEmpty()
            throw IllegalStateException(detail.ifBlank { "Mail request failed (${response.code}). Please try again." })
        }
        if (text.isBlank()) JSONObject()
        else if (text.trimStart().startsWith("[")) JSONObject().put("array_data", JSONArray(text))
        else JSONObject(text)
    }
}

private suspend fun createMailAccount(): MailAccount {
    val domainsRes = api("GET", "/domains")
    val domains = domainsRes.optJSONArray("hydra:member") ?: domainsRes.optJSONArray("array_data") ?: JSONArray()
    val domain = (0 until domains.length()).mapNotNull { domains.optJSONObject(it) }.firstOrNull { it.optBoolean("isActive", true) && !it.optBoolean("isPrivate", false) }?.optString("domain")
        ?: throw IllegalStateException("No available email domain from provider")
    val local = "au${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val address = "$local@$domain"
    val password = "Au${UUID.randomUUID().toString().replace("-", "").take(18)}9!"
    api("POST", "/accounts", body = JSONObject().put("address", address).put("password", password))
    val token = api("POST", "/token", body = JSONObject().put("address", address).put("password", password)).getString("token")
    return MailAccount(address, password, token)
}

private suspend fun fetchMessages(account: MailAccount): List<MailMessage> {
    if (account.token.isBlank()) throw IllegalStateException("This saved address has no login session. Generate a new address.")
    val response = api("GET", "/messages?page=1", account.token)
    val rows = response.optJSONArray("hydra:member") ?: response.optJSONArray("array_data") ?: JSONArray()
    return (0 until rows.length()).mapNotNull { i ->
        rows.optJSONObject(i)?.let { row ->
            val fromObj = row.optJSONObject("from")
            val fromAddr = fromObj?.optString("address", "Unknown sender") ?: "Unknown sender"
            val fromName = fromObj?.optString("name", "") ?: ""
            val toArray = row.optJSONArray("to")
            val toAddr = toArray?.optJSONObject(0)?.optString("address", account.address) ?: account.address
            val createdAt = row.optString("createdAt", "")
            MailMessage(
                id = row.optString("id"),
                from = fromAddr,
                fromName = fromName,
                to = toAddr,
                subject = row.optString("subject", "(No subject)").ifBlank { "(No subject)" },
                intro = row.optString("intro", ""),
                seen = row.optBoolean("seen"),
                createdAt = createdAt
            )
        }
    }.filter { it.id.isNotBlank() }
}

private data class MessageBodyResult(
    val plainText: String,
    val htmlContent: String,
    val fromName: String,
    val toAddress: String,
    val createdAt: String
)

private suspend fun fetchMessageDetail(account: MailAccount, messageId: String): MessageBodyResult {
    val detail = api("GET", "/messages/${java.net.URLEncoder.encode(messageId, "UTF-8")}", account.token)

    val plain = when {
        detail.has("text") && !detail.isNull("text") -> {
            val t = detail.opt("text")
            if (t is JSONArray) {
                (0 until t.length()).mapNotNull { t.optString(it).takeIf(String::isNotBlank) }.joinToString("\n\n")
            } else {
                t?.toString().orEmpty()
            }
        }
        else -> ""
    }

    val html = when {
        detail.has("html") && !detail.isNull("html") -> {
            val h = detail.opt("html")
            if (h is JSONArray) {
                (0 until h.length()).mapNotNull { h.optString(it).takeIf(String::isNotBlank) }.joinToString("\n")
            } else {
                h?.toString().orEmpty()
            }
        }
        else -> ""
    }

    val fromObj = detail.optJSONObject("from")
    val fromName = fromObj?.optString("name", "") ?: ""
    val toArray = detail.optJSONArray("to")
    val toAddr = toArray?.optJSONObject(0)?.optString("address", "") ?: ""
    val createdAt = detail.optString("createdAt", "")

    val finalPlain = if (plain.isNotBlank()) {
        plain
    } else if (html.isNotBlank()) {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } else {
        detail.optString("intro").ifBlank { "This message has no readable text body." }
    }

    return MessageBodyResult(
        plainText = finalPlain,
        htmlContent = html,
        fromName = fromName,
        toAddress = toAddr,
        createdAt = createdAt
    )
}

private fun extractOtpOrVerification(text: String, subject: String = ""): String? {
    val combined = "$subject\n$text"
    val keywordRegex = Regex("""(?i)(?:verification\s*code|security\s*code|login\s*code|confirmation\s*code|otp\s*code|otp|passcode|pin)\s*(?:is|:|\-)?\s*([0-9]{4,8})\b""")
    val match1 = keywordRegex.find(combined)
    if (match1 != null) {
        val code = match1.groupValues[1]
        if (code.isNotBlank()) return code
    }

    val sixDigitRegex = Regex("""\b([0-9]{6})\b""")
    val matches = sixDigitRegex.findAll(combined)
    for (m in matches) {
        val c = m.groupValues[1]
        if (!c.startsWith("19") && !c.startsWith("20")) {
            return c
        }
    }

    val shortCodeRegex = Regex("""(?i)(?:code|pin)\D{1,6}([0-9]{4,5})\b""")
    val matchShort = shortCodeRegex.find(combined)
    if (matchShort != null) {
        return matchShort.groupValues[1]
    }

    return null
}

private fun formatMailTime(dateString: String): String {
    if (dateString.isBlank()) return ""
    return try {
        val clean = dateString.substringBefore(".").replace("Z", "")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = format.parse(clean) ?: return dateString.take(10)
        val now = System.currentTimeMillis()
        val diff = now - date.time
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(date)
            else -> java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        dateString.take(10)
    }
}

private fun getAvatarColor(key: String): Color {
    val palette = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF06B6D4), // Cyan
        Color(0xFF6366F1), // Indigo
        Color(0xFFEF4444)  // Red
    )
    val hash = Math.abs(key.hashCode())
    return palette[hash % palette.size]
}

/** Periodic background check. Android/WorkManager controls the exact run time (minimum 15 minutes). */
class TempMailNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("au_temp_mail_store", Context.MODE_PRIVATE)
            val activeAddress = prefs.getString("active", null) ?: return Result.success()
            val accounts = JSONArray(prefs.getString("accounts", "[]"))
            var active: JSONObject? = null
            for (i in 0 until accounts.length()) {
                val row = accounts.optJSONObject(i) ?: continue
                if (row.optString("address") == activeAddress && !row.optBoolean("deleted")) { active = row; break }
            }
            val account = active ?: return Result.success()
            val token = account.optString("token", "")
            if (token.isBlank()) return Result.success()
            val messages = fetchMessages(MailAccount(activeAddress, account.optString("password", ""), token))
            val ids = messages.map { it.id }.toSet()
            val old = prefs.getStringSet("known_$activeAddress", emptySet())?.toSet() ?: emptySet()
            val newMessages = messages.filter { it.id !in old }
            if (old.isNotEmpty() && newMessages.isNotEmpty()) showTempMailNotification(applicationContext, newMessages.size, newMessages.first().subject, activeAddress)
            prefs.edit().putStringSet("known_$activeAddress", old + ids).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

private fun scheduleTempMailBackgroundChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<TempMailNotificationWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("au_temp_mail_background_check", ExistingPeriodicWorkPolicy.UPDATE, request)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMailScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onSaveAsNote: ((title: String, content: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scheduleTempMailBackgroundChecks(context) }

    var accounts by remember { mutableStateOf(TempMailStore.accounts(context)) }
    var active by remember { mutableStateOf(TempMailStore.active(context, accounts)) }
    var messages by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
    var cachedMessages by remember { mutableStateOf(TempMailStore.cachedMessages(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Full-screen viewer state
    var selectedMessage by remember { mutableStateOf<MailMessage?>(null) }
    var selectedMessageDetail by remember { mutableStateOf<MessageBodyResult?>(null) }
    var messageBodyLoading by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Ready") }
    val deletedMessages = remember { mutableStateOf(TempMailStore.deletedMessages(context)) }

    // System BackHandler: if full screen mail is open, go back to inbox
    BackHandler(enabled = selectedMessage != null) {
        selectedMessage = null
        selectedMessageDetail = null
    }

    fun refreshMessages(notify: Boolean = false) {
        val account = active ?: return
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val fresh = fetchMessages(account)
                val ids = fresh.map { it.id }.toSet()
                val oldIds = TempMailStore.knownIds(context, account.address)
                val newMessages = fresh.filter { it.id !in oldIds }
                if (notify && oldIds.isNotEmpty() && newMessages.isNotEmpty()) {
                    showTempMailNotification(context, newMessages.size, newMessages.first().subject, account.address)
                }
                TempMailStore.saveKnownIds(context, account.address, oldIds + ids)
                messages = fresh
                TempMailStore.saveMessages(context, fresh)
                cachedMessages = TempMailStore.cachedMessages(context)
                status = if (fresh.isEmpty()) "Inbox checked — no messages received yet" else "Inbox checked — ${fresh.size} message(s) found"
            } catch (e: Exception) {
                error = e.message ?: "Unable to refresh inbox"
                status = "Inbox check failed"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(active?.address) {
        if (active != null) refreshMessages()
    }

    LaunchedEffect(active?.address) {
        while (active != null) {
            delay(30_000)
            if (active != null) refreshMessages(notify = true)
        }
    }

    // Detail fetch for full screen viewer
    LaunchedEffect(selectedMessage?.id, active?.address) {
        val message = selectedMessage
        val account = active
        if (message != null) {
            if (message.body.isNotBlank() || message.htmlBody.isNotBlank()) {
                selectedMessageDetail = MessageBodyResult(
                    plainText = message.body.ifBlank { message.intro },
                    htmlContent = message.htmlBody,
                    fromName = message.fromName,
                    toAddress = message.to.ifBlank { account?.address.orEmpty() },
                    createdAt = message.createdAt
                )
            }
            if (account != null && (message.body.isBlank() && message.htmlBody.isBlank())) {
                messageBodyLoading = true
                try {
                    val detail = fetchMessageDetail(account, message.id)
                    selectedMessageDetail = detail
                    val updated = message.copy(
                        body = detail.plainText,
                        htmlBody = detail.htmlContent,
                        fromName = detail.fromName.ifBlank { message.fromName },
                        to = detail.toAddress.ifBlank { message.to },
                        createdAt = detail.createdAt.ifBlank { message.createdAt }
                    )
                    selectedMessage = updated
                    TempMailStore.saveMessages(context, listOf(updated))
                    cachedMessages = TempMailStore.cachedMessages(context)
                } catch (e: Exception) {
                    selectedMessageDetail = MessageBodyResult(
                        plainText = message.intro.ifBlank { e.message ?: "Unable to load email content." },
                        htmlContent = "",
                        fromName = message.fromName,
                        toAddress = account.address,
                        createdAt = message.createdAt
                    )
                } finally {
                    messageBodyLoading = false
                }
            }
        }
    }

    val bg = if (isDarkMode) Color(0xFF0F1218) else Color(0xFFF4F6F9)
    val cardBg = if (isDarkMode) Color(0xFF191E28) else Color.White
    val fg = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val borderCol = if (isDarkMode) Color(0xFF263040) else Color(0xFFE2E8F0)

    val viewingMessage = selectedMessage
    if (viewingMessage != null) {
        FullScreenMailView(
            message = viewingMessage,
            detail = selectedMessageDetail,
            isLoading = messageBodyLoading,
            isDarkMode = isDarkMode,
            activeAddress = active?.address.orEmpty(),
            onBack = {
                selectedMessage = null
                selectedMessageDetail = null
            },
            onDelete = {
                deletedMessages.value = (deletedMessages.value + viewingMessage.id).toMutableSet()
                TempMailStore.saveDeletedMessages(context, deletedMessages.value)
                Toast.makeText(context, "Email moved to Trash", Toast.LENGTH_SHORT).show()
                selectedMessage = null
                selectedMessageDetail = null
            },
            onSaveToNotes = {
                val plainText = selectedMessageDetail?.plainText ?: viewingMessage.intro
                val content = buildString {
                    append("From: ${viewingMessage.from}\n")
                    if (viewingMessage.to.isNotBlank()) append("To: ${viewingMessage.to}\n")
                    if (viewingMessage.createdAt.isNotBlank()) append("Date: ${viewingMessage.createdAt}\n")
                    append("\n---\n\n")
                    append(plainText)
                }
                if (onSaveAsNote != null) {
                    onSaveAsNote("[Mail] ${viewingMessage.subject}", content)
                }
                Toast.makeText(context, "Saved to AU Notes!", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Temp Mail", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Secure disposable inbox", fontSize = 12.sp, color = fg.copy(alpha = 0.65f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshMessages() },
                        enabled = !busy && active != null
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh inbox")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bg,
                    titleContentColor = fg,
                    navigationIconContentColor = fg,
                    actionIconContentColor = fg
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            // Active Email Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "ACTIVE INBOX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        if (busy) {
                            Text(
                                "Updating…",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Address box with tap to copy
                    Surface(
                        color = if (isDarkMode) Color(0xFF121620) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                active?.address?.let { address ->
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("Temp Mail", address))
                                    Toast.makeText(context, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                                    status = "Email copied!"
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = active?.address ?: "No email generated",
                                color = fg,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        val account = createMailAccount()
                                        accounts = (accounts.filterNot { it.address == account.address } + account).toMutableList()
                                        active = account
                                        TempMailStore.save(context, accounts, account.address)
                                        messages = emptyList()
                                        status = "New address ready"
                                        Toast.makeText(context, "New address created!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not create an address"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Address", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { refreshMessages() },
                            enabled = !busy && active != null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check")
                        }
                    }

                    Text(
                        status,
                        fontSize = 12.sp,
                        color = fg.copy(alpha = .65f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val visibleInbox = messages.filter { it.id !in deletedMessages.value }
            val tabs = listOf(
                "Inbox (${visibleInbox.size})",
                "Saved (${accounts.count { !it.deleted }})",
                "Trash (${cachedMessages.count { it.id in deletedMessages.value }})"
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    if (visibleInbox.isEmpty() && !busy) {
                        EmptyTempMail(
                            title = "Inbox is empty",
                            subtitle = "Emails sent to your temporary address will appear here automatically."
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(visibleInbox, key = { it.id }) { message ->
                            MailListItemCard(
                                message = message,
                                fg = fg,
                                cardBg = cardBg,
                                borderCol = borderCol,
                                isDarkMode = isDarkMode,
                                onClick = {
                                    selectedMessage = message
                                },
                                onDelete = {
                                    deletedMessages.value = (deletedMessages.value + message.id).toMutableSet()
                                    TempMailStore.saveDeletedMessages(context, deletedMessages.value)
                                    Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }

                1 -> AccountList(
                    accounts = accounts.filter { !it.deleted },
                    activeAddress = active?.address,
                    fg = fg,
                    card = cardBg,
                    borderCol = borderCol,
                    onSelect = { account ->
                        active = account
                        TempMailStore.save(context, accounts, account.address)
                        selectedTab = 0
                    },
                    onDelete = { account ->
                        accounts = accounts.map {
                            if (it.address == account.address) it.copy(deleted = true) else it
                        }.toMutableList()
                        TempMailStore.save(context, accounts)
                        if (active?.address == account.address) {
                            active = TempMailStore.active(context, accounts)
                            active?.let { TempMailStore.save(context, accounts, it.address) }
                        }
                    }
                )

                2 -> {
                    val removed = cachedMessages.filter { it.id in deletedMessages.value }
                    if (removed.isEmpty()) {
                        EmptyTempMail(
                            title = "Trash is empty",
                            subtitle = "Deleted messages from the current session will show up here."
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(removed, key = { it.id }) { message ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            message.subject.ifBlank { "(No Subject)" },
                                            color = fg,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(message.from, color = fg.copy(alpha = 0.7f), fontSize = 12.sp)
                                    }
                                    IconButton(onClick = {
                                        deletedMessages.value = (deletedMessages.value - message.id).toMutableSet()
                                        TempMailStore.saveDeletedMessages(context, deletedMessages.value)
                                        Toast.makeText(context, "Message restored to Inbox", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Restore, "Recover message", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * Modern card representing an email item in the list with avatar, time, OTP pill, and actions
 */
@Composable
private fun MailListItemCard(
    message: MailMessage,
    fg: Color,
    cardBg: Color,
    borderCol: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val detectedOtp = remember(message.intro, message.subject, message.body) {
        extractOtpOrVerification(message.body.ifBlank { message.intro }, message.subject)
    }

    val senderInitial = (message.fromName.ifBlank { message.from }).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val avatarColor = remember(message.from) { getAvatarColor(message.from) }
    val formattedTime = remember(message.createdAt) { formatMailTime(message.createdAt) }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Sender Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = senderInitial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // Header row: Sender name + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.fromName.ifBlank { message.from },
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (formattedTime.isNotBlank()) {
                        Text(
                            text = formattedTime,
                            color = fg.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // Subject
                Text(
                    text = message.subject.ifBlank { "(No Subject)" },
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Snippet intro
                if (message.intro.isNotBlank()) {
                    Text(
                        text = message.intro,
                        color = fg.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                // If OTP/Code detected, show a badge
                if (detectedOtp != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Code: $detectedOtp",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp).padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Move to Trash",
                    tint = fg.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Dedicated Full Screen Mail Viewer Screen
 * Provides clean typography, rich HTML rendering via WebView, OTP quick copy,
 * Save to AU Notes, Share, and Copy full text actions.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenMailView(
    message: MailMessage,
    detail: MessageBodyResult?,
    isLoading: Boolean,
    isDarkMode: Boolean,
    activeAddress: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveToNotes: () -> Unit
) {
    val context = LocalContext.current
    val bg = if (isDarkMode) Color(0xFF0F1218) else Color(0xFFF4F6F9)
    val cardBg = if (isDarkMode) Color(0xFF191E28) else Color.White
    val fg = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val borderCol = if (isDarkMode) Color(0xFF263040) else Color(0xFFE2E8F0)

    val plainText = detail?.plainText?.ifBlank { message.body.ifBlank { message.intro } }
        ?: message.body.ifBlank { message.intro }
    val htmlContent = detail?.htmlContent?.ifBlank { message.htmlBody } ?: message.htmlBody

    val detectedOtp = remember(plainText, message.subject, htmlContent) {
        extractOtpOrVerification(plainText, message.subject)
    }

    var viewMode by remember(htmlContent) {
        mutableStateOf(if (htmlContent.isNotBlank()) "rich" else "text")
    }

    val senderInitial = (message.fromName.ifBlank { message.from }).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val avatarColor = remember(message.from) { getAvatarColor(message.from) }
    val formattedTime = remember(message.createdAt) { formatMailTime(message.createdAt) }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Email Message",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Inbox")
                    }
                },
                actions = {
                    // Save as AU Note
                    IconButton(onClick = onSaveToNotes) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "Save to AU Notes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Copy text
                    IconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Email Content", plainText))
                        Toast.makeText(context, "Email content copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text")
                    }

                    // Share
                    IconButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_SUBJECT, message.subject)
                            putExtra(Intent.EXTRA_TEXT, "Subject: ${message.subject}\nFrom: ${message.from}\n\n$plainText")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Email via"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Email")
                    }

                    // Delete
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Email",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bg,
                    titleContentColor = fg,
                    navigationIconContentColor = fg,
                    actionIconContentColor = fg
                )
            )
        },
        bottomBar = {
            Surface(
                color = cardBg,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSaveToNotes,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save to AU Notes", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("Email", plainText))
                            Toast.makeText(context, "Full text copied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Subject in prominent typography
                Text(
                    text = message.subject.ifBlank { "(No Subject)" },
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp
                )

                Spacer(Modifier.height(14.dp))

                // Sender Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = senderInitial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message.fromName.ifBlank { message.from },
                                    color = fg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (formattedTime.isNotBlank()) {
                                    Text(
                                        text = formattedTime,
                                        color = fg.copy(alpha = 0.55f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }

                            if (message.fromName.isNotBlank() && message.from.isNotBlank()) {
                                Text(
                                    text = "<${message.from}>",
                                    color = fg.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(2.dp))

                            Text(
                                text = "To: ${message.to.ifBlank { activeAddress }}",
                                color = fg.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // High-priority OTP / Verification Code Banner
                if (detectedOtp != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFEFF6FF)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isDarkMode) Color(0xFF3B82F6) else Color(0xFF60A5FA)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "VERIFICATION CODE DETECTED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = detectedOtp,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 3.sp,
                                        color = fg
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("Verification Code", detectedOtp))
                                    Toast.makeText(context, "Code copied: $detectedOtp", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // If HTML content is available, show Rich / Plain switcher
                if (htmlContent.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDarkMode) Color(0xFF1E2430) else Color(0xFFE2E8F0))
                                .padding(3.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewMode == "rich") MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { viewMode = "rich" }
                            ) {
                                Text(
                                    "🎨 Rich HTML",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (viewMode == "rich") MaterialTheme.colorScheme.onPrimary else fg.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewMode == "text") MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { viewMode = "text" }
                            ) {
                                Text(
                                    "📄 Plain Text",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (viewMode == "text") MaterialTheme.colorScheme.onPrimary else fg.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Body Display Container (Full Screen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                if (viewMode == "rich" && htmlContent.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.domStorageEnabled = false
                                    setBackgroundColor(0)
                                }
                            },
                            update = { webView ->
                                val textHex = if (isDarkMode) "#E2E8F0" else "#0F172A"
                                val linkHex = if (isDarkMode) "#60A5FA" else "#2563EB"
                                val bgHex = if (isDarkMode) "#191E28" else "#FFFFFF"
                                val customCss = """
                                    <style>
                                        html, body {
                                            color: $textHex !important;
                                            background-color: $bgHex !important;
                                            font-family: -apple-system, Roboto, Helvetica, Arial, sans-serif !important;
                                            font-size: 15px !important;
                                            line-height: 1.6 !important;
                                            padding: 12px !important;
                                            margin: 0 !important;
                                            word-wrap: break-word !important;
                                            overflow-wrap: break-word !important;
                                        }
                                        a { color: $linkHex !important; font-weight: 500 !important; }
                                        img { max-width: 100% !important; height: auto !important; border-radius: 8px !important; }
                                        table { max-width: 100% !important; border-collapse: collapse !important; }
                                        pre, code {
                                            background-color: ${if (isDarkMode) "#222938" else "#F1F5F9"} !important;
                                            color: ${if (isDarkMode) "#F1F5F9" else "#0F172A"} !important;
                                            padding: 4px 6px !important;
                                            border-radius: 4px !important;
                                            font-size: 13px !important;
                                        }
                                    </style>
                                """.trimIndent()

                                val fullDoc = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                                        $customCss
                                    </head>
                                    <body>
                                        $htmlContent
                                    </body>
                                    </html>
                                """.trimIndent()
                                webView.loadDataWithBaseURL(null, fullDoc, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = plainText.ifBlank { "No message body available." },
                                    color = fg,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp
                                )
                                Spacer(Modifier.height(30.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AccountList(
    accounts: List<MailAccount>,
    activeAddress: String?,
    fg: Color,
    card: Color,
    borderCol: Color,
    onSelect: (MailAccount) -> Unit,
    onDelete: (MailAccount) -> Unit
) {
    if (accounts.isEmpty()) {
        EmptyTempMail("No saved addresses", "Generate or switch an email address to build your history.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(accounts, key = { it.address }) { account ->
            val isActive = account.address == activeAddress
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (isActive) 1.5.dp else 1.dp,
                    if (isActive) MaterialTheme.colorScheme.primary else borderCol
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(account) }
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else fg.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else fg,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            account.address,
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            if (isActive) "Active address" else "Tap to switch to this address",
                            color = if (isActive) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.65f),
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = { onDelete(account) }) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete address",
                            tint = fg.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EmptyTempMail(title: String, subtitle: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Email,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun showTempMailNotification(context: Context, count: Int, subject: String, address: String) {
    val channelId = "au_temp_mail_messages"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Temp Mail messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New temporary email messages"
            }
        )
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(if (count == 1) "New Temp Mail message" else "$count new Temp Mail messages")
        .setContentText("$subject • $address")
        .setStyle(NotificationCompat.BigTextStyle().bigText("$subject\nInbox: $address"))
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify((address + subject).hashCode(), notification) }
}
