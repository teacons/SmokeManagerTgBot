package models

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object Chats : IdTable<Long>() {
    override val id: Column<EntityID<Long>> = long("chat_id").entityId()
    val startJobTime = integer("start_job_time").nullable()
    val endJobTime = integer("end_job_time").nullable()
    val endJobFridayTime = integer("end_job_friday_time").nullable()
    override val primaryKey = PrimaryKey(id)
}

class Chat(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Chat>(Chats)

    var startJobTime by Chats.startJobTime
    val endJobTime by Chats.endJobTime
    val endJobFridayTime by Chats.endJobFridayTime
}