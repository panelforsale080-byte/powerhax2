package com.accel.loader

import java.time.LocalDate

object MenuKeyList {

    enum class Status { VALID, EXPIRED, INVALID, EMPTY, OFFLINE }

    data class Entry(val name: String, val key: String, val until: LocalDate)

    data class Result(val status: Status, val name: String, val until: String)

    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        for (raw in text.lineSequence()) {
            val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size < 3) continue
            val until = parseDate(parts.last()) ?: continue
            entries += Entry(parts.first(), parts[parts.size - 2], until)
        }
        return entries
    }

    fun check(text: String, typed: String, today: LocalDate): Result {
        val token = typed.trim()
        if (token.isEmpty()) return Result(Status.EMPTY, "", "")
        val entry = parse(text).firstOrNull { it.key.equals(token, ignoreCase = true) || token.split(Regex("\\s+")).any { word -> word.equals(it.key, ignoreCase = true) } }
            ?: return Result(Status.INVALID, "", "")
        val until = "%04d-%02d-%02d".format(entry.until.year, entry.until.monthValue, entry.until.dayOfMonth)
        val status = if (today.isAfter(entry.until)) Status.EXPIRED else Status.VALID
        return Result(status, entry.name, until)
    }

    fun label(result: Result): String {
        return when (result.status) {
            Status.VALID -> "${result.name} valid until ${result.until}"
            Status.EXPIRED -> "${result.name} expired ${result.until}"
            Status.INVALID -> "Invalid key"
            Status.EMPTY -> "enter license key first"
            Status.OFFLINE -> "Key list unavailable"
        }
    }

    private fun parseDate(text: String): LocalDate? {
        val parts = text.split('-', '/')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}
