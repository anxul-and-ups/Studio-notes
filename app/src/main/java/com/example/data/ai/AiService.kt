package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConversationTurn(
    val role: String, // "user" or "bot"/"model"
    val content: String
)

/** Result of a tool-enabled AU Bot turn: either a normal text reply, or a request
 * to run one of the small set of safe app-command functions defined below. */
sealed class AiActionResult {
    data class Text(val text: String) : AiActionResult()
    data class FunctionCall(val name: String, val args: JSONObject) : AiActionResult()
}

/**
 * AU Bot brain. Talks to Gemini with a proper system instruction, real multi-turn
 * history, document / image input and optional app-function calling.
 *
 * IMPORTANT: failures are reported honestly (bad key, quota, offline...) — this class
 * never replaces a failed request with a canned answer any more.
 */
class AiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private class ApiFailure(val userMessage: String, val retryable: Boolean, val modelMissing: Boolean = false) : Exception(userMessage)

    private fun baseSystemPrompt(): String = """
        You are AU Bot, a smart, friendly AI assistant inside the AU Notes app (built by student developer Anshul).
        You behave like a modern conversational assistant (Claude / Gemini / ChatGPT):
        - Actually read and answer what the user asked. Never reply with a generic menu of things you can do.
        - Reply in the same language and style the user writes in (English, Hindi, or Hinglish written in Roman script).
        - Be direct and natural. Short questions get short answers; complex ones get structured, step-by-step answers.
        - Use Markdown: short paragraphs, bullet lists, and fenced code blocks with a language tag for code.
        - When the user attaches a document or image, read it carefully and base your answer on its real content
          (summaries, questions about it, extraction, rewriting, translation). Quote or refer to specific parts.
        - Use earlier messages in this conversation for context; follow-up questions refer to them.
        - If something is unclear, make a sensible assumption and say so, or ask ONE short clarifying question.
        - Be honest: if you don't know, say so instead of guessing.
    """.trimIndent()

    private fun textContent(role: String, text: String) = JSONObject().apply {
        put("role", role)
        put("parts", JSONArray().put(JSONObject().put("text", text)))
    }

    /** Gemini needs alternating roles starting with "user"; merge consecutive same-role turns. */
    private fun buildContents(history: List<ConversationTurn>, latest: JSONObject): JSONArray {
        val cleaned = mutableListOf<Pair<String, String>>()
        for (turn in history.takeLast(24)) {
            if (turn.content.isBlank()) continue
            val role = if (turn.role == "bot" || turn.role == "model") "model" else "user"
            if (cleaned.isNotEmpty() && cleaned.last().first == role) {
                cleaned[cleaned.lastIndex] = role to (cleaned.last().second + "\n\n" + turn.content)
            } else cleaned.add(role to turn.content)
        }
        while (cleaned.isNotEmpty() && cleaned.first().first != "user") cleaned.removeAt(0)
        // the latest message is a user turn — a trailing user history turn would make two in a row
        if (cleaned.isNotEmpty() && cleaned.last().first == "user") cleaned.removeAt(cleaned.lastIndex)
        val arr = JSONArray()
        cleaned.forEach { (r, t) -> arr.put(textContent(r, t)) }
        arr.put(latest)
        return arr
    }

    private fun describeHttpError(code: Int, body: String?): ApiFailure {
        val apiMsg = try { JSONObject(body ?: "").optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
        return when (code) {
            400 -> if (apiMsg?.contains("API key", ignoreCase = true) == true)
                ApiFailure("Your Gemini API key looks invalid. Open Settings → API Room and check the key.", false)
            else ApiFailure("Gemini could not process this request. ${apiMsg.orEmpty()}".trim(), false)
            401, 403 -> ApiFailure("Gemini rejected your API key (or it has no access to this model). Open Settings → API Room and check the key.", false)
            404 -> ApiFailure("The selected model isn't available for your key.", false, modelMissing = true)
            429 -> ApiFailure("Gemini rate limit / free quota reached. Wait a minute and try again, or switch model in API Room.", true)
            500, 502, 503, 504 -> ApiFailure("Gemini is busy right now. Please try again in a moment.", true)
            else -> ApiFailure("Gemini error (HTTP $code). ${apiMsg.orEmpty()}".trim(), false)
        }
    }

    /** One HTTP call. Returns the parsed candidate parts or throws [ApiFailure]. */
    private fun callGemini(model: String, apiKey: String, body: JSONObject): JSONArray {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw ApiFailure("Couldn't reach Gemini. Check your internet connection and try again.", true)
        }
        response.use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) throw describeHttpError(resp.code, text)
            if (text.isNullOrBlank()) throw ApiFailure("Gemini returned an empty response.", true)
            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val block = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw ApiFailure(
                    if (block.isNotBlank()) "Gemini blocked this request ($block). Try rephrasing." else "Gemini didn't return an answer. Try again.",
                    false
                )
            }
            val cand = candidates.getJSONObject(0)
            val parts = cand.optJSONObject("content")?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                val reason = cand.optString("finishReason")
                throw ApiFailure(
                    if (reason == "SAFETY") "Gemini's safety filter stopped this answer. Try rephrasing." else "Gemini sent an empty answer. Try again.",
                    reason != "SAFETY"
                )
            }
            return parts
        }
    }

    /** Calls Gemini with retry on transient errors and fallback when the chosen model isn't available. */
    private suspend fun generate(apiKey: String, model: String, body: JSONObject): JSONArray {
        val key = apiKey.trim()
        if (key.isBlank()) throw ApiFailure("No API key set. Open Settings → API Room and add your Gemini API key.", false)
        val candidatesModels = listOf(model.ifBlank { "gemini-2.0-flash" }, "gemini-2.0-flash", "gemini-1.5-flash").distinct()
        var lastFailure: ApiFailure? = null
        for (m in candidatesModels) {
            var attempt = 0
            while (attempt < 3) {
                try {
                    return callGemini(m, key, body)
                } catch (f: ApiFailure) {
                    lastFailure = f
                    if (f.modelMissing) break // try next model
                    if (!f.retryable) throw f
                    attempt++
                    if (attempt < 3) delay(1200L * attempt)
                    else throw f
                }
            }
        }
        throw lastFailure ?: ApiFailure("Something went wrong talking to Gemini.", true)
    }

    private fun joinText(parts: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optBoolean("thought", false)) continue
            if (p.has("text")) sb.append(p.getString("text"))
        }
        return sb.toString().trim()
    }

    /**
     * AU Bot with real app-command access, restricted to a small, explicit set of
     * safe functions (search/create/move/delete-to-trash). Destructive functions are
     * executed by the CALLER only after the user confirms.
     */
    suspend fun generateWithTools(
        prompt: String,
        apiKey: String,
        model: String = "gemini-2.0-flash",
        noteContext: String? = null,
        history: List<ConversationTurn> = emptyList(),
        allowEditCurrentNote: Boolean = false
    ): AiActionResult = withContext(Dispatchers.IO) {
        val system = buildString {
            append(baseSystemPrompt())
            append("\n\nYou can also control the app through functions: search_notes, create_note, move_note_to_folder, delete_note, create_folder, delete_folder, open_folder.")
            append(" Call a function ONLY when the user clearly asks for that app action (e.g. \"find my Java note\", \"create a note from this\", \"make a folder called Travel\").")
            append(" For questions, explanations, writing, coding help or chatting, just answer in text — do NOT call a function.")
            append(" move_note_to_folder and delete_note need a note_id: get it with search_notes first if you don't have it.")
            if (allowEditCurrentNote) {
                append("\nYou are running INSIDE the editor of the note in [Current Note Context]. When the user asks you to rewrite, clean up, correct, format, translate, summarize into, or insert something into THIS note, call edit_current_note with the note's COMPLETE new text (not a diff).")
            }
            if (!noteContext.isNullOrBlank()) {
                append("\n\n[Current Note Context]\n").append(noteContext.take(30000))
            }
        }

        val functionDeclarations = JSONArray()
            .put(functionDecl("search_notes", "Search the user's notes by a keyword or topic and return matching titles.",
                listOf(FnParam("query", "STRING", "Keyword or topic to search for", true))))
            .put(functionDecl("create_note", "Create a brand-new note with the given title and content.",
                listOf(
                    FnParam("title", "STRING", "Short title for the note", true),
                    FnParam("content", "STRING", "The note's body text", true),
                    FnParam("category", "STRING", "One of: General, Code, API, Media, Personal", false)
                )))
            .put(functionDecl("move_note_to_folder", "Move an existing note (by its id) into a different folder. Needs user confirmation.",
                listOf(
                    FnParam("note_id", "NUMBER", "The id of the note to move, from a prior search_notes result", true),
                    FnParam("folder", "STRING", "Target folder: All Notes, APIs Keys, Code, Media, or Personal", true)
                )))
            .put(functionDecl("delete_note", "Move an existing note (by its id) to the recycle bin. Needs user confirmation.",
                listOf(FnParam("note_id", "NUMBER", "The id of the note to delete, from a prior search_notes result", true))))
            .put(functionDecl("create_folder", "Create a new custom folder with the given name.",
                listOf(FnParam("name", "STRING", "Name for the new folder", true))))
            .put(functionDecl("delete_folder", "Delete an existing custom folder by name.",
                listOf(FnParam("name", "STRING", "Name of the custom folder to delete", true))))
            .put(functionDecl("open_folder", "Navigate to and open a folder by name.",
                listOf(FnParam("name", "STRING", "Name of the folder to open", true))))
        if (allowEditCurrentNote) {
            functionDeclarations.put(functionDecl("edit_current_note",
                "Replace the content of the note currently open in the editor with new text.",
                listOf(FnParam("new_content", "STRING", "The complete new body text for the current note", true))))
        }

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", buildContents(history, textContent("user", prompt)))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations)))
            put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 4096))
        }

        try {
            val parts = generate(apiKey, model, body)
            for (i in 0 until parts.length()) {
                val fn = parts.getJSONObject(i).optJSONObject("functionCall")
                if (fn != null && fn.optString("name").isNotBlank()) {
                    return@withContext AiActionResult.FunctionCall(fn.getString("name"), fn.optJSONObject("args") ?: JSONObject())
                }
            }
            val text = joinText(parts)
            AiActionResult.Text(text.ifBlank { "I couldn't come up with an answer. Could you rephrase that?" })
        } catch (f: ApiFailure) {
            AiActionResult.Text("⚠️ ${f.userMessage}")
        } catch (e: Exception) {
            AiActionResult.Text("⚠️ Something went wrong: ${e.message ?: "unknown error"}")
        }
    }

    private data class FnParam(val name: String, val type: String, val description: String, val required: Boolean)

    private fun functionDecl(name: String, description: String, params: List<FnParam>): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        for (p in params) {
            properties.put(p.name, JSONObject().apply {
                put("type", p.type)
                put("description", p.description)
            })
            if (p.required) required.put(p.name)
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                put("required", required)
            })
        }
    }

    /**
     * Plain conversational path, used whenever the user sends a document / image.
     * The attached text and/or page images are sent together with the question so the
     * model answers from the real file content.
     */
    suspend fun generateResponse(
        prompt: String,
        apiKey: String,
        model: String = "gemini-2.0-flash",
        noteContext: String? = null,
        attachmentContent: String? = null,
        history: List<ConversationTurn> = emptyList(),
        attachmentImagesBase64: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val system = buildString {
            append(baseSystemPrompt())
            if (!noteContext.isNullOrBlank()) append("\n\n[Current Note Context]\n").append(noteContext.take(30000))
        }

        val latestText = buildString {
            if (!attachmentContent.isNullOrBlank()) {
                append("The user attached this document. Read it fully and use it to answer.\n")
                append("=== DOCUMENT START ===\n").append(attachmentContent).append("\n=== DOCUMENT END ===\n\n")
            } else if (attachmentImagesBase64.isNotEmpty()) {
                append("The user attached ${attachmentImagesBase64.size} image(s)/page(s). Look at them carefully (read any text in them) and use them to answer.\n\n")
            }
            append("User message: ").append(prompt)
        }

        val latest = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray().put(JSONObject().put("text", latestText))
            for (img in attachmentImagesBase64.take(8)) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/png").put("data", img)))
            }
            put("parts", parts)
        }

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", buildContents(history, latest))
            put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 4096))
        }

        try {
            val text = joinText(generate(apiKey, model, body))
            text.ifBlank { "I couldn't come up with an answer. Could you rephrase that?" }
        } catch (f: ApiFailure) {
            "⚠️ ${f.userMessage}"
        } catch (e: Exception) {
            "⚠️ Something went wrong: ${e.message ?: "unknown error"}"
        }
    }

    /**
     * Real "Auto Detect Models" — calls Gemini's own ListModels endpoint with the
     * user's key and returns the models that actually support generateContent,
     * instead of a fixed guessed list. Throws on invalid key / network failure so
     * the caller can show a real error instead of fake results.
     */
    suspend fun listAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = apiKey.trim()
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Enter an API key first"))
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                val message = when (response.code) {
                    400, 403 -> "This API key was rejected — double-check it's correct"
                    404 -> "Model list endpoint not found for this key's project"
                    else -> "Could not reach Gemini (HTTP ${response.code})"
                }
                return@withContext Result.failure(Exception(message))
            }

            val json = JSONObject(body)
            val modelsArray = json.optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                val m = modelsArray.getJSONObject(i)
                val supported = m.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = (0 until (supported?.length() ?: 0)).any {
                    supported?.optString(it) == "generateContent"
                }
                if (supportsGenerate) {
                    val fullName = m.optString("name", "") // e.g. "models/gemini-2.0-flash"
                    val shortName = fullName.substringAfterLast('/')
                    if (shortName.isNotBlank()) names.add(shortName)
                }
            }

            if (names.isEmpty()) {
                Result.failure(Exception("This key works, but no generateContent-capable models were returned"))
            } else {
                Result.success(names.distinct())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
