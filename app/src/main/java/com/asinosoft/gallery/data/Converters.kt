package com.asinosoft.gallery.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.TypeConverter
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let { UUID.fromString(it) }

    @TypeConverter
    fun localDateToLong(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun localTimeToLong(time: LocalTime): Long = time.toNanoOfDay()

    @TypeConverter
    fun longToLocalDateTime(value: Long): LocalTime = LocalTime.ofNanoOfDay(value)

    @TypeConverter
    fun uriToString(value: Uri): String = value.toString()

    @TypeConverter
    fun stringToUri(value: String): Uri = value.toUri()

    @TypeConverter
    fun imageToString(value: Image): String = Gson().toJson(value)

    @TypeConverter
    fun stringToImage(value: String): Image = Gson().fromJson(value, Image::class.java)

    @TypeConverter
    fun videoToString(value: Video): String = Gson().toJson(value)

    @TypeConverter
    fun stringToVideo(value: String): Video = Gson().fromJson(value, Video::class.java)
}
