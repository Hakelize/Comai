package com.comai.contextengine.providers.android

import com.comai.contextengine.providers.interfaces.TimeContextProvider
import com.comai.contextengine.providers.models.TimeContextData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Android implementation for Time Context. Always available.
 */
class AndroidTimeContextProvider : TimeContextProvider {

    override val isAvailable: Boolean = true

    override fun getContextData(): TimeContextData {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(calendar.time).uppercase(Locale.US)
        val isWorkDay = dayOfWeek !in listOf("SATURDAY", "SUNDAY")
        val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)

        return TimeContextData(
            hour = hour,
            minute = minute,
            dayOfWeek = dayOfWeek,
            isWorkDay = isWorkDay,
            formattedTime = formattedTime
        )
    }
}
