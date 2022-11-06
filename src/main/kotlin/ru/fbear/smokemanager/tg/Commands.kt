package ru.fbear.smokemanager.tg

import dev.inmo.tgbotapi.types.BotCommand

val StartMonday = BotCommand("start_monday", COMMAND_DESCRIPTION_SET_START_MONDAY)
val StartTuesday = BotCommand("start_tuesday", COMMAND_DESCRIPTION_SET_START_TUESDAY)
val StartWednesday = BotCommand("start_wednesday", COMMAND_DESCRIPTION_SET_START_WEDNESDAY)
val StartThursday = BotCommand("start_thursday", COMMAND_DESCRIPTION_SET_START_THURSDAY)
val StartFriday = BotCommand("start_friday", COMMAND_DESCRIPTION_SET_START_FRIDAY)
val StartSaturday = BotCommand("start_saturday", COMMAND_DESCRIPTION_SET_START_SATURDAY)
val StartSunday = BotCommand("start_sunday", COMMAND_DESCRIPTION_SET_START_SUNDAY)
val EndMonday = BotCommand("end_monday", COMMAND_DESCRIPTION_SET_END_MONDAY)
val EndTuesday = BotCommand("end_tuesday", COMMAND_DESCRIPTION_SET_END_TUESDAY)
val EndWednesday = BotCommand("end_wednesday", COMMAND_DESCRIPTION_SET_END_WEDNESDAY)
val EndThursday = BotCommand("end_thursday", COMMAND_DESCRIPTION_SET_END_THURSDAY)
val EndFriday = BotCommand("end_friday", COMMAND_DESCRIPTION_SET_END_FRIDAY)
val EndSaturday = BotCommand("end_saturday", COMMAND_DESCRIPTION_SET_END_SATURDAY)
val EndSunday = BotCommand("end_sunday", COMMAND_DESCRIPTION_SET_END_SUNDAY)
val SetSmokeDuration = BotCommand("set_smoke_duration", COMMAND_DESCRIPTION_SET_SMOKE_DURATION)
val SetSmokeInterval = BotCommand("set_smoke_interval", COMMAND_DESCRIPTION_SET_SMOKE_INTERVAL)
val ListDayCycle = BotCommand("list_day_cycle", COMMAND_DESCRIPTION_LIST_DAY_CYCLE)
val ListTodaySmokeCycle = BotCommand("list_smoke_cycle", COMMAND_DESCRIPTION_LIST_TODAY_SMOKE_CYCLE)

val commandList = listOf(
    StartMonday, StartTuesday, StartWednesday, StartThursday, StartFriday, StartSaturday, StartSunday,
    EndMonday, EndTuesday, EndWednesday, EndThursday, EndFriday, EndSaturday, EndSunday,
    SetSmokeDuration, SetSmokeInterval,
    ListDayCycle, ListTodaySmokeCycle
)

