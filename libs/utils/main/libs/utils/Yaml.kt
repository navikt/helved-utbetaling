package libs.utils

sealed interface YamlNode {
    data class Scalar(val value: String) : YamlNode
    data class Sequence(val items: List<YamlNode>) : YamlNode
    data class Mapping(val entries: Map<String, YamlNode>) : YamlNode
}

fun YamlNode.Mapping.string(key: String): String? = (entries[key] as? YamlNode.Scalar)?.value
fun YamlNode.Mapping.double(key: String): Double? = string(key)?.toDoubleOrNull()
fun YamlNode.Mapping.mapping(key: String): YamlNode.Mapping? = entries[key] as? YamlNode.Mapping
fun YamlNode.Mapping.sequence(key: String): List<YamlNode> =
    (entries[key] as? YamlNode.Sequence)?.items ?: emptyList()

fun parseYaml(text: String): YamlNode {
    val lines = text.lines()
    val parser = YamlParser(lines)
    return parser.parse()
}

private class YamlParser(private val lines: List<String>) {
    private var pos = 0

    fun parse(): YamlNode {
        skipBlankAndComments()
        if (pos >= lines.size) return YamlNode.Mapping(emptyMap())
        val indent = currentIndent()
        return if (strippedLine().startsWith("- ")) {
            parseSequence(indent)
        } else {
            parseMapping(indent)
        }
    }

    private fun parseMapping(baseIndent: Int): YamlNode.Mapping {
        val entries = linkedMapOf<String, YamlNode>()
        while (pos < lines.size) {
            skipBlankAndComments()
            if (pos >= lines.size) break
            val indent = currentIndent()
            if (indent < baseIndent) break
            if (indent > baseIndent) break

            val line = strippedLine()
            val colonIdx = findKeyColon(line)
            if (colonIdx < 0) { pos++; continue }

            val key = line.substring(0, colonIdx).trim()
            val afterColon = line.substring(colonIdx + 1).trim()

            val value = when {
                afterColon == "|" -> parseBlockScalar(baseIndent)
                afterColon.isEmpty() -> { pos++; parseNestedValue(baseIndent) }
                else -> { pos++; YamlNode.Scalar(unquote(afterColon)) }
            }
            entries[key] = value
        }
        return YamlNode.Mapping(entries)
    }

    private fun parseSequence(baseIndent: Int): YamlNode.Sequence {
        val items = mutableListOf<YamlNode>()
        while (pos < lines.size) {
            skipBlankAndComments()
            if (pos >= lines.size) break
            val indent = currentIndent()
            if (indent < baseIndent) break
            if (indent > baseIndent) break

            val line = strippedLine()
            if (!line.startsWith("- ")) break

            val afterDash = line.removePrefix("- ")
            val itemIndent = baseIndent + 2
            val colonIdx = findKeyColon(afterDash)

            if (colonIdx < 0 || afterDash.startsWith("\"") || afterDash.startsWith("'")) {
                // Bare scalar item
                pos++
                items.add(YamlNode.Scalar(unquote(afterDash)))
            } else {
                // Mapping item — first key is on same line as dash
                val key = afterDash.substring(0, colonIdx).trim()
                val afterColon = afterDash.substring(colonIdx + 1).trim()

                val firstValue = when {
                    afterColon == "|" -> parseBlockScalar(baseIndent)
                    afterColon.isEmpty() -> { pos++; parseNestedValue(itemIndent) }
                    else -> { pos++; YamlNode.Scalar(unquote(afterColon)) }
                }

                // Collect remaining keys at itemIndent level
                val entries = linkedMapOf<String, YamlNode>(key to firstValue)
                while (pos < lines.size) {
                    skipBlankAndComments()
                    if (pos >= lines.size) break
                    val nextIndent = currentIndent()
                    if (nextIndent < itemIndent) break
                    if (nextIndent > itemIndent) break

                    val nextLine = strippedLine()
                    if (nextLine.startsWith("- ")) break
                    val nextColon = findKeyColon(nextLine)
                    if (nextColon < 0) { pos++; continue }

                    val nextKey = nextLine.substring(0, nextColon).trim()
                    val nextAfter = nextLine.substring(nextColon + 1).trim()

                    entries[nextKey] = when {
                        nextAfter == "|" -> parseBlockScalar(itemIndent)
                        nextAfter.isEmpty() -> { pos++; parseNestedValue(itemIndent) }
                        else -> { pos++; YamlNode.Scalar(unquote(nextAfter)) }
                    }
                }
                items.add(YamlNode.Mapping(entries))
            }
        }
        return YamlNode.Sequence(items)
    }

    private fun parseBlockScalar(parentIndent: Int): YamlNode.Scalar {
        pos++
        val collected = mutableListOf<String>()
        val blockIndent = if (pos < lines.size && lines[pos].isNotBlank()) {
            lines[pos].indentLevel()
        } else {
            parentIndent + 2
        }
        while (pos < lines.size) {
            val raw = lines[pos]
            if (raw.isBlank()) {
                collected.add("")
                pos++
                continue
            }
            val indent = raw.indentLevel()
            if (indent < blockIndent) break
            collected.add(raw.substring(blockIndent))
            pos++
        }
        // Trim trailing empty lines
        while (collected.isNotEmpty() && collected.last().isEmpty()) {
            collected.removeLast()
        }
        return YamlNode.Scalar(collected.joinToString("\n"))
    }

    private fun parseNestedValue(parentIndent: Int): YamlNode {
        skipBlankAndComments()
        if (pos >= lines.size) return YamlNode.Mapping(emptyMap())
        val childIndent = currentIndent()
        if (childIndent <= parentIndent) return YamlNode.Scalar("")
        return if (strippedLine().startsWith("- ")) {
            parseSequence(childIndent)
        } else {
            parseMapping(childIndent)
        }
    }

    private fun skipBlankAndComments() {
        while (pos < lines.size) {
            val line = lines[pos]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                pos++
            } else {
                break
            }
        }
    }

    private fun currentIndent(): Int = if (pos < lines.size) lines[pos].indentLevel() else 0
    private fun strippedLine(): String = if (pos < lines.size) stripComment(lines[pos].trimStart()) else ""

    private fun String.indentLevel(): Int {
        var i = 0
        while (i < length && this[i] == ' ') i++
        return i
    }
}

/**
 * Find the colon that separates key from value.
 * Skips colons inside quoted strings and colons that are part of values like `http://`.
 * The key colon must be followed by a space or end-of-line.
 */
private fun findKeyColon(line: String): Int {
    var inDouble = false
    var inSingle = false
    for (i in line.indices) {
        when (line[i]) {
            '"' -> if (!inSingle) inDouble = !inDouble
            '\'' -> if (!inDouble) inSingle = !inSingle
            ':' -> if (!inDouble && !inSingle) {
                if (i + 1 >= line.length || line[i + 1] == ' ') return i
            }
        }
    }
    return -1
}

/**
 * Strip inline comments (` # ...`) unless inside quotes.
 */
private fun stripComment(line: String): String {
    var inDouble = false
    var inSingle = false
    for (i in line.indices) {
        when (line[i]) {
            '"' -> if (!inSingle) inDouble = !inDouble
            '\'' -> if (!inDouble) inSingle = !inSingle
            '#' -> if (!inDouble && !inSingle && i > 0 && line[i - 1] == ' ') {
                return line.substring(0, i).trimEnd()
            }
        }
    }
    return line
}

private fun unquote(s: String): String = when {
    s.length >= 2 && s.startsWith('"') && s.endsWith('"') -> s.substring(1, s.length - 1)
    s.length >= 2 && s.startsWith('\'') && s.endsWith('\'') -> s.substring(1, s.length - 1)
    else -> s
}
