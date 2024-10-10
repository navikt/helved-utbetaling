package utsjekk.task

import libs.task.TaskDao

interface TaskStrategy {
    suspend fun isApplicable(task: TaskDao): Boolean
    suspend fun execute(task: TaskDao)
}