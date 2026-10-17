package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.preferences.AppPreferences
import com.example.data.repository.NoteRepository
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Intelligent AdBlocker filter for Nickfinder and Web Name Generator
 */
object WebAdBlocker {
    private val AD_HOSTS = hashSetOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "tpc.googlesyndication.com",
        "www.googletagservices.com",
        "securepubads.g.doubleclick.net",
        "adclick.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "popads.net",
        "popcash.net",
        "adsterra.com",
        "propellerads.com",
        "monetag.com",
        "yandex.ru/ads",
        "inmobi.com",
        "unityads.unity3d.com",
        "vungle.com",
        "applovin.com",
        "scorecardresearch.com",
        "quantserve.com",
        "adcolony.com",
        "smartadserver.com",
        "casalemedia.com",
        "openx.net",
        "mgid.com",
        "revcontent.com",
        "adthrive.com",
        "mediavine.com",
        "yieldlove.com",
        "adform.net"
    )

    private val AD_KEYWORDS = arrayOf(
        "/ads/", "/ad/", "/ad-banner/", "/ad_banner/", "/popunder", "/popup-ad",
        "adsbygoogle", "doubleclick", "googlesyndication", "googleadservices",
        "adservice", "adnxs", "amazon-adsystem", "taboola", "outbrain",
        "popads", "adsterra", "propellerads", "monetag", "criteo", "adroll",
        "/show_ads.js", "googletagmanager.com/gtm.js"
    )

    fun isAdUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        for (host in AD_HOSTS) {
            if (lower.contains(host)) return true
        }
        for (kw in AD_KEYWORDS) {
            if (lower.contains(kw)) return true
        }
        return false
    }

    const val AD_BLOCK_CSS = """
        iframe[src*="ad"], iframe[id*="ad"], iframe[class*="ad"],
        div[id*="google_ads"], div[class*="google_ads"],
        div[id*="banner"], div[class*="banner"],
        .adsbygoogle, .ad-box, .ad-container, .advertisement,
        .ad-banner, .sponsor, .sponsored, .ad-wrapper, [data-ad], [data-ad-unit],
        #ad_container, #ad_frame, .ad-slot, .ad_slot,
        .native-ad, .popup-overlay, .ad-placement,
        div[id^="ad-"], div[id^="div-gpt-ad"],
        a[href*="googleads"], a[href*="doubleclick"] {
            display: none !important;
            visibility: hidden !important;
            height: 0px !important;
            max-height: 0px !important;
            pointer-events: none !important;
            opacity: 0 !important;
        }
    """

    const val AD_BLOCK_JS = """
        (function() {
            try {
                var style = document.getElementById('au-adblock-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'au-adblock-style';
                    style.type = 'text/css';
                    style.innerHTML = 'iframe[src*="ad"], iframe[id*="ad"], div[id*="google_ads"], div[class*="banner"], .adsbygoogle, .ad-box, .ad-container, .advertisement, .ad-banner, .ad-wrapper, [data-ad] { display: none !important; height: 0 !important; opacity: 0 !important; }';
                    document.head.appendChild(style);
                }
                var adSelectors = 'iframe[src*="ad"], .adsbygoogle, div[id*="google_ads"], div[id*="ad_"], div[class*="ad_"], [data-ad], div[id^="div-gpt-ad"], .ad-container, .advertisement';
                var elements = document.querySelectorAll(adSelectors);
                for (var i = 0; i < elements.length; i++) {
                    elements[i].remove();
                }
            } catch(e) {}
        })();
    """
}

class AuWebBridge(private val onCopyCaptured: (String) -> Unit) {
    @JavascriptInterface
    fun onNameCopied(name: String) {
        onCopyCaptured(name)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NameGeneratorScreen(
    repository: NoteRepository,
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Load permanently saved names and AdBlock setting from SharedPreferences
    val savedNamesPrefs = remember {
        context.getSharedPreferences("au_copied_names_prefs", Context.MODE_PRIVATE)
    }

    var isAdBlockEnabled by remember {
        mutableStateOf(savedNamesPrefs.getBoolean("adblock_enabled", true))
    }
    var blockedAdsCount by remember { mutableIntStateOf(0) }

    val savedNamesList = remember {
        val raw = savedNamesPrefs.getString("saved_names_list", "") ?: ""
        mutableStateListOf<String>().apply {
            if (raw.isNotBlank()) {
                addAll(raw.split(";;;").filter { it.isNotBlank() })
            }
        }
    }

    fun persistSavedNames() {
        val joined = savedNamesList.joinToString(";;;")
        savedNamesPrefs.edit().putString("saved_names_list", joined).apply()
    }

    // Automatic Direct Save into Room Notes Database
    fun autoSaveToNotes(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val allNotes = repository.allActiveNotes.firstOrNull() ?: emptyList()
                val existingNote = allNotes.find { it.title == "Nickfinder Nicknames" && !it.isTrash }

                if (existingNote != null) {
                    val currentContent = existingNote.content
                    if (!currentContent.contains(clean)) {
                        val updatedContent = if (currentContent.isBlank()) "• $clean" else "$currentContent\n• $clean"
                        repository.saveNote(
                            existingNote.copy(
                                content = updatedContent,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    repository.saveNote(
                        id = 0L,
                        title = "Nickfinder Nicknames",
                        content = "• $clean",
                        manualCategory = "Personal"
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Auto-saved to Notes: $clean", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // handle silently
            }
        }
    }

    fun processCapturedName(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            if (!savedNamesList.contains(clean)) {
                savedNamesList.add(0, clean)
                persistSavedNames()
            }
            autoSaveToNotes(clean)
        }
    }

    // Clipboard listener to auto-detect names copied from Nickfinder
    DisposableEffect(Unit) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text?.toString()
                if (!text.isNullOrBlank()) {
                    processCapturedName(text)
                }
            }
        }
        clipboardManager.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager.removePrimaryClipChangedListener(listener)
        }
    }

    var isRightDrawerOpen by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("generate") } // "generate" or "your_names"
    var isFullScreenYourNames by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoadingWeb by remember { mutableStateOf(true) }

    GlassBackground(isDarkMode = isDarkMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                text = "Name Generator",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color.White else Color(0xFF111111)
                            )
                            Text(
                                text = if (activeTab == "generate") {
                                    if (isAdBlockEnabled) "⚡ AdBlock ON • Auto-Save Active" else "Nickfinder (Auto-Save Active)"
                                } else {
                                    "Copied Names (${savedNamesList.size})"
                                },
                                fontSize = 11.sp,
                                color = CrimsonPrimary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick AdBlocker Status / Toggle Badge Button
                        GlassCard(
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(17.dp),
                            isDarkMode = isDarkMode,
                            onClick = {
                                val newState = !isAdBlockEnabled
                                isAdBlockEnabled = newState
                                savedNamesPrefs.edit().putBoolean("adblock_enabled", newState).apply()
                                webViewRef?.reload()
                                Toast.makeText(
                                    context,
                                    if (newState) "⚡ AdBlocker Shield Enabled" else "AdBlocker Disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isAdBlockEnabled) Icons.Default.Shield else Icons.Default.Block,
                                    contentDescription = "AdBlocker Status",
                                    tint = if (isAdBlockEnabled) Color(0xFF4CAF50) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAdBlockEnabled) "AdBlock ON" else "AdBlock OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdBlockEnabled) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }

                        if (activeTab == "generate") {
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = if (isDarkMode) Color.White else Color.Black
                                )
                            }
                        }

                        // Right Sidebar Toggle Button
                        GlassCard(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            isDarkMode = isDarkMode,
                            onClick = { isRightDrawerOpen = !isRightDrawerOpen }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = CrimsonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Main Content: Either WebView (Generate) or Your Names History View
                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeTab == "generate" && !isFullScreenYourNames) {
                        // In-App WebView for nickfinder.com with AdBlocker
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                                    addJavascriptInterface(AuWebBridge { captured ->
                                        processCapturedName(captured)
                                    }, "AndroidBridge")

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val url = request?.url?.toString()
                                            if (isAdBlockEnabled && WebAdBlocker.isAdUrl(url)) {
                                                blockedAdsCount++
                                                return WebResourceResponse(
                                                    "text/plain",
                                                    "UTF-8",
                                                    ByteArrayInputStream(ByteArray(0))
                                                )
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoadingWeb = false
                                            if (isAdBlockEnabled) {
                                                view?.evaluateJavascript(WebAdBlocker.AD_BLOCK_JS, null)
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            isLoadingWeb = false
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            isLoadingWeb = false
                                            view?.let { wv ->
                                                (wv.parent as? ViewGroup)?.removeView(wv)
                                                wv.destroy()
                                            }
                                            webViewRef = null
                                            return true
                                        }
                                    }
                                    webChromeClient = WebChromeClient()
                                    loadUrl("https://nickfinder.com/")
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLoadingWeb) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CrimsonPrimary)
                            }
                        }
                    } else {
                        // "Your Names" Fullscreen View (Simplified History without manual save-to-notes buttons)
                        YourNamesContentView(
                            savedNames = savedNamesList,
                            isDarkMode = isDarkMode,
                            onDeleteName = { name ->
                                savedNamesList.remove(name)
                                persistSavedNames()
                            }
                        )
                    }
                }
            }

            // Right Slide Sidebar Panel
            AnimatedVisibility(
                visible = isRightDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(if (isDarkMode) Color(0xF0181A20) else Color(0xF5F0F4F9))
                        .border(
                            1.dp,
                            if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000),
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Name Hub",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary
                            )
                            IconButton(onClick = { isRightDrawerOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = if (isDarkMode) Color.White else Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // AdBlocker Switch Feature Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            isDarkMode = isDarkMode,
                            onClick = {
                                val newState = !isAdBlockEnabled
                                isAdBlockEnabled = newState
                                savedNamesPrefs.edit().putBoolean("adblock_enabled", newState).apply()
                                webViewRef?.reload()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (isAdBlockEnabled) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "AdBlocker Shield",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = if (isDarkMode) Color.White else Color.Black
                                        )
                                        Text(
                                            text = if (isAdBlockEnabled) "Blocking popups & ads" else "Disabled",
                                            fontSize = 11.sp,
                                            color = if (isAdBlockEnabled) Color(0xFF4CAF50) else Color.Gray
                                        )
                                    }
                                }
                                Switch(
                                    checked = isAdBlockEnabled,
                                    onCheckedChange = { newState ->
                                        isAdBlockEnabled = newState
                                        savedNamesPrefs.edit().putBoolean("adblock_enabled", newState).apply()
                                        webViewRef?.reload()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4CAF50)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 1: Generate Names (Website)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            isDarkMode = isDarkMode,
                            onClick = {
                                activeTab = "generate"
                                isFullScreenYourNames = false
                                isRightDrawerOpen = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = CrimsonPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("1. Nickfinder Web", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDarkMode) Color.White else Color.Black)
                                    Text("Tap any name to auto-save to Notes", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 2: Copied Names History
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            isDarkMode = isDarkMode,
                            onClick = {
                                activeTab = "your_names"
                                isFullScreenYourNames = true
                                isRightDrawerOpen = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("2. Copied History", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDarkMode) Color.White else Color.Black)
                                    Text("${savedNamesList.size} captured names", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YourNamesContentView(
    savedNames: List<String>,
    isDarkMode: Boolean,
    onDeleteName: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

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
            Text(
                text = "Captured Nicknames (${savedNames.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color.White else Color.Black
            )
            Text(
                text = "✓ Auto-Saved to Notes",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = CrimsonPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (savedNames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No captured names yet.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap on any name in Nickfinder to auto-save directly to Notes!",
                        color = CrimsonPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedNames) { name ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isDarkMode = isDarkMode
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkMode) Color.White else Color(0xFF111111),
                                modifier = Modifier.weight(1f)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clip = android.content.ClipData.newPlainText("Nickname", name)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied: $name", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = CrimsonPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteName(name) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
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
