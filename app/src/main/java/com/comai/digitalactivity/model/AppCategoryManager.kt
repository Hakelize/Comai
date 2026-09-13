package com.comai.digitalactivity.model

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Standard app categories for Comai Digital Activity intelligence.
 */
enum class AppCategory(val displayName: String) {
    PRODUCTIVITY("Productivity"),
    EDUCATION("Education"),
    COMMUNICATION("Communication"),
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAMING("Gaming"),
    BROWSER("Browser"),
    DEVELOPMENT("Development"),
    UTILITIES("Utilities"),
    OTHER("Other")
}

/**
 * Clean, maintainable categorization system using Android system categories,
 * curated package maps, and keyword heuristics.
 */
object AppCategoryManager {

    private val KNOWN_APPS = mapOf(
        // Social
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.snapchat.android" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        "com.linkedin.android" to AppCategory.SOCIAL,
        "com.pinterest" to AppCategory.SOCIAL,
        "org.thoughtcrime.securesms" to AppCategory.COMMUNICATION,

        // Communication
        "com.whatsapp" to AppCategory.COMMUNICATION,
        "com.whatsapp.w4b" to AppCategory.COMMUNICATION,
        "org.telegram.messenger" to AppCategory.COMMUNICATION,
        "com.google.android.talk" to AppCategory.COMMUNICATION,
        "com.facebook.orca" to AppCategory.COMMUNICATION,
        "com.discord" to AppCategory.COMMUNICATION,
        "com.slack" to AppCategory.COMMUNICATION,
        "com.microsoft.teams" to AppCategory.COMMUNICATION,
        "com.google.android.apps.messaging" to AppCategory.COMMUNICATION,
        "com.google.android.dialer" to AppCategory.COMMUNICATION,
        "com.truecaller" to AppCategory.COMMUNICATION,

        // Entertainment
        "com.google.android.youtube" to AppCategory.ENTERTAINMENT,
        "com.netflix.mediaclient" to AppCategory.ENTERTAINMENT,
        "com.spotify.music" to AppCategory.ENTERTAINMENT,
        "com.amazon.avod.thirdpartyclient" to AppCategory.ENTERTAINMENT,
        "in.startv.hotstar" to AppCategory.ENTERTAINMENT,
        "com.google.android.apps.youtube.music" to AppCategory.ENTERTAINMENT,
        "com.amazon.mp3" to AppCategory.ENTERTAINMENT,
        "com.jio.media.ondemand" to AppCategory.ENTERTAINMENT,

        // Browser
        "com.android.chrome" to AppCategory.BROWSER,
        "org.mozilla.firefox" to AppCategory.BROWSER,
        "com.brave.browser" to AppCategory.BROWSER,
        "com.microsoft.emmx" to AppCategory.BROWSER,
        "com.opera.browser" to AppCategory.BROWSER,
        "com.sec.android.app.sbrowser" to AppCategory.BROWSER,

        // Productivity
        "com.google.android.gm" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office.outlook" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs.editors.sheets" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs.editors.docs" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs.editors.slides" to AppCategory.PRODUCTIVITY,
        "com.google.android.keep" to AppCategory.PRODUCTIVITY,
        "notion.id" to AppCategory.PRODUCTIVITY,
        "com.todoist" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office.word" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office.excel" to AppCategory.PRODUCTIVITY,

        // Education
        "org.coursera.android" to AppCategory.EDUCATION,
        "com.duolingo" to AppCategory.EDUCATION,
        "com.udemy.android" to AppCategory.EDUCATION,
        "org.khanacademy.android" to AppCategory.EDUCATION,
        "com.quizlet.quizletandroid" to AppCategory.EDUCATION,

        // Development
        "com.termux" to AppCategory.DEVELOPMENT,
        "com.github.android" to AppCategory.DEVELOPMENT,
        "com.sonelli.juicessh" to AppCategory.DEVELOPMENT,
        "com.aide.ui" to AppCategory.DEVELOPMENT,

        // Utilities
        "com.android.settings" to AppCategory.UTILITIES,
        "com.google.android.calculator" to AppCategory.UTILITIES,
        "com.google.android.deskclock" to AppCategory.UTILITIES,
        "com.android.vending" to AppCategory.UTILITIES,
        "com.google.android.apps.photos" to AppCategory.UTILITIES,
        "com.google.android.calendar" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.maps" to AppCategory.UTILITIES
    )

    /**
     * Categorizes an application by packageName and optional PackageManager for system metadata.
     */
    fun categorizeApp(packageName: String, packageManager: PackageManager? = null): AppCategory {
        // 1. Check curated mapping
        KNOWN_APPS[packageName]?.let { return it }

        // 2. Check Android system category metadata (API 26+)
        if (packageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMING
                    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
                    ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.ENTERTAINMENT
                    ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                    ApplicationInfo.CATEGORY_NEWS -> return AppCategory.EDUCATION
                    ApplicationInfo.CATEGORY_MAPS -> return AppCategory.UTILITIES
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: package name keyword heuristics
        val lower = packageName.lowercase()
        return when {
            lower.contains("game") || lower.contains("play") || lower.contains("arcade") -> AppCategory.GAMING
            lower.contains("browser") || lower.contains("web") -> AppCategory.BROWSER
            lower.contains("social") || lower.contains("chat") || lower.contains("meet") -> AppCategory.SOCIAL
            lower.contains("mail") || lower.contains("note") || lower.contains("office") || lower.contains("doc") || lower.contains("task") -> AppCategory.PRODUCTIVITY
            lower.contains("learn") || lower.contains("edu") || lower.contains("school") || lower.contains("study") -> AppCategory.EDUCATION
            lower.contains("video") || lower.contains("media") || lower.contains("music") || lower.contains("tv") || lower.contains("stream") -> AppCategory.ENTERTAINMENT
            lower.contains("dev") || lower.contains("code") || lower.contains("git") || lower.contains("terminal") -> AppCategory.DEVELOPMENT
            lower.contains("calc") || lower.contains("clock") || lower.contains("tool") || lower.contains("system") -> AppCategory.UTILITIES
            else -> AppCategory.OTHER
        }
    }
}
