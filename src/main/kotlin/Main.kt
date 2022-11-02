import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onChatEvent
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onNewChatMembers
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.chat.GroupChat
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.SupergroupChat
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import models.Chat
import models.Chats
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter


const val telegramToken = "5791814775:AAEmorTAxE_zEn55MXewyR-oXn1P5d3BMFg"

val cs = CoroutineScope(Dispatchers.Default + Job())

val bot = telegramBot(telegramToken)

val taskScheduler = TaskScheduler()

object DbSettings {
    val db by lazy {
        Database.connect("jdbc:sqlite:data.db", "org.sqlite.JDBC")
    }
}

suspend fun main() {

    TransactionManager.defaultDatabase = DbSettings.db

    transaction {
        SchemaUtils.create(Chats)
        commit()
    }

    taskScheduler.start()

    transaction {
        Chat.all().forEach {
            if (it.startJobTime != null)
                addJobTask(
                    Task(
                        chatId = it.id.value,
                        time = it.startJobTime!!,
                        type = TaskType.StartDay,
                        daysOfWeek = listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        ),
                        job = { bot.sendMessage(ChatId(it.id.value), START_DAY_MESSAGE) }
                    )
                )
            if (it.endJobTime != null)
                addJobTask(
                    Task(
                        chatId = it.id.value,
                        time = it.endJobTime!!,
                        type = TaskType.EndDay,
                        daysOfWeek = listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY
                        ),
                        job = { bot.sendMessage(ChatId(it.id.value), END_DAY_MESSAGE) }
                    )
                )
            if (it.endJobFridayTime != null)
                addJobTask(
                    Task(
                        chatId = it.id.value,
                        time = it.endJobFridayTime!!,
                        type = TaskType.EndFriday,
                        daysOfWeek = listOf(
                            DayOfWeek.FRIDAY
                        ),
                        job = { bot.sendMessage(ChatId(it.id.value), END_FRIDAY_MESSAGE) }
                    )
                )
        }
    }

    cs.launch {
        bot.buildBehaviourWithLongPolling() {
            val me = bot.getMe()
            onNewChatMembers {
                if (it.chat !is GroupChat || it.chat !is SupergroupChat) return@onNewChatMembers
                if (me !in it.chatEvent.members) return@onNewChatMembers
                val chatPreferences = findChat(it.chat.id.chatId)
                if (chatPreferences == null) {
                    createChat(it.chat.id.chatId)
                    sendMessage(it.chat, "Hello smokers!")
                } else {
                    sendMessage(it.chat, "I am back smokers!")
                }
            }
            onChatEvent {
                println(it.chatEvent.toString())
            }
            onCommand("start") {
                if (it.chat is PrivateChat)
                    sendMessage(it.chat, "Hello to private")
                else
                    sendMessage(it.chat, "hello to chat")
            }

            onCommandWithArgs("start_day") { commonMessage, args ->
                handleTimeCommand(commonMessage, args.toList(), TaskType.StartDay)
            }

            onCommandWithArgs("end_day") { commonMessage, args ->
                handleTimeCommand(commonMessage, args.toList(), TaskType.EndDay)
            }

            onCommandWithArgs("end_friday") { commonMessage, args ->
                handleTimeCommand(commonMessage, args.toList(), TaskType.EndFriday)
            }

        }.join()
    }.join()
}

suspend fun BehaviourContext.handleTimeCommand(
    commonMessage: CommonMessage<TextContent>,
    args: List<String>,
    taskType: TaskType,
) {
    try {
        if (commonMessage.chat !is GroupChat || commonMessage.chat !is SupergroupChat) return
        if (args.size != 1) {
            reply(commonMessage, "Wrong args. Use: ${taskType.command} 17:00")
            return
        }

        val chat = getChat(commonMessage.chat)

        val time = parseStringTime(args.first())

        when (taskType) {
            TaskType.StartDay -> {
                transaction { chat.startJobTime = time }
                addJobTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.StartDay,
                        daysOfWeek = listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        ),
                        job = { sendMessage(commonMessage.chat, START_DAY_MESSAGE) })
                )
            }

            TaskType.EndDay -> {
                transaction { chat.endJobTime = time }
                addJobTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.EndDay,
                        daysOfWeek = listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY
                        ),
                        job = { sendMessage(commonMessage.chat, END_DAY_MESSAGE) })
                )
            }

            TaskType.EndFriday -> {
                transaction { chat.endJobFridayTime = time }
                addJobTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.EndFriday,
                        daysOfWeek = listOf(DayOfWeek.FRIDAY),
                        job = { sendMessage(commonMessage.chat, END_FRIDAY_MESSAGE) })
                )
            }
        }
        sendMessage(commonMessage.chat, "Successful")
    } catch (e: Exception) {
        sendMessage(commonMessage.chat, "error: ${e.message}")
        e.printStackTrace()
    }
}

fun addJobTask(task: Task) {
    val chatTasks = taskScheduler.findTasksByChat(task.chatId)
    chatTasks.filter { it.type == task.type }.let {
        when {
            it.size > 1 -> throw IllegalStateException("too much task for ${task.type}")
            it.size == 1 -> taskScheduler.removeTask(it.first())
        }
    }
    taskScheduler.addTask(task)
}

fun parseStringTime(timeString: String): LocalTime {
    //todo(сделать проверку правильности формата времени в строке)
    return LocalTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_TIME)
}

fun getChat(chatTg: dev.inmo.tgbotapi.types.chat.Chat): Chat {
    return chatTg.id.chatId.let { id ->
        transaction { findChat(id) ?: createChat(id) }
    }
}

fun findChat(id: Long): Chat? = transaction { Chat.findById(id) }

fun createChat(id: Long): Chat {
    return transaction { Chat.new(id) { } }
}


