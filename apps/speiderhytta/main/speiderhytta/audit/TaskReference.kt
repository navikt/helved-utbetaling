package speiderhytta.audit

data class TaskReference(val repository: String, val number: Long)

private val issueNumber = Regex("(?<![A-Za-z0-9_])#([0-9]+)\\b")

fun taskReference(message: String, repository: String): TaskReference {
    val taskText = message.lineSequence()
        .filterNot { it.startsWith("Merge pull request ", ignoreCase = true) }
        .joinToString("\n")
    val number = issueNumber.find(taskText)
    return TaskReference(repository, number?.groupValues?.get(1)?.toLong() ?: 511L)
}
