package ru.fbear.smokemanager.tg.models

import java.time.DayOfWeek

data class DayCycleTimetable(
    val monday: DayCycleStartEnd,
    val tuesday: DayCycleStartEnd,
    val wednesday: DayCycleStartEnd,
    val thursday: DayCycleStartEnd,
    val friday: DayCycleStartEnd,
    val saturday: DayCycleStartEnd,
    val sunday: DayCycleStartEnd,
) {
    fun getMap(): Map<DayOfWeek, DayCycleStartEnd> =
        buildMap {
            put(DayOfWeek.MONDAY, monday)
            put(DayOfWeek.TUESDAY, tuesday)
            put(DayOfWeek.WEDNESDAY, wednesday)
            put(DayOfWeek.THURSDAY, thursday)
            put(DayOfWeek.FRIDAY, friday)
            put(DayOfWeek.SATURDAY, saturday)
            put(DayOfWeek.SUNDAY, sunday)
        }
}