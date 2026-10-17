package com.example.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * App-managed file organisation: virtual folders, which file lives in which folder, and which
 * files are locked. Everything is persisted in SharedPreferences, keyed by the file's absolute
 * path inside the app's own storage, so it survives restarts.
 *
 * Android does not let an app move arbitrary files that belong to other apps / other storage
 * locations, so [importIntoAppStorage] makes a byte-for-byte copy inside the app and all further
 * moves, renames and locks act on that copy. The original is never changed or deleted.
 */
class FileManagerStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("au_file_manager", Context.MODE_PRIVATE)

    val documentsDir: File get() = File(appContext.filesDir, "documents").apply { mkdirs() }

    // ---------------- folders ----------------
    fun folders(): List<String> = readArray("folders")

    fun createFolder(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean.equals("All", true) || clean.equals("Unfiled", true)) return false
        val list = folders()
        if (list.any { it.equals(clean, true) }) return false
        writeArray("folders", list + clean)
        return true
    }

    fun deleteFolder(name: String) {
        writeArray("folders", folders().filter { it != name })
        val map = assignments().filterValues { it != name }
        writeMap(map)
    }

    // ---------------- assignments ----------------
    private fun assignments(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(prefs.getString("assign", "{}") ?: "{}")
            o.keys().forEach { k -> out[k] = o.getString(k) }
        } catch (_: Exception) {}
        return out
    }

    private fun writeMap(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("assign", o.toString()).apply()
    }

    fun folderOf(path: String): String? = assignments()[path]

    fun assign(path: String, folder: String?) {
        val map = assignments().toMutableMap()
        if (folder == null) map.remove(path) else map[path] = folder
        writeMap(map)
    }

    // ---------------- locks ----------------
    fun isLocked(path: String): Boolean = readArray("locked").contains(path)

    fun setLocked(path: String, locked: Boolean) {
        val set = readArray("locked").toMutableList()
        set.remove(path)
        if (locked) set.add(path)
        writeArray("locked", set)
    }

    /** Called after a rename so folder + lock state follow the file. */
    fun onRenamed(oldPath: String, newPath: String) {
        folderOf(oldPath)?.let { assign(newPath, it); assign(oldPath, null) }
        if (isLocked(oldPath)) { setLocked(oldPath, false); setLocked(newPath, true) }
    }

    fun onDeleted(path: String) {
        assign(path, null)
        setLocked(path, false)
    }

    fun isAppManaged(file: File): Boolean = try {
        file.canonicalPath.startsWith(appContext.filesDir.canonicalPath)
    } catch (_: Exception) { false }

    // ---------------- import / copy ----------------
    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        return f
    }

    private fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    } catch (_: Exception) { null }

    /** Copies a picked content:// document (e.g. a PDF) into app storage, unchanged. */
    fun importIntoAppStorage(uri: Uri, folder: String? = null): File? {
        return try {
            val name = (displayName(uri) ?: "imported_${System.currentTimeMillis()}").replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val dest = uniqueFile(documentsDir, name)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            if (folder != null) assign(dest.absolutePath, folder)
            dest
        } catch (_: Exception) {
            null
        }
    }

    /** Copies a file that lives outside app storage (e.g. Downloads) into the app. */
    fun importFile(source: File, folder: String? = null): File? = try {
        val dest = uniqueFile(documentsDir, source.name)
        source.inputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        if (folder != null) assign(dest.absolutePath, folder)
        dest
    } catch (_: Exception) { null }

    // ---------------- helpers ----------------
    private fun readArray(key: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val a = JSONArray(prefs.getString(key, "[]") ?: "[]")
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (_: Exception) {}
        return out
    }

    private fun writeArray(key: String, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        prefs.edit().putString(key, a.toString()).apply()
    }
}
