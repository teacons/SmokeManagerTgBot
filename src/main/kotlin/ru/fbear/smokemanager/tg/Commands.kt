package ru.fbear.smokemanager.tg

import dev.inmo.tgbotapi.types.BotCommand

val Monday = BotCommand("monday", COMMAND_DESCRIPTION_MONDAY)
val Tuesday = BotCommand("tuesday", COMMAND_DESCRIPTION_TUESDAY)
val Wednesday = BotCommand("wednesday", COMMAND_DESCRIPTION_WEDNESDAY)
val Thursday = BotCommand("thursday", COMMAND_DESCRIPTION_THURSDAY)
val Friday = BotCommand("friday", COMMAND_DESCRIPTION_FRIDAY)
val Saturday = BotCommand("saturday", COMMAND_DESCRIPTION_SATURDAY)
val Sunday = BotCommand("sunday", COMMAND_DESCRIPTION_SUNDAY)
val SetSmokeDuration = BotCommand("set_smoke_duration", COMMAND_DESCRIPTION_SET_SMOKE_DURATION)
val SetSmokeInterval = BotCommand("set_smoke_interval", COMMAND_DESCRIPTION_SET_SMOKE_INTERVAL)
val ListDayCycle = BotCommand("list_day_cycle", COMMAND_DESCRIPTION_LIST_DAY_CYCLE)
val ListTodaySmokeCycle = BotCommand("list_smoke_cycle", COMMAND_DESCRIPTION_LIST_TODAY_SMOKE_CYCLE)

val commandList = listOf(
    Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday,
    SetSmokeDuration, SetSmokeInterval,
    ListDayCycle, ListTodaySmokeCycle
)

