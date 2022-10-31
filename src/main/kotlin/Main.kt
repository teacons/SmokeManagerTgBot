import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


const val telegramToken = "5791814775:AAEmorTAxE_zEn55MXewyR-oXn1P5d3BMFg"

val mainTimer = Timer(true)
val cs = CoroutineScope(Dispatchers.Default + Job())

val mainTimerCoroutineScope = CoroutineScope(Dispatchers.Default + Job())

private val bot = telegramBot(telegramToken)

val startJobTask = mutableListOf<TimerTask>()

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

            onCommandWithArgs("start_work_time") { commonMessage, args ->
                handleTimeCommand(commonMessage, args.toList())
            }

        }.join()
    }.join()
}

suspend fun BehaviourContext.handleTimeCommand(commonMessage: CommonMessage<TextContent>, args: List<String>) {
    try {
        if (commonMessage.chat !is GroupChat || commonMessage.chat !is SupergroupChat) return
        if (args.size != 1) {
            sendMessage(commonMessage.chat, "Wrong args. Use: /start_work_time 17:00")
            return
        }

        val chat = getChat(commonMessage.chat)

        val timeToStart = parseStringTime(args.first())

        transaction { chat.startJobTime = timeToStart.toSecondOfDay() }

        updateStartJobTasks()

        sendMessage(commonMessage.chat, "Successful")
    } catch (e: Exception) {
        sendMessage(commonMessage.chat, "error: ${e.message}")
        e.printStackTrace()
    }
}

fun updateStartJobTasks() {
    startJobTask.cancel()


    transaction {
        Chat.all().filter { it.startJobTime != null }.map { it.id.value to it.startJobTime!! }.forEach {
            val task = object : TimerTask() {
                override fun run() {
                    try {
                        mainTimerCoroutineScope.launch { bot.sendMessage(ChatId(it.first), "Time to work bitches!") }
                    } catch (e: Exception) {
                    }
                }
            }
            startJobTask.add(task)
            mainTimer.schedule(task, LocalTime.ofSecondOfDay(it.second.toLong()).toDate())
        }
    }
}

fun parseStringTime(timeString: String): LocalTime {
    return LocalTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_TIME)
}

fun LocalTime.toDate(): Date {
    val timeNow = LocalTime.now()
    val dateTime = if (this.isAfter(timeNow)) this.atDate(LocalDate.now()) else this.atDate(LocalDate.now().plusDays(1))
    val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()

    return Date.from(instant)
}

fun MutableList<TimerTask>.cancel() {
    val toDelete = mutableListOf<TimerTask>()
    forEachIndexed { index, timerTask ->
        timerTask.cancel().also {
            println("$index : $it")
            if (it) toDelete.add(timerTask)
        }
    }
    this.removeAll(toDelete)
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


