package io.github.vvb2060.ims.model

import androidx.compose.ui.graphics.Color
import java.util.Date

data class LogEntry(
    val level: LogLevel = LogLevel.INFO,
    val time: String = "",
    val content: String = "",
    val tag: String? = "",
    val pid: Int? = 0,
    val tid: Int? = 0,
    val raw: String = "",
) {
    companion object {
        fun parseLog(line: String): LogEntry {
            var i = 0
            val n = line.length

            fun skipSpaces() {
                while (i < n && line[i] == ' ') i++
            }

            fun readWord(): String {
                val start = i
                while (i < n && line[i] != ' ') i++
                return line.substring(start, i)
            }

            skipSpaces()
            val date = readWord()
            skipSpaces()
            val timeStr = readWord()
            skipSpaces()
            val pidStr = readWord()
            skipSpaces()
            val tidStr = readWord()
            skipSpaces()
            val levelStr = readWord()
            skipSpaces()
            val tagStart = i
            while (i < n && line[i] != ':') i++
            val tag = if (i < n) line.substring(tagStart, i) else ""
            i++ // skip ':'
            val content = if (i < n) line.substring(i).trimStart() else ""

            val level = when (levelStr) {
                "V", "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E", "F" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }

            if (content.isEmpty())
                return LogEntry(time = Date().toString(), content = line)

            return LogEntry(
                level = level,
                time = "$date $timeStr",
                content = content,
                tag = tag,
                pid = pidStr.toIntOrNull(),
                tid = tidStr.toIntOrNull(),
                raw = line,
            )
        }
    }
}

enum class LogLevel(val tag: String, val bgColor: Color, val priority: Int) {
    DEBUG("DEBUG", Color(0xFF4CAF50), 1),
    INFO("INFO", Color(0xFF2196F3), 2),
    WARN("WARN", Color(0xFFFFC107), 3),
    ERROR("ERROR", Color(0xFFF44336), 4),
    ;

    fun isLevelEnabled(filter: LogLevel): Boolean = priority >= filter.priority
}
