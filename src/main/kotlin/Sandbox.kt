import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

fun main() {
    val time = LocalTime.parse("13:00", DateTimeFormatter.ISO_LOCAL_TIME)
    val timeNow = LocalTime.now()
    val dateTime = if (time.isAfter(timeNow)) time.atDate(LocalDate.now()) else time.atDate(LocalDate.now().plusDays(1))
    val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()

    val date = Date.from(instant)
    print(date.toString())
}