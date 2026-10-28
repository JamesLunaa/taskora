package io.github.jameslunaa.taskora.data.local

import androidx.room.TypeConverter
import io.github.jameslunaa.taskora.domain.model.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    // Stored as the numeric level so ORDER BY priority sorts correctly.
    @TypeConverter
    fun priorityToLevel(value: Priority): Int = value.level

    @TypeConverter
    fun levelToPriority(value: Int): Priority = Priority.fromLevel(value)
}
