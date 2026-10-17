package com.example.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private data class MailAccount(val address: String, val password: String, val token: String, val deleted: Boolean = false)
private data class MailMessage(val id: String, val from: String, val subject: String, val intro: String, val seen: Boolean)

private object TempMailStore {
    private const val PREF = "au_temp_mail_store"
    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun accounts(context: Context): MutableList<MailAccount> {
        val array = runCatching { JSONArray(prefs(context).getString("accounts", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i -> runCatching { array.getJSONObject(i).let { MailAccount(it.getString("address"), it.getString("password"), it.optString("token", ""), it.optBoolean("deleted")) } }.getOrNull() }.toMutableList()
    }
    fun save(context: Context, list: List<MailAccount>, active: String? = null) {
        val array = JSONArray(); list.forEach { a -> array.put(JSONObject().put("address", a.address).put("password", a.password).put("token", a.token).put("deleted", a.deleted)) }
        prefs(context).edit().putString("accounts", array.toString()).apply()
        if (active != null) prefs(context).edit().putString("active", active).apply()
    }
    fun active(context: Context, list: List<MailAccount>) = list.firstOrNull { it.address == prefs(context).getString("active", null) && !it.deleted } ?: list.firstOrNull { !it.deleted }
    fun deletedMessages(context: Context): MutableSet<String> = prefs(context).getStringSet("deleted_messages", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun saveDeletedMessages(context: Context, ids: Set<String>) { prefs(context).edit().putStringSet("deleted_messages", ids).apply() }
    fun knownIds(context: Context, address: String): Set<String> = prefs(context).getStringSet("known_$address", emptySet())?.toSet() ?: emptySet()
    fun saveKnownIds(context: Context, address: String, ids: Set<String>) { prefs(context).edit().putStringSet("known_$address", ids).apply() }
    fun cachedMessages(context: Context): List<MailMessage> {
        val array = runCatching { JSONArray(prefs(context).getString("cached_messages", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i -> runCatching { array.getJSONObject(i).let { MailMessage(it.getString("id"), it.getString("from"), it.getString("subject"), it.optString("intro", ""), it.optBoolean("seen")) } }.getOrNull() }
    }
    fun saveMessages(context: Context, messages: List<MailMessage>) {
        val all = (cachedMessages(context).associateBy { it.id } + messages.associateBy { it.id }).values
        val array = JSONArray(); all.forEach { array.put(JSONObject().put("id", it.id).put("from", it.from).put("subject", it.subject).put("intro", it.intro).put("seen", it.seen)) }
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
        if (!response.isSuccessful) throw IllegalStateException(JSONObject(text.ifBlank { "{}" }).optString("detail", "Temp Mail API error (${response.code})"))
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}

private suspend fun createMailAccount(): MailAccount {
    val domains = api("GET", "/domains").optJSONArray("hydra:member") ?: JSONArray()
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
    val rows = api("GET", "/messages?page=1", account.token).optJSONArray("hydra:member") ?: JSONArray()
    return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { row ->
        MailMessage(row.optString("id"), row.optJSONObject("from")?.optString("address", "Unknown sender") ?: "Unknown sender", row.optString("subject", "(No subject)"), row.optString("intro", ""), row.optBoolean("seen"))
    } }.filter { it.id.isNotBlank() }
}

/** Periodic background check. Android/WorkManager controls the exact run time (minimum 15 minutes). */
class TempMailNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
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
    } catch (_: Exception) { Result.retry() }
}

private fun scheduleTempMailBackgroundChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<TempMailNotificationWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("au_temp_mail_background_check", ExistingPeriodicWorkPolicy.UPDATE, request)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMailScreen(isDarkMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scheduleTempMailBackgroundChecks(context) }
    var accounts by remember { mutableStateOf(TempMailStore.accounts(context)) }
    var active by remember { mutableStateOf(TempMailStore.active(context, accounts)) }
    var messages by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
    var cachedMessages by remember { mutableStateOf(TempMailStore.cachedMessages(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedMessage by remember { mutableStateOf<MailMessage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Ready") }
    val deletedMessages = remember { mutableStateOf(TempMailStore.deletedMessages(context)) }

    fun refreshMessages(notify: Boolean = false) {
        val account = active ?: return
        scope.launch {
            busy = true; error = null
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
                status = "Last checked just now"
            } catch (e: Exception) { error = e.message ?: "Unable to refresh inbox" }
            finally { busy = false }
        }
    }

    LaunchedEffect(active?.address) { if (active != null) refreshMessages() }
    LaunchedEffect(active?.address) {
        while (active != null) { delay(30_000); if (active != null) refreshMessages(notify = true) }
    }

    val bg = if (isDarkMode) androidx.compose.ui.graphics.Color(0xFF10131A) else androidx.compose.ui.graphics.Color(0xFFF5F6FA)
    val card = if (isDarkMode) androidx.compose.ui.graphics.Color(0xFF1C222D) else androidx.compose.ui.graphics.Color.White
    val fg = if (isDarkMode) androidx.compose.ui.graphics.Color(0xFFF2F4F8) else androidx.compose.ui.graphics.Color(0xFF1C2430)
    Scaffold(containerColor = bg, topBar = {
        TopAppBar(title = { Column { Text("Temp Mail", fontWeight = FontWeight.Bold); Text("Temporary inbox manager", fontSize = 12.sp) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { IconButton(onClick = { refreshMessages() }, enabled = !busy && active != null) { Icon(Icons.Default.Refresh, "Refresh inbox") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("ACTIVE EMAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(active?.address ?: "No email generated", color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch { busy = true; error = null
                                try { val account = createMailAccount(); accounts = (accounts.filterNot { it.address == account.address } + account).toMutableList(); active = account; TempMailStore.save(context, accounts, account.address); messages = emptyList(); status = "New address created" }
                                catch (e: Exception) { error = e.message ?: "Could not create an address" }
                                finally { busy = false }
                            }
                        }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Change Email") }
                        OutlinedButton(onClick = { active?.address?.let { address -> val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clip.setPrimaryClip(android.content.ClipData.newPlainText("Temp Mail", address)); status = "Email copied" } }, enabled = active != null) { Icon(Icons.Default.ContentCopy, "Copy email") }
                    }
                    Text(status, fontSize = 12.sp, color = fg.copy(alpha = .65f), modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            val tabs = listOf("Inbox", "Changed", "Deleted", "Recover")
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) { tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) } }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(8.dp))
            when (selectedTab) {
                0 -> {
                    val visible = messages.filter { it.id !in deletedMessages.value }
                    if (visible.isEmpty() && !busy) EmptyTempMail("No messages yet", "Tap refresh to check this inbox.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible, key = { it.id }) { message ->
                        Card(colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.fillMaxWidth().clickable { selectedMessage = message }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(message.subject, color = fg, fontWeight = FontWeight.SemiBold); Text(message.from, color = fg.copy(alpha = .7f), fontSize = 12.sp); if (message.intro.isNotBlank()) Text(message.intro, color = fg.copy(alpha = .7f), fontSize = 12.sp, maxLines = 2) }; IconButton(onClick = { deletedMessages.value = (deletedMessages.value + message.id).toMutableSet(); TempMailStore.saveDeletedMessages(context, deletedMessages.value) }) { Icon(Icons.Default.Delete, "Move to deleted") } } }
                    } }
                }
                1 -> AccountList(accounts.filter { !it.deleted }, active?.address, fg, card, onSelect = { account -> active = account; TempMailStore.save(context, accounts, account.address); selectedTab = 0 }, onDelete = { account -> accounts = accounts.map { if (it.address == account.address) it.copy(deleted = true) else it }.toMutableList(); TempMailStore.save(context, accounts); if (active?.address == account.address) { active = TempMailStore.active(context, accounts); active?.let { TempMailStore.save(context, accounts, it.address) } } })
                2 -> {
                    val removed = cachedMessages.filter { it.id in deletedMessages.value }
                    if (removed.isEmpty()) EmptyTempMail("Deleted messages", "Messages you remove from the inbox appear here.")
                    LazyColumn { items(removed, key = { it.id }) { message -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = card)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(message.subject, color = fg, fontWeight = FontWeight.SemiBold); Text(message.from, color = fg, fontSize = 12.sp) }; IconButton(onClick = { deletedMessages.value = (deletedMessages.value - message.id).toMutableSet(); TempMailStore.saveDeletedMessages(context, deletedMessages.value) }) { Icon(Icons.Default.Restore, "Recover message") } } } } }
                }
                else -> AccountList(accounts.filter { it.deleted }, active?.address, fg, card, onSelect = { account -> accounts = accounts.map { if (it.address == account.address) it.copy(deleted = false) else it }.toMutableList(); active = accounts.first { it.address == account.address }; TempMailStore.save(context, accounts, account.address); selectedTab = 0 }, onDelete = { account -> accounts = accounts.filterNot { it.address == account.address }.toMutableList(); TempMailStore.save(context, accounts) })
            }
        }
    }
    selectedMessage?.let { message -> AlertDialog(onDismissRequest = { selectedMessage = null }, title = { Text(message.subject) }, text = { Column { Text("From: ${message.from}"); Spacer(Modifier.height(8.dp)); Text(message.intro.ifBlank { "No preview available. Open your provider inbox to view full content." }) } }, confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("Close") } }) }
}

@Composable private fun AccountList(accounts: List<MailAccount>, activeAddress: String?, fg: androidx.compose.ui.graphics.Color, card: androidx.compose.ui.graphics.Color, onSelect: (MailAccount) -> Unit, onDelete: (MailAccount) -> Unit) {
    if (accounts.isEmpty()) { EmptyTempMail("No saved addresses", "Generate or change an email address to build your history."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(accounts, key = { it.address }) { account -> Card(colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.fillMaxWidth().clickable { onSelect(account) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(account.address, color = fg, fontWeight = FontWeight.Medium); Text(if (account.address == activeAddress) "Currently active" else "Saved address • tap to switch", color = fg.copy(alpha = .65f), fontSize = 12.sp) }; IconButton(onClick = { onDelete(account) }) { Icon(Icons.Default.Delete, "Move to deleted") } } } } }
}

@Composable private fun EmptyTempMail(title: String, subtitle: String) { Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 12.sp) } } }

private fun showTempMailNotification(context: Context, count: Int, subject: String, address: String) {
    val channelId = "au_temp_mail_messages"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "Temp Mail messages", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "New temporary email messages" })
    val notification = NotificationCompat.Builder(context, channelId).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(if (count == 1) "New Temp Mail message" else "$count new Temp Mail messages").setContentText("$subject • $address").setStyle(NotificationCompat.BigTextStyle().bigText("$subject\nInbox: $address")).setAutoCancel(true).build()
    runCatching { NotificationManagerCompat.from(context).notify((address + subject).hashCode(), notification) }
}
