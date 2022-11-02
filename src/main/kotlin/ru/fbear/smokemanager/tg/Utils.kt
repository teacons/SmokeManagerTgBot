package ru.fbear.smokemanager.tg

import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun String.toLocalTimeOrNull(): LocalTime? {
    //todo(сделать проверку правильности формата времени в строке) 00:00
    return if (Regex("\\d{2}:\\d{2}").matches(this))
        LocalTime.parse(this, DateTimeFormatter.ISO_LOCAL_TIME)
    else null
}