package com.yogeshpaliyal.deepr.caldav

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalDavVTodo(
    val uid: String,
    val link: String,
    val summary: String,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val profileName: String = "",
    val isFavourite: Boolean = false,
    val isPrivate: Boolean = false,
    val openedCount: Long = 0L,
    val thumbnail: String = "",
    val targetApp: String = "",
    val createdMillis: Long = 0L,
    val lastModifiedMillis: Long = 0L,
)

object VTodoMapper {
    private const val PRODID = "-//Deepr//CalDAV Sync//EN"
    private const val MAX_LINE_OCTETS = 75
    private val URL_REGEX = Regex("(https?://\\S+|[a-zA-Z][a-zA-Z0-9+.-]*://\\S+)")

    fun toIcs(item: CalDavVTodo): String {
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:$PRODID"
        lines += "BEGIN:VTODO"
        lines += "UID:${escapeText(item.uid)}"

        val effectiveStamp = if (item.lastModifiedMillis > 0L) item.lastModifiedMillis else System.currentTimeMillis()
        val formattedStamp = formatUtcTimestamp(effectiveStamp)
        lines += "DTSTAMP:$formattedStamp"
        lines += "LAST-MODIFIED:$formattedStamp"

        if (item.createdMillis > 0L) {
            lines += "CREATED:${formatUtcTimestamp(item.createdMillis)}"
        }

        val summaryText = item.summary.ifBlank { item.link }
        lines += "SUMMARY:${escapeText(summaryText)}"
        lines += "URL:${item.link}"
        lines += "STATUS:NEEDS-ACTION"

        if (item.description.isNotBlank()) {
            lines += "DESCRIPTION:${escapeText(item.description)}"
        }

        val cleanTags =
            item.categories
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        if (cleanTags.isNotEmpty()) {
            lines += "CATEGORIES:${cleanTags.joinToString(",") { escapeText(it) }}"
        }

        if (item.profileName.isNotBlank()) {
            lines += "X-DEEPR-PROFILE:${escapeText(item.profileName)}"
        }
        lines += "X-DEEPR-FAVOURITE:${if (item.isFavourite) "1" else "0"}"
        lines += "X-DEEPR-PRIVATE:${if (item.isPrivate) "1" else "0"}"
        if (item.openedCount > 0L) {
            lines += "X-DEEPR-OPENED:${item.openedCount}"
        }
        if (item.thumbnail.isNotBlank()) {
            lines += "X-DEEPR-THUMBNAIL:${escapeText(item.thumbnail)}"
        }
        if (item.targetApp.isNotBlank()) {
            lines += "X-DEEPR-TARGET-APP:${escapeText(item.targetApp)}"
        }

        lines += "END:VTODO"
        lines += "END:VCALENDAR"

        return lines.joinToString("\r\n") { foldLine(it) } + "\r\n"
    }

    fun parseIcs(icsContent: String): CalDavVTodo? {
        if (icsContent.isBlank()) return null
        val unfolded = unfoldLines(icsContent)
        var inVTodo = false

        var uid = ""
        var summary = ""
        var url = ""
        var description = ""
        val categories = mutableListOf<String>()
        var profileName = ""
        var isFavourite = false
        var isPrivate = false
        var openedCount = 0L
        var thumbnail = ""
        var targetApp = ""
        var createdMillis = 0L
        var lastModifiedMillis = 0L
        var dtStampMillis = 0L

        for (line in unfolded) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VTODO", ignoreCase = true)) {
                inVTodo = true
                continue
            }
            if (trimmed.equals("END:VTODO", ignoreCase = true)) {
                break
            }
            if (!inVTodo || trimmed.isEmpty()) continue

            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) continue

            val rawKey = line.substring(0, colonIndex)
            val key = rawKey.substringBefore(';').trim().uppercase(Locale.US)
            val value = line.substring(colonIndex + 1)

            when (key) {
                "UID" -> uid = unescapeText(value).trim()
                "SUMMARY" -> summary = unescapeText(value)
                "URL" -> url = value.trim()
                "DESCRIPTION" -> description = unescapeText(value)
                "CATEGORIES" -> {
                    val splitCats = splitEscapedList(value)
                    splitCats.forEach { cat ->
                        val unescaped = unescapeText(cat).trim()
                        if (unescaped.isNotEmpty() && unescaped !in categories) {
                            categories.add(unescaped)
                        }
                    }
                }
                "X-DEEPR-PROFILE" -> profileName = unescapeText(value).trim()
                "X-DEEPR-FAVOURITE" -> isFavourite = value.trim() == "1" || value.trim().equals("true", ignoreCase = true)
                "X-DEEPR-PRIVATE" -> isPrivate = value.trim() == "1" || value.trim().equals("true", ignoreCase = true)
                "X-DEEPR-OPENED" -> openedCount = value.trim().toLongOrNull() ?: 0L
                "X-DEEPR-THUMBNAIL" -> thumbnail = unescapeText(value).trim()
                "X-DEEPR-TARGET-APP" -> targetApp = unescapeText(value).trim()
                "CREATED" -> createdMillis = parseTimestamp(value)
                "LAST-MODIFIED" -> lastModifiedMillis = parseTimestamp(value)
                "DTSTAMP" -> dtStampMillis = parseTimestamp(value)
            }
        }

        val resolvedLink =
            url.ifBlank {
                extractUrlFallback(description) ?: extractUrlFallback(summary) ?: ""
            }
        if (uid.isBlank() || resolvedLink.isBlank()) {
            return null
        }

        val effectiveLastModified =
            when {
                lastModifiedMillis > 0L -> lastModifiedMillis
                dtStampMillis > 0L -> dtStampMillis
                createdMillis > 0L -> createdMillis
                else -> 0L
            }

        return CalDavVTodo(
            uid = uid,
            link = resolvedLink,
            summary = summary,
            description = description,
            categories = categories,
            profileName = profileName,
            isFavourite = isFavourite,
            isPrivate = isPrivate,
            openedCount = openedCount,
            thumbnail = thumbnail,
            targetApp = targetApp,
            createdMillis = createdMillis,
            lastModifiedMillis = effectiveLastModified,
        )
    }

    private fun extractUrlFallback(text: String): String? {
        if (text.isBlank()) return null
        return URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', ';')
    }

    internal fun unfoldLines(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val rawLines = normalized.split("\n")
        val result = mutableListOf<String>()
        for (line in rawLines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                val lastIdx = result.lastIndex
                result[lastIdx] = result[lastIdx] + line.substring(1)
            } else {
                result.add(line)
            }
        }
        return result
    }

    internal fun foldLine(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_LINE_OCTETS) return line

        val result = StringBuilder()
        var currentLineOctets = 0
        var isFirstLine = true

        for (char in line) {
            val charString = char.toString()
            val charBytes = charString.toByteArray(Charsets.UTF_8).size
            val limit = if (isFirstLine) MAX_LINE_OCTETS else MAX_LINE_OCTETS - 1
            if (currentLineOctets + charBytes > limit) {
                result.append("\r\n ")
                currentLineOctets = 0
                isFirstLine = false
            }
            result.append(char)
            currentLineOctets += charBytes
        }
        return result.toString()
    }

    internal fun escapeText(input: String): String =
        input
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")

    internal fun unescapeText(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\', ';', ',' -> sb.append(next)
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun splitEscapedList(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                current.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == ',') {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (escaped) {
            current.append('\\')
        }
        result.add(current.toString())
        return result
    }

    fun formatUtcTimestamp(millis: Long): String {
        val sdf =
            SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        return sdf.format(Date(millis))
    }

    fun formatDbTimestamp(millis: Long): String {
        val sdf =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        return sdf.format(Date(millis))
    }

    fun parseTimestamp(rawValue: String): Long {
        val value = rawValue.trim()
        if (value.isEmpty()) return 0L
        val utcFormat =
            SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val localDateTimeFormat =
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val dateOnlyFormat =
            SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        return runCatching {
            when {
                value.endsWith("Z", ignoreCase = true) -> utcFormat.parse(value.uppercase(Locale.US))?.time ?: 0L
                value.contains("T") -> localDateTimeFormat.parse(value)?.time ?: 0L
                value.length == 8 -> dateOnlyFormat.parse(value)?.time ?: 0L
                else -> 0L
            }
        }.getOrDefault(0L)
    }
}
