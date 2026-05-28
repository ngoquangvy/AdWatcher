package com.adwatcher.app.analyzer
 
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Helper class to get the recently foregrounded apps using UsageStatsManager.
 */
class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? = 
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Get the package name of the app that was in the foreground `secondsAgo` seconds ago.
     */
    fun getForegroundAppPastSeconds(secondsAgo: Int): String? {
        if (usageStatsManager == null) return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (secondsAgo * 1000L)

        // Give a bit of buffer
        val events = usageStatsManager.queryEvents(startTime - 5000L, endTime)
        if (events == null || !events.hasNextEvent()) return null

        var lastForegroundPackage: String? = null
        var lastEventTime = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // Look for ACTIVITY_RESUMED
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp <= startTime && event.timeStamp > lastEventTime) {
                    lastForegroundPackage = event.packageName
                    lastEventTime = event.timeStamp
                } else if (event.timeStamp > startTime && lastForegroundPackage == null) {
                    // If no event exactly before startTime, take the first one after
                    lastForegroundPackage = event.packageName
                }
            }
        }
        return lastForegroundPackage
    }

    /**
     * Get all unique packages that had an ACTIVITY_RESUMED event in the last X seconds.
     */
    fun getRecentForegroundApps(seconds: Int): List<String> {
        if (usageStatsManager == null) return emptyList()

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (seconds * 1000L)

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val recentPackages = mutableSetOf<String>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                event.packageName?.let { recentPackages.add(it) }
            }
        }
        return recentPackages.toList()
    }
}
