package libs.utils

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/** example:
 * Rule("nokkelAvstemming") { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) },
 */
data class Rule(
    val field: String,
    val map: (value: String) -> Any
)

class CsvReader(val rules: List<Rule>) {
    companion object {
        inline fun <reified T : Any> parse(csv: String, rules: List<Rule> = emptyList()): List<T> {
            val reader = CsvReader(rules)
            return reader.parse(csv, T::class)
        }
    }

    fun <T : Any> parse(csv: String, clazz: KClass<T>): List<T> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val headers = lines.first().split(",").map { it.trim() }
        val constructor = clazz.primaryConstructor ?: error("Data class ${clazz.simpleName} must have a primary constructor")

        return lines.drop(1).map { line ->
            val values = parseCSVLine(line)
            val parameterMap = constructor.parameters.associateWith { param ->
                val columnIndex = headers.indexOfFirst {
                    it.equals(param.name, ignoreCase = true)
                }
                if (columnIndex == -1 && !param.isOptional) {
                    error("Column '${param.name}' not found in CSV headers: $headers")
                }
                val rawValue = values.getOrNull(columnIndex)?.trim()
                convertValue(rawValue, param)
            }
            constructor.callBy(parameterMap)
        }
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when(char) {
                '"' -> inQuotes = !inQuotes
                ',' if !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())

        return result
    }

    private fun convertValue(value: String?, param: KParameter): Any? {
        if (value.isNullOrBlank() || value.equals("NULL", ignoreCase = true)) {
            return if (param.type.isMarkedNullable) null
            else error("Cannot assign null to non-nullable parameter '${param.name}'")
        }

        val rule = rules.singleOrNull { rule -> rule.field == param.name }
        if (rule != null) { return rule.map(value) }

        return when (param.type.classifier) {
            String::class -> value
            Int::class -> value.toIntOrNull() ?: error("Cannot convert '$value' to Int for parameter '${param.name}'")
            Long::class -> value.toLongOrNull() ?: error("Cannot convert '$value' to Long for parameter '${param.name}'")
            Double::class -> value.toDoubleOrNull() ?: error("Cannot convert '$value' to Double for parameter '${param.name}'")
            Float::class -> value.toFloatOrNull() ?: error("Cannot convert '$value' to Float for parameter '${param.name}'")
            Boolean::class -> value.toBooleanStrictOrNull() ?: error("Cannot convert '$value' to Boolean for parameter '${param.name}'")
            else -> error("Unsupported type ${param.type} for parameter '${param.name}'")
        }
    }
}

