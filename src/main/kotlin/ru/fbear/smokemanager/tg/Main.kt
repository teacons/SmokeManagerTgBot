package ru.fbear.smokemanager.tg

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onNewChatMembers
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.chat.GroupChat
import dev.inmo.tgbotapi.types.chat.SupergroupChat
import dev.inmo.tgbotapi.types.message.MarkdownV2ParseMode
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.abstracts.Message
import dev.inmo.tgbotapi.types.message.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import ru.fbear.smokemanager.tg.TaskType.Companion.isDayCycleType
import ru.fbear.smokemanager.tg.TaskType.Companion.isSmokeCycleType
import ru.fbear.smokemanager.tg.models.Chat
import ru.fbear.smokemanager.tg.models.Chats
import ru.fbear.smokemanager.tg.models.DayCycleStartEnd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

object DbSettings {
    val db by lazy {
        Database.connect("jdbc:sqlite:data.db", "org.sqlite.JDBC")
    }
}

suspend fun main() {
    val tgToken = System.getenv("SMOKE_MANAGER_TG_BOT_TOKEN")
    if (tgToken == null) {
        println("Telegram token not provided")
        return
    }
    Bot(tgToken).launch()
}

class Bot(telegramToken: String) {

    private val cs = CoroutineScope(Dispatchers.Default + Job())

    private val bot = telegramBot(telegramToken)

    private val taskScheduler = TaskScheduler()

    private val messageToDelete = mutableListOf<Message>()

    suspend fun launch() {

        TransactionManager.defaultDatabase = DbSettings.db

        transaction {
            SchemaUtils.create(Chats)
            commit()
        }

        taskScheduler.start()

        taskScheduler.addTask(
            Task(
                chatId = null,
                time = LocalTime.of(0, 0),
                type = TaskType.CleanMessages,
                daysOfWeek = DayOfWeek.values().toList(),
                job = { messageToDelete.forEach { it.delete(bot) } }
            )
        )

        transaction {
            Chat.all().forEach { chat: Chat ->
                addTaskByDayCycleTimetable(chat, bot)

                addSmokeTasksByDayCycleTimetable(chat, bot)
            }
        }

        bot.setMyCommands(commandList)

        cs.launch {
            bot.buildBehaviourWithLongPolling {
                val me = bot.getMe()
                onNewChatMembers {
                    if (it.chat !is GroupChat && it.chat !is SupergroupChat) return@onNewChatMembers
                    if (me !in it.chatEvent.members) return@onNewChatMembers
                    val chatPreferences = findChat(it.chat.id.chatId)
                    if (chatPreferences == null) {
                        createChat(it.chat.id.chatId)
                        sendMessage(it.chat, BOT_ADDED_FIRST_TIME).also { mes -> messageToDelete.add(mes) }
                    } else {
                        sendMessage(it.chat, BOT_ADDED).also { mes -> messageToDelete.add(mes) }
                    }
                }

                onCommand(ListDayCycle) { message ->
                    if (message.chat !is GroupChat && message.chat !is SupergroupChat) return@onCommand
                    buildString {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        taskScheduler.findTasksByChat(message.chat.id.chatId)
                            .filter { it.type.isDayCycleType() }.also { dayCycleList ->

                                appendLine("```")
                                appendLine("$DAY_CYCLE_STRING:")
                                val leftAlignFormat = "| %-7s | %-5s | %-5s |"

                                appendLine("+${"-".repeat(7)}+${"-".repeat(7)}+${"-".repeat(7)}+")
                                appendLine(leftAlignFormat.format(DAY_STRING, START_STRING, END_STRING))
                                appendLine("+${"-".repeat(7)}+${"-".repeat(7)}+${"-".repeat(7)}+")

                                DayOfWeek.values().forEach { day ->
                                    val dayStart =
                                        dayCycleList.filter { it.type == TaskType.StartDay }
                                            .find { day in it.daysOfWeek }?.time
                                    val dayEnd =
                                        dayCycleList.filter { it.type == TaskType.EndDay }
                                            .find { day in it.daysOfWeek }?.time
                                    appendLine(
                                        leftAlignFormat.format(
                                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                            dayStart?.format(timeFormatter),
                                            dayEnd?.format(timeFormatter)
                                        )
                                    )
                                }
                                appendLine("+${"-".repeat(7)}+${"-".repeat(7)}+${"-".repeat(7)}+")
                                appendLine("```")
                            }
                    }.also {
                        sendMessage(message.chat, it, MarkdownV2ParseMode).also { mes -> messageToDelete.add(mes) }
                    }
                }

                onCommand(ListTodaySmokeCycle) { message ->
                    if (message.chat !is GroupChat && message.chat !is SupergroupChat) return@onCommand
                    buildString {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        appendLine("```")
                        taskScheduler.findTasksByChat(message.chat.id.chatId)
                            .filter { it.type.isSmokeCycleType() && LocalDate.now().dayOfWeek in it.daysOfWeek }
                            .takeIf { it.isNotEmpty() }
                            ?.sortedBy { it.time }?.also { smokeTasks ->
                                val starts = smokeTasks.filter { it.type == TaskType.SmokeTimeStart }
                                val ends = smokeTasks.filter { it.type == TaskType.SmokeTimeEnd }

                                buildList {
                                    starts.forEachIndexed { index, task ->
                                        add(task to ends.getOrNull(index))
                                    }
                                }.forEachIndexed { index, smokeTime ->
                                    append("$index. ")
                                    append(smokeTime.first.time.format(timeFormatter))
                                    append(" - ")
                                    appendLine(smokeTime.second?.time?.format(timeFormatter))
                                }
                            } ?: appendLine(EMPTY_LIST)
                        appendLine("```")
                    }.also {
                        sendMessage(message.chat, it, MarkdownV2ParseMode).also { mes -> messageToDelete.add(mes) }
                    }
                }

                onCommandWithArgs(Monday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Monday)
                }
                onCommandWithArgs(Tuesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Tuesday)
                }
                onCommandWithArgs(Wednesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Wednesday)
                }
                onCommandWithArgs(Thursday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Thursday)
                }
                onCommandWithArgs(Friday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Friday)
                }
                onCommandWithArgs(Saturday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Saturday)
                }
                onCommandWithArgs(Sunday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), Sunday)
                }

                onCommand("regen") {
                    addTaskByDayCycleTimetable(getChat(it.chat), bot)
                    addSmokeTasksByDayCycleTimetable(getChat(it.chat), bot)
                }

                onCommandWithArgs(SetSmokeDuration) { commonMessage, args ->
                    handleSmokeTimeCommands(commonMessage, args.toList(), SetSmokeDuration)
                }
                onCommandWithArgs(SetSmokeInterval) { commonMessage, args ->
                    handleSmokeTimeCommands(commonMessage, args.toList(), SetSmokeInterval)
                }

            }.join()
        }.join()
    }

    private suspend fun BehaviourContext.handleSmokeTimeCommands(
        commonMessage: CommonMessage<TextContent>,
        args: List<String>,
        command: BotCommand,
    ) {
        try {
            if (commonMessage.chat !is GroupChat && commonMessage.chat !is SupergroupChat) return
            if (args.size != 1) {
                reply(commonMessage, "Wrong args. Use: ${command.command} 20").also { mes -> messageToDelete.add(mes) }
                return
            }

            val chat = getChat(commonMessage.chat)

            val time = args.first().toIntOrNull()

            if (time == null) {
                reply(commonMessage, "Wrong args. Use: ${command.command} 20").also { mes -> messageToDelete.add(mes) }
                return
            }

            transaction {
                when (command) {
                    SetSmokeDuration -> chat.smokeDuration = time
                    SetSmokeInterval -> chat.smokeInterval = time
                    else -> throw IllegalArgumentException("Impossible")
                }
            }

            addSmokeTasksByDayCycleTimetable(chat, bot)

            sendMessage(commonMessage.chat, SUCCESSFUL_STRING).also { mes -> messageToDelete.add(mes) }
        } catch (e: Exception) {
            sendMessage(commonMessage.chat, "$ERROR_STRING: ${e.message}").also { mes -> messageToDelete.add(mes) }
            e.printStackTrace()
        }
    }

    private suspend fun BehaviourContext.handleDayCycleCommand(
        commonMessage: CommonMessage<TextContent>,
        args: List<String>,
        taskType: BotCommand,
    ) {
        try {
            if (commonMessage.chat !is GroupChat && commonMessage.chat !is SupergroupChat) return
            if (args.size != 2) {
                reply(
                    commonMessage,
                    "Wrong args. Use: ${taskType.command} 17:00 20:00"
                ).also { mes -> messageToDelete.add(mes) }
                return
            }

            val chat = getChat(commonMessage.chat)

            val timeStart = args.first().toLocalTimeOrNull()
            val timeEnd = args[1].toLocalTimeOrNull()

            if (timeStart?.isAfter(timeEnd) == true) {
                reply(
                    commonMessage,
                    "Wrong args. Start of the day should be before end of the day"
                ).also { mes -> messageToDelete.add(mes) }
                return
            }

            transaction {
                when (taskType) {
                    Monday -> chat.apply {
                        startMonday = timeStart
                        endMonday = timeEnd
                    }

                    Tuesday -> chat.apply {
                        startTuesday = timeStart
                        endTuesday = timeEnd
                    }

                    Wednesday -> chat.apply {
                        startWednesday = timeStart
                        endWednesday = timeEnd
                    }

                    Thursday -> chat.apply {
                        startThursday = timeStart
                        endThursday = timeEnd
                    }

                    Friday -> chat.apply {
                        startFriday = timeStart
                        endFriday = timeEnd
                    }

                    Saturday -> chat.apply {
                        startSaturday = timeStart
                        endSaturday = timeEnd
                    }

                    Sunday -> chat.apply {
                        startSunday = timeStart
                        endSunday = timeEnd
                    }

                    else -> throw IllegalArgumentException("Impossible")
                }
                commit()
            }
            addTaskByDayCycleTimetable(chat, bot)
            addSmokeTasksByDayCycleTimetable(chat, bot)
            sendMessage(commonMessage.chat, SUCCESSFUL_STRING).also { mes -> messageToDelete.add(mes) }
        } catch (e: Exception) {
            sendMessage(commonMessage.chat, "$ERROR_STRING: ${e.message}").also { mes -> messageToDelete.add(mes) }
            e.printStackTrace()
        }
    }

    private fun addTaskByDayCycleTimetable(chat: Chat, bot: TelegramBot) {
        val starts = chat.getDayCycleTimetable().getMap().toList()
            .filter { it.second != DayCycleStartEnd.Empty && it.second.startDay != null }
            .groupingBy { it.second.startDay }.fold(
                { _, _ -> mutableListOf<DayOfWeek>() },
                { _, accumulator, element -> accumulator.also { it.add(element.first) } }
            )

        val ends = chat.getDayCycleTimetable().getMap().toList()
            .filter { it.second != DayCycleStartEnd.Empty && it.second.endDay != null }
            .groupingBy { it.second.endDay }.fold(
                { _, _ -> mutableListOf<DayOfWeek>() },
                { _, accumulator, element -> accumulator.also { it.add(element.first) } }
            )

        taskScheduler.removeTasks(TaskType.StartDay, chat)
        taskScheduler.removeTasks(TaskType.EndDay, chat)

        starts.forEach {
            taskScheduler.addTask(
                Task(
                    chatId = chat.id.value,
                    time = it.key!!,
                    type = TaskType.StartDay,
                    daysOfWeek = it.value,
                    job = {
                        bot.sendMessage(ChatId(chat.id.value), START_DAY_MESSAGES.random())
                            .also { mes -> messageToDelete.add(mes) }
                    })
            )
        }

        ends.forEach {
            taskScheduler.addTask(
                Task(
                    chatId = chat.id.value,
                    time = it.key!!,
                    type = TaskType.EndDay,
                    daysOfWeek = it.value,
                    job = {
                        bot.sendMessage(
                            ChatId(chat.id.value),
                            if (LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY) END_FRIDAY_MESSAGES.random()
                            else END_DAY_MESSAGES.random()
                        ).also { mes -> messageToDelete.add(mes) }
                    })
            )
        }
    }


    private fun addSmokeTasksByDayCycleTimetable(chat: Chat, bot: TelegramBot) {

        val dayCycleTimetable = chat.getDayCycleTimetable()

        val groupsSameDays = dayCycleTimetable.getMap().toList()
            .filter { it.second != DayCycleStartEnd.Empty && it.second.startDay != null && it.second.endDay != null }
            .groupingBy { it.second }.fold(
                { _, _ -> mutableListOf<DayOfWeek>() },
                { _, accumulator, element -> accumulator.also { it.add(element.first) } }
            )

        taskScheduler.removeTasks(TaskType.SmokeTimeStart, chat)
        taskScheduler.removeTasks(TaskType.SmokeTimeEnd, chat)

        groupsSameDays.forEach {
            var a = it.key.startDay!!
            val endTime = it.key.endDay

            val startSmoke = mutableListOf<LocalTime>()
            val endSmoke = mutableListOf<LocalTime>()

            while (a.isBefore(endTime)) {
                a = a.plus(chat.smokeInterval.toLong(), ChronoUnit.MINUTES)
                startSmoke.add(a)
                a = a.plus(chat.smokeDuration.toLong(), ChronoUnit.MINUTES)
                if (a.isAfter(endTime)) break
                endSmoke.add(a)
            }

            startSmoke.forEach { time ->
                taskScheduler.addTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.SmokeTimeStart,
                        daysOfWeek = it.value,
                        job = {
                            bot.sendMessage(ChatId(chat.id.value), SMOKE_TIME_START_MESSAGES.random())
                                .also { mes -> messageToDelete.add(mes) }
                        }
                    )
                )
            }

            endSmoke.forEach { time ->
                taskScheduler.addTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.SmokeTimeEnd,
                        daysOfWeek = it.value,
                        job = {
                            bot.sendMessage(ChatId(chat.id.value), SMOKE_TIME_END_MESSAGES.random())
                                .also { mes -> messageToDelete.add(mes) }
                        }
                    )
                )
            }
        }
    }

    private fun getChat(chatTg: dev.inmo.tgbotapi.types.chat.Chat): Chat {
        return chatTg.id.chatId.let { id ->
            transaction { findChat(id) ?: createChat(id) }
        }
    }

    private fun findChat(id: Long): Chat? = transaction { Chat.findById(id) }

    private fun createChat(id: Long): Chat = transaction { Chat.new(id) { } }

}

