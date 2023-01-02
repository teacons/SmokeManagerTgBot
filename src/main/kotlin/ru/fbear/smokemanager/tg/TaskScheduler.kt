package ru.fbear.smokemanager.tg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.fbear.smokemanager.tg.models.Chat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class TaskScheduler {

    private val taskSchedulerThread = TaskSchedulerThread()

    fun start() {
        taskSchedulerThread.start()
    }

    fun findTasksByChat(chatId: Long): List<Task> {
        return taskSchedulerThread.findTasksByChatId(chatId)
    }

    fun removeTasks(taskType: TaskType, chat: Chat) {
        findTasksByChat(chat.id.value).filter { it.type == taskType }.let { taskList ->
            taskList.forEach { taskSchedulerThread.removeTask(it) }
        }
    }

    fun addTask(task: Task) {
        taskSchedulerThread.addTask(task)
    }
}

private class TaskSchedulerThread : Thread() {

    private val tasks = mutableListOf<Task>()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    override fun run() {
        while (true) {
            sleep(900)
            if (tasks.isEmpty()) continue

            val currentDayOfWeek = LocalDate.now().dayOfWeek
            val currentTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
            tasks.filter {
                currentDayOfWeek in it.daysOfWeek
                        && currentTime == it.time
                        && it.taskStatus == TaskStatus.NotStarted
            }.onEach { task ->
                task.taskStatus = TaskStatus.InWork
                coroutineScope.launch { task.job.invoke() }
                    .invokeOnCompletion { task.taskStatus = TaskStatus.Completed }
            }

            if (currentTime == LocalTime.of(0, 0)) {
                tasks.forEach { it.taskStatus = TaskStatus.NotStarted }
            }
        }
    }

    fun addTask(task: Task) {
        tasks.add(task)
    }

    fun removeTask(task: Task) {
        tasks.remove(task)
    }

    fun findTasksByChatId(chatId: Long): List<Task> {
        return tasks.filter { it.chatId == chatId }
    }

}

sealed class TaskType {
    object StartDay : TaskType()

    object EndDay : TaskType()

    object SmokeTimeStart : TaskType()

    object SmokeTimeEnd : TaskType()

    companion object {

        fun TaskType.isDayCycleType(): Boolean {
            return this == StartDay || this == EndDay
        }

        fun TaskType.isSmokeCycleType(): Boolean {
            return this == SmokeTimeStart || this == SmokeTimeEnd
        }
    }
}

data class Task(
    val chatId: Long,
    val time: LocalTime,
    val type: TaskType,
    val daysOfWeek: List<DayOfWeek>,
    val job: suspend () -> Unit,
) {
    var taskStatus: TaskStatus = TaskStatus.NotStarted

    fun cancel() {
        taskStatus = TaskStatus.Cancelled
    }
}

enum class TaskStatus {
    Completed, NotStarted, InWork, Cancelled
}