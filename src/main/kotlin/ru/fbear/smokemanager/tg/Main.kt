package ru.fbear.smokemanager.tg

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
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
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.SupergroupChat
import dev.inmo.tgbotapi.types.message.MarkdownV2ParseMode
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
import ru.fbear.smokemanager.tg.TaskType.Companion.isDayCycleType
import ru.fbear.smokemanager.tg.TaskType.Companion.isSmokeCycleType
import ru.fbear.smokemanager.tg.models.Chat
import ru.fbear.smokemanager.tg.models.Chats
import ru.fbear.smokemanager.tg.models.DayCycleStartEnd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    suspend fun launch() {

        TransactionManager.defaultDatabase = DbSettings.db

        transaction {
            SchemaUtils.create(Chats)
            commit()
        }

        taskScheduler.start()

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

                onCommand("start") {
                    if (it.chat is PrivateChat)
                        sendMessage(it.chat, "Hello to private")
                    else
                        sendMessage(it.chat, "hello to chat")
                }

                onCommand(ListDayCycle) { message ->
                    if (message.chat !is GroupChat || message.chat !is SupergroupChat) return@onCommand
                    buildString {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        taskScheduler.findTasksByChat(message.chat.id.chatId)
                            .filter { it.type.isDayCycleType() }.also { dayCycleList ->

                                appendLine("```")
                                appendLine("Day cycle:")
                                val leftAlignFormat = "| %-15s | %-5s | %-5s |"

                                appendLine("+-----------------+-------+-------+")
                                appendLine("|   Day of Week   | Start |  End  |")
                                appendLine("+-----------------+-------+-------+")
                                DayOfWeek.values().forEach { day ->
                                    val dayStart =
                                        dayCycleList.filter { it.type == TaskType.StartDay }
                                            .find { day in it.daysOfWeek }?.time
                                    val dayEnd =
                                        dayCycleList.filter { it.type == TaskType.EndDay }
                                            .find { day in it.daysOfWeek }?.time
                                    appendLine(
                                        leftAlignFormat.format(
                                            day.name, dayStart?.format(timeFormatter), dayEnd?.format(timeFormatter)
                                        )
                                    )
                                }
                                appendLine("+-----------------+-------+-------+")
                                appendLine("```")
                            }
                    }.also {
                        sendMessage(message.chat, it, MarkdownV2ParseMode)
                    }
                }

                onCommand(ListTodaySmokeCycle) { message ->
                    if (message.chat !is GroupChat || message.chat !is SupergroupChat) return@onCommand
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
                            } ?: appendLine("Nothing to show")
                        appendLine("```")
                    }.also {
                        sendMessage(message.chat, it, MarkdownV2ParseMode)
                    }
                }

                onCommandWithArgs(StartMonday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartMonday)
                }
                onCommandWithArgs(StartTuesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartTuesday)
                }
                onCommandWithArgs(StartWednesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartWednesday)
                }
                onCommandWithArgs(StartThursday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartThursday)
                }
                onCommandWithArgs(StartFriday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartFriday)
                }
                onCommandWithArgs(StartSaturday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartSaturday)
                }
                onCommandWithArgs(StartSunday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), StartSunday)
                }
                onCommandWithArgs(EndMonday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndMonday)
                }
                onCommandWithArgs(EndTuesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndTuesday)
                }
                onCommandWithArgs(EndWednesday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndWednesday)
                }
                onCommandWithArgs(EndThursday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndThursday)
                }
                onCommandWithArgs(EndFriday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndFriday)
                }
                onCommandWithArgs(EndSaturday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndSaturday)
                }
                onCommandWithArgs(EndSunday) { commonMessage, args ->
                    handleDayCycleCommand(commonMessage, args.toList(), EndSunday)
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
            if (commonMessage.chat !is GroupChat || commonMessage.chat !is SupergroupChat) return
            if (args.size != 1) {
                reply(commonMessage, "Wrong args. Use: ${command.command} 20")
                return
            }

            val chat = getChat(commonMessage.chat)

            val time = args.first().toIntOrNull()

            if (time == null) {
                reply(commonMessage, "Wrong args. Use: ${command.command} 20")
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

            sendMessage(commonMessage.chat, "Successful")
        } catch (e: Exception) {
            sendMessage(commonMessage.chat, "error: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun BehaviourContext.handleDayCycleCommand(
        commonMessage: CommonMessage<TextContent>,
        args: List<String>,
        taskType: BotCommand,
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
            transaction {
                when (taskType) {
                    StartMonday -> chat.startMonday = time
                    StartTuesday -> chat.startTuesday = time
                    StartWednesday -> chat.startWednesday = time
                    StartThursday -> chat.startThursday = time
                    StartFriday -> chat.startFriday = time
                    StartSaturday -> chat.startSaturday = time
                    StartSunday -> chat.startSunday = time

                    EndMonday -> chat.endMonday = time
                    EndTuesday -> chat.endTuesday = time
                    EndWednesday -> chat.endWednesday = time
                    EndThursday -> chat.endThursday = time
                    EndFriday -> chat.endFriday = time
                    EndSaturday -> chat.endSaturday = time
                    EndSunday -> chat.endSunday = time

                    else -> throw IllegalArgumentException("Impossible")
                }
                commit()
            }
            addTaskByDayCycleTimetable(chat, bot)
            addSmokeTasksByDayCycleTimetable(chat, bot)
            sendMessage(commonMessage.chat, "Successful")
        } catch (e: Exception) {
            sendMessage(commonMessage.chat, "error: ${e.message}")
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
                    job = { bot.sendMessage(ChatId(chat.id.value), START_DAY_MESSAGES.random()) })
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
                        )
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
                if (a.isAfter(endTime)) break
                a = a.plus(chat.smokeDuration.toLong(), ChronoUnit.MINUTES)
                endSmoke.add(a)
            }

            startSmoke.forEach { time ->
                taskScheduler.addTask(
                    Task(
                        chatId = chat.id.value,
                        time = time,
                        type = TaskType.SmokeTimeStart,
                        daysOfWeek = it.value,
                        job = { bot.sendMessage(ChatId(chat.id.value), SMOKE_TIME_START_MESSAGES.random()) }
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
                        job = { bot.sendMessage(ChatId(chat.id.value), SMOKE_TIME_END_MESSAGES.random()) }
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

