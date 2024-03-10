package com.asinosoft.gallery.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateToLong(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)
}
