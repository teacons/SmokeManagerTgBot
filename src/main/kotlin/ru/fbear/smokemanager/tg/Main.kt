package ru.fbear.smokemanager.tg

import END_DAY_MESSAGE
import END_FRIDAY_MESSAGE
import START_DAY_MESSAGE
import dev.inmo.tgbotapi.bot.TelegramBot
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import ru.fbear.smokemanager.tg.models.Chat
import ru.fbear.smokemanager.tg.models.Chats
import java.time.DayOfWeek
import java.time.LocalTime


val telegramToken = System.getenv("SMOKE_MANAGER_TG_BOT_TOKEN") ?: throw IllegalArgumentException()

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
                addJobTask(TaskType.StartDay, it.id.value, it.startJobTime!!, bot)
            if (it.endJobTime != null)
                addJobTask(TaskType.EndDay, it.id.value, it.endJobTime!!, bot)
            if (it.endJobFridayTime != null)
                addJobTask(TaskType.EndFriday, it.id.value, it.endJobFridayTime!!, bot)
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

        val time = args.first().toLocalTimeOrNull()

        if (time == null) {
            reply(commonMessage, "Wrong args. Use: ${taskType.command} 17:00")
            return
        }

        when (taskType) {
            TaskType.StartDay -> {
                transaction { chat.startJobTime = time }
                addJobTask(TaskType.StartDay, chat.id.value, time, bot)
            }

            TaskType.EndDay -> {
                transaction { chat.endJobTime = time }
                addJobTask(TaskType.EndDay, chat.id.value, time, bot)
            }

            TaskType.EndFriday -> {
                transaction { chat.endJobFridayTime = time }
                addJobTask(TaskType.EndFriday, chat.id.value, time, bot)
            }
        }
        sendMessage(commonMessage.chat, "Successful")
    } catch (e: Exception) {
        sendMessage(commonMessage.chat, "error: ${e.message}")
        e.printStackTrace()
    }
}

fun addJobTask(type: TaskType, chatId: Long, time: LocalTime, bot: TelegramBot) {
    val (dayOfWeek, message) =
        when (type) {
            TaskType.StartDay -> {
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                ) to START_DAY_MESSAGE
            }

            TaskType.EndDay -> {
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY
                ) to END_DAY_MESSAGE
            }

            TaskType.EndFriday -> {
                listOf(
                    DayOfWeek.FRIDAY
                ) to END_FRIDAY_MESSAGE
            }
        }
    taskScheduler.addJobTask(
        Task(
            chatId = chatId,
            time = time,
            type = type,
            daysOfWeek = dayOfWeek,
            job = { bot.sendMessage(ChatId(chatId), message) })
    )
}

fun getChat(chatTg: dev.inmo.tgbotapi.types.chat.Chat): Chat {
    return chatTg.id.chatId.let { id ->
        transaction { findChat(id) ?: createChat(id) }
    }
}

fun findChat(id: Long): Chat? = transaction { Chat.findById(id) }

fun createChat(id: Long): Chat = transaction { Chat.new(id) { } }



