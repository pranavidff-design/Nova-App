package com.nova.assistant

import android.content.Context

/**
 * Routes simple commands to real Android actions — free, instant, no AI call needed.
 * Sensitive actions (camera) are flagged here but the actual approval dialog + execution
 * happens in MainActivity, since only an Activity can show a dialog / needs runtime permission.
 */
class LocalCommandRouter(private val context: Context) {

    private val executor = ActionExecutor(context)

    sealed class RouteResult {
        data class Executed(val message: String) : RouteResult()
        data class NeedsApproval(val actionLabel: String, val onApproved: () -> String) : RouteResult()
    }

    fun tryHandle(text: String): RouteResult? {
        val lower = text.lowercase()

        if (Regex("what.?s the time|current time").containsMatchIn(lower)) {
            val time = java.text.SimpleDateFormat("h:mm a").format(java.util.Date())
            return RouteResult.Executed("It's $time.")
        }

        if (Regex("open calculator").containsMatchIn(lower)) return RouteResult.Executed(executor.openCalculator())
        if (Regex("open maps|navigate").containsMatchIn(lower)) return RouteResult.Executed(executor.openMaps())
        if (Regex("open browser|open chrome").containsMatchIn(lower)) return RouteResult.Executed(executor.openBrowser())
        if (Regex("open (email|mail)").containsMatchIn(lower)) return RouteResult.Executed(executor.openEmail())
        if (Regex("open settings").containsMatchIn(lower)) return RouteResult.Executed(executor.openSettings())

        if (Regex("flashlight on|torch on").containsMatchIn(lower)) return RouteResult.Executed(executor.setFlashlight(true))
        if (Regex("flashlight off|torch off").containsMatchIn(lower)) return RouteResult.Executed(executor.setFlashlight(false))

        if (Regex("volume up").containsMatchIn(lower)) return RouteResult.Executed(executor.adjustVolume(true))
        if (Regex("volume down").containsMatchIn(lower)) return RouteResult.Executed(executor.adjustVolume(false))

        if (Regex("open camera|take a photo|take a picture").containsMatchIn(lower)) {
            return RouteResult.NeedsApproval("open the Camera") { executor.openCameraApp() }
        }

        // "set alarm for 7 am" / "set alarm for 7:30 pm" / "set an alarm for 7"
        val alarmMatch = Regex("alarm for (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toInt()
            val minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = alarmMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return RouteResult.Executed(executor.setAlarm(hour, minute))
        }

        // "add to calendar: dentist appointment" / "create event dentist appointment"
        val calendarMatch = Regex("(?:add to calendar|create (?:an? )?event)[: ]+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (calendarMatch != null) {
            return RouteResult.Executed(executor.createCalendarEvent(calendarMatch.groupValues[1].trim()))
        }

        // "search for X" / "google X" / "search the web for X"
        val searchMatch = Regex("(?:search(?: the web)? for|google) (.+)", RegexOption.IGNORE_CASE).find(text)
        if (searchMatch != null) {
            return RouteResult.Executed(executor.webSearch(searchMatch.groupValues[1].trim()))
        }

        return null // unmatched -> goes to the AI model
    }
}
