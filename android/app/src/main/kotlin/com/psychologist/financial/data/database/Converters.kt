package com.psychologist.financial.data.database

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun fromLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun localTimeToString(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    fun fromLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    @TypeConverter
    fun localDateTimeToString(dateTime: LocalDateTime?): String? = dateTime?.toString()

    @TypeConverter
    fun fromBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()
}
