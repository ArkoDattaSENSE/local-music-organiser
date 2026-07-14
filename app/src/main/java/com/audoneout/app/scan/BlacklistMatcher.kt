package com.audoneout.app.scan

import com.audoneout.app.data.FolderBlacklistRuleEntity

object BlacklistMatcher {
    const val EXACT = "Exact"
    const val DESCENDANTS = "Descendants"
    const val FOLDER_NAME = "FolderName"
    const val PATH_PATTERN = "PathPattern"

    val defaultRules = listOf(
        "WhatsApp Audio",
        "Telegram Audio",
        "Voice recordings",
        "Recordings",
        "Ringtones",
        "Notifications",
        "Alarms",
        "Audio_Lab",
        "Dolby records"
    ).map { name ->
        FolderBlacklistRuleEntity(
            label = name,
            pattern = name,
            matchType = FOLDER_NAME,
            defaultSuggestion = true
        )
    }

    fun isExcluded(path: String, rules: List<FolderBlacklistRuleEntity>): Boolean {
        val normalizedPath = path.trim('/').lowercase()
        val folders = normalizedPath.split('/').filter { it.isNotBlank() }
        return rules.any { rule ->
            if (!rule.enabled) return@any false
            val pattern = rule.pattern.trim('/').lowercase()
            when (rule.matchType) {
                EXACT -> normalizedPath == pattern
                DESCENDANTS -> normalizedPath == pattern || normalizedPath.startsWith("$pattern/")
                PATH_PATTERN -> wildcardMatch(normalizedPath, pattern)
                else -> folders.any { it == pattern }
            }
        }
    }

    private fun wildcardMatch(value: String, pattern: String): Boolean {
        val regex = pattern
            .split('*')
            .joinToString(".*") { Regex.escape(it) }
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(value)
    }
}

