package ru.fbear.smokemanager.tg.models

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.time.LocalTime

object Chats : IdTable<Long>() {
    override val id: Column<EntityID<Long>> = long("chat_id").entityId()
    val startMonday = integer("start_monday").nullable()
    val startTuesday = integer("start_tuesday").nullable()
    val startWednesday = integer("start_wednesday").nullable()
    val startThursday = integer("start_thursday").nullable()
    val startFriday = integer("start_friday").nullable()
    val startSaturday = integer("start_saturday").nullable()
    val startSunday = integer("start_sunday").nullable()
    val endMonday = integer("end_monday").nullable()
    val endTuesday = integer("end_tuesday").nullable()
    val endWednesday = integer("end_wednesday").nullable()
    val endThursday = integer("end_thursday").nullable()
    val endFriday = integer("end_friday").nullable()
    val endSaturday = integer("end_saturday").nullable()
    val endSunday = integer("end_sunday").nullable()
    val smokeDuration = integer("smoke_duration").default(15)
    val smokeInterval = integer("smoke_interval").default(60)
    override val primaryKey = PrimaryKey(id)
}

val transformRealToColumn: (LocalTime?) -> Int? = { it?.toSecondOfDay() }
val transformColumnToReal: (Int?) -> LocalTime? =
    { timeInSeconds -> timeInSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) } }

class Chat(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Chat>(Chats)

    var startMonday: LocalTime? by Chats.startMonday.transform(transformRealToColumn, transformColumnToReal)
    var startTuesday: LocalTime? by Chats.startTuesday.transform(transformRealToColumn, transformColumnToReal)
    var startWednesday: LocalTime? by Chats.startWednesday.transform(transformRealToColumn, transformColumnToReal)
    var startThursday: LocalTime? by Chats.startThursday.transform(transformRealToColumn, transformColumnToReal)
    var startFriday: LocalTime? by Chats.startFriday.transform(transformRealToColumn, transformColumnToReal)
    var startSaturday: LocalTime? by Chats.startSaturday.transform(transformRealToColumn, transformColumnToReal)
    var startSunday: LocalTime? by Chats.startSunday.transform(transformRealToColumn, transformColumnToReal)
    var endMonday: LocalTime? by Chats.endMonday.transform(transformRealToColumn, transformColumnToReal)
    var endTuesday: LocalTime? by Chats.endTuesday.transform(transformRealToColumn, transformColumnToReal)
    var endWednesday: LocalTime? by Chats.endWednesday.transform(transformRealToColumn, transformColumnToReal)
    var endThursday: LocalTime? by Chats.endThursday.transform(transformRealToColumn, transformColumnToReal)
    var endFriday: LocalTime? by Chats.endFriday.transform(transformRealToColumn, transformColumnToReal)
    var endSaturday: LocalTime? by Chats.endSaturday.transform(transformRealToColumn, transformColumnToReal)
    var endSunday: LocalTime? by Chats.endSunday.transform(transformRealToColumn, transformColumnToReal)

    var smokeDuration by Chats.smokeDuration
    var smokeInterval by Chats.smokeInterval

    fun getDayCycleTimetable(): DayCycleTimetable {
        return DayCycleTimetable(
            monday = DayCycleStartEnd(startMonday, endMonday),
            tuesday = DayCycleStartEnd(startTuesday, endTuesday),
            wednesday = DayCycleStartEnd(startWednesday, endWednesday),
            thursday = DayCycleStartEnd(startThursday, endThursday),
            friday = DayCycleStartEnd(startFriday, endFriday),
            saturday = DayCycleStartEnd(startSaturday, endSaturday),
            sunday = DayCycleStartEnd(startSunday, endSunday)
        )
    }
}