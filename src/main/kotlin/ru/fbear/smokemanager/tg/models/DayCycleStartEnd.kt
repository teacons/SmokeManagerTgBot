package ru.fbear.smokemanager.tg.models

import java.time.LocalTime

data class DayCycleStartEnd(
    val startDay: LocalTime?,
    val endDay: LocalTime?,
) {
    companion object {
        val Empty = DayCycleStartEnd(null, null)
    }
}