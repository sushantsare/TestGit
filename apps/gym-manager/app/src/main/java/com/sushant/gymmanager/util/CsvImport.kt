package com.sushant.gymmanager.util

import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.repository.PlanInput
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CsvImportResult(
    val rows: List<Pair<MemberEntity, PlanInput>>,
    /** One plain-language line per skipped row, e.g. "Row 4: WhatsApp number is missing". */
    val problems: List<String>
)

/**
 * Reads members from a CSV file saved from Excel or Google Sheets.
 * Columns (any order when a header row is present): Roll, Name, WhatsApp, Joining Date, From, To, Plan, Fee, Father, DOB.
 */
object CsvImport {
    val HEADER = listOf("Roll", "Name", "WhatsApp", "Joining Date", "From", "To", "Plan (months)", "Fee", "Father", "DOB")

    fun sample(): String = HEADER.joinToString(",") + "\n" +
        "101,Aarav Sharma,9876543210,01/11/2026,6:00 AM,7:00 AM,1,1000,Rajesh Sharma,12/04/2001\n" +
        "102,Priya Singh,9812345678,15/10/2026,18:00,19:00,3,2700,,\n"

    fun parse(text: String, settings: GymSettingsEntity, existingRolls: Set<String>, today: LocalDate = LocalDate.now()): CsvImportResult {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvImportResult(emptyList(), listOf("The file is empty"))
        val delimiter = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val table = lines.map { splitRow(it, delimiter) }
        val first = table.first().map { it.trim().lowercase(Locale.ENGLISH) }
        val hasHeader = first.any { it.contains("name") } && first.any { it.contains("roll") || it.contains("whatsapp") || it.contains("phone") }
        val col = columnMap(if (hasHeader) first else emptyList())
        val rows = mutableListOf<Pair<MemberEntity, PlanInput>>()
        val problems = mutableListOf<String>()
        val seen = existingRolls.map { it.lowercase(Locale.ENGLISH) }.toMutableSet()
        table.drop(if (hasHeader) 1 else 0).forEachIndexed { i, cells ->
            val rowNo = i + if (hasHeader) 2 else 1
            fun cell(key: String): String = col[key]?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val roll = cell("roll")
            val name = cell("name")
            val phoneDigits = cell("whatsapp").filter { it.isDigit() }
            val problem = when {
                roll.isEmpty() -> "roll number is missing"
                roll.length > 12 -> "roll number is longer than 12 characters"
                name.isEmpty() -> "name is missing"
                phoneDigits.length < 10 -> "WhatsApp number is missing or too short"
                roll.lowercase(Locale.ENGLISH) in seen -> "roll number $roll is already used"
                else -> null
            }
            if (problem != null) {
                problems += "Row $rowNo: $problem"
                return@forEachIndexed
            }
            val joined = parseDate(cell("joined")) ?: today
            val dob = parseDate(cell("dob"))?.toString().orEmpty()
            val from = parseTime(cell("from")) ?: LocalTime.of(6, 0)
            val to = parseTime(cell("to")) ?: from.plusHours(1)
            val months = cell("plan").filter { it.isDigit() }.toIntOrNull() ?: 1
            val planType = when (months) {
                1 -> PlanType.ONE_MONTH
                3 -> PlanType.THREE_MONTHS
                6 -> PlanType.SIX_MONTHS
                12 -> PlanType.TWELVE_MONTHS
                else -> PlanType.CUSTOM
            }
            val fee = cell("fee").replace(",", "").replace("₹", "").trim().toDoubleOrNull()
                ?: settings.defaultFeeFor(planType) ?: settings.fee1Month * months
            val fromText = DateUtils.toHHmm(from.hour, from.minute)
            val toText = DateUtils.toHHmm(to.hour, to.minute)
            seen += roll.lowercase(Locale.ENGLISH)
            rows += MemberEntity(
                rollNumber = roll,
                fullName = name,
                whatsappNumber = phoneDigits,
                dateOfJoining = joined.toString(),
                dateOfBirth = dob,
                fatherName = cell("father").ifBlank { null },
                shift = DateUtils.determineShift(fromText, settings.morningShiftStart, settings.morningShiftEnd),
                fromTime = fromText,
                toTime = toText
            ) to PlanInput(planType, if (planType == PlanType.CUSTOM) months.coerceAtLeast(1) else 0, 0, fee, true)
        }
        return CsvImportResult(rows, problems)
    }

    private fun columnMap(header: List<String>): Map<String, Int> {
        if (header.isEmpty()) {
            return mapOf("roll" to 0, "name" to 1, "whatsapp" to 2, "joined" to 3, "from" to 4, "to" to 5, "plan" to 6, "fee" to 7, "father" to 8, "dob" to 9)
        }
        val out = mutableMapOf<String, Int>()
        header.forEachIndexed { i, h ->
            val key = when {
                h.contains("roll") -> "roll"
                h.contains("father") -> "father"
                h.contains("name") -> "name"
                h.contains("whatsapp") || h.contains("phone") || h.contains("mobile") -> "whatsapp"
                h.contains("join") -> "joined"
                h.contains("dob") || h.contains("birth") -> "dob"
                h.startsWith("from") || h.contains("start") -> "from"
                h == "to" || h.startsWith("to ") || h.contains("end") -> "to"
                h.contains("plan") || h.contains("month") -> "plan"
                h.contains("fee") || h.contains("amount") -> "fee"
                else -> null
            }
            if (key != null && key !in out) out[key] = i
        }
        return out
    }

    /** Splits one CSV line, honouring "quoted, values". */
    fun splitRow(line: String, delimiter: Char = ','): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private val dateFormats = listOf("yyyy-MM-dd", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d/M/yy", "d-MMM-yyyy", "d MMM yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parseDate(raw: String): LocalDate? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        for (f in dateFormats) {
            runCatching { return LocalDate.parse(t, f) }
        }
        return null
    }

    fun parseTime(raw: String): LocalTime? {
        val t = raw.trim().uppercase(Locale.ENGLISH).replace(".", ":")
        if (t.isEmpty()) return null
        val m = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(AM|PM)?$""").find(t) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3]) {
            "AM" -> if (h == 12) h = 0
            "PM" -> if (h < 12) h += 12
        }
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }
}
