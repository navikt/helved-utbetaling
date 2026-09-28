package speiderhytta.audit

data class TaskReference(val repository: String, val number: Long)

private val taskTrailer = Regex(
    pattern = "(?im)^Task:\\s*(?:https://github\\.com/)?([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?:/issues/|#)([0-9]+)\\s*$",
)

private val issueNumber = Regex("(?<![A-Za-z0-9_])#([0-9]+)\\b")

fun taskReference(message: String, defaultRepository: String? = null): TaskReference? {
    taskTrailer.find(message)?.let { match ->
        return TaskReference(repository = match.groupValues[1], number = match.groupValues[2].toLong())
    }
    val repository = defaultRepository ?: return null
    val taskText = message.lineSequence()
        .filterNot { it.startsWith("Merge pull request ", ignoreCase = true) }
        .joinToString("\n")
    val numbers = issueNumber.findAll(taskText).map { it.groupValues[1].toLong() }.distinct().toList()
    return numbers.singleOrNull()?.let { TaskReference(repository, it) }
}
