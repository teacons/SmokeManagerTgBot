package ru.fbear.smokemanager.tg.models

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.time.LocalTime

object Chats : IdTable<Long>() {
    override val id: Column<EntityID<Long>> = long("chat_id").entityId()
    val startJobTime = integer("start_job_time").nullable()
    val endJobTime = integer("end_job_time").nullable()
    val endJobFridayTime = integer("end_job_friday_time").nullable()
    override val primaryKey = PrimaryKey(id)
}

class Chat(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Chat>(Chats)

    var startJobTime: LocalTime? by Chats.startJobTime.transform(
        { it?.toSecondOfDay() },
        { timeInSeconds -> timeInSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) } })
    var endJobTime: LocalTime? by Chats.endJobTime.transform(
        { it?.toSecondOfDay() },
        { timeInSeconds -> timeInSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) } })
    var endJobFridayTime: LocalTime? by Chats.endJobFridayTime.transform(
        { it?.toSecondOfDay() },
        { timeInSeconds -> timeInSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) } })
}