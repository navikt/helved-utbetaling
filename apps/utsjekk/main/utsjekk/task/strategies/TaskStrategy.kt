package utsjekk.task.strategies

import utsjekk.task.TaskDao

interface TaskStrategy {
    suspend fun execute(task: TaskDao)
}
