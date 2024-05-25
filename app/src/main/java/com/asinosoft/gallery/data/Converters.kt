package com.asinosoft.gallery.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun localDateToLong(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun localTimeToLong(time: LocalTime): Long = time.toNanoOfDay()

    @TypeConverter
    fun longToLocalDateTime(value: Long): LocalTime = LocalTime.ofNanoOfDay(value)
}
