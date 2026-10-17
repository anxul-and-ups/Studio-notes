package com.example.data.model

object AutoClassifier {
    private val apiKeyRegex = Regex(
        """(AQ\.[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{35}|sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{20,}|bearer\s+[A-Za-z0-9._~+/-]+=*|key\s*=\s*['"]?[A-Za-z0-9_-]{16,}['"]?)""",
        RegexOption.IGNORE_CASE
    )

    private val mediaRegex = Regex(
        """(https?://[^\s]+?\.(jpg|jpeg|png|gif|webp|svg|mp4|mov|webm)(\?[^\s]*)?|https?://(www\.)?(youtube\.com|youtu\.be|ibb\.co|unsplash\.com)/[^\s]+)""",
        RegexOption.IGNORE_CASE
    )

    private val codeKeywords = listOf(
        "<!doctype", "<html>", "<script", "</script>", "<div", "<style",
        "import ", "export ", "function ", "def ", "class ", "fun ", "val ", "var ",
        "return ", "public static void", "#include", "const ", "let ", "console.log",
        "print(", "System.out.print", "package com.", "SELECT * FROM", "curl ", "npm install"
    )

    private val personalKeywords = listOf(
        "diary", "dear diary", "personal", "secret", "private", "confidential",
        "my password", "my pin", "bank", "account number", "journal", "memories"
    )

    fun detectCategory(title: String, content: String): String {
        val combined = "$title\n$content"
        val lower = combined.lowercase()

        // 1. Check API Key
        if (apiKeyRegex.containsMatchIn(combined) || combined.contains("API key", ignoreCase = true) && combined.contains("AQ.", ignoreCase = false)) {
            return "API"
        }

        // 2. Check Code
        val codeMatches = codeKeywords.count { lower.contains(it) }
        if (codeMatches >= 2 || lower.contains("<!doctype") || lower.contains("<html>") || lower.contains("def ") || lower.contains("fun ") || combined.contains("```")) {
            return "Code"
        }

        // 3. Check Media
        if (mediaRegex.containsMatchIn(combined)) {
            return "Media"
        }

        // 4. Check Personal
        if (personalKeywords.any { lower.contains(it) }) {
            return "Personal"
        }

        return "General"
    }

    fun isProbablyApiKey(text: String): Boolean {
        return apiKeyRegex.containsMatchIn(text) || text.startsWith("AQ.") || text.startsWith("AIza")
    }

    fun maskApiKey(text: String): String {
        return apiKeyRegex.replace(text) { matchResult ->
            val key = matchResult.value
            if (key.length > 8) {
                key.take(4) + "••••••••••••••••" + key.takeLast(4)
            } else {
                "••••••••"
            }
        }
    }
}
