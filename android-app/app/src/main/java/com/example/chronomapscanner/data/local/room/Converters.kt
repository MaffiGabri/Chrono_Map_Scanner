package com.example.chronomapscanner.data.local.room

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(epochDays: Long?): LocalDate? {
        return epochDays?.let { LocalDate.ofEpochDay(it) }
    }
}
