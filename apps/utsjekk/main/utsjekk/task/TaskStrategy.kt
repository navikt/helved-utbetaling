package utsjekk.task

interface TaskStrategy {
    suspend fun isApplicable(task: TaskDao): Boolean
    suspend fun execute(task: TaskDao)
}