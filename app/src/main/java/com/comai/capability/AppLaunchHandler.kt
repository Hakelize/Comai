package com.comai.capability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Handles "open <app>" commands from chat by launching matching
 * Android system intents or package-specific intents.
 *
 * Returns a human-readable confirmation string, or null if no match.
 */
object AppLaunchHandler {

    private const val TAG = "AppLaunchHandler"

    /**
     * Canonical map of user-spoken app names to launch actions.
     * Each entry is: spoken name → (action description, intent builder).
     */
    private data class AppEntry(
        val displayName: String,
        val intentBuilder: (Context) -> Intent
    )

    private val APP_MAP: Map<String, AppEntry> = mapOf(
        "maps" to AppEntry("Google Maps") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        },
        "google maps" to AppEntry("Google Maps") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        },
        "chrome" to AppEntry("Chrome") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.android.chrome")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        },
        "browser" to AppEntry("Browser") { _ ->
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        },
        "settings" to AppEntry("Settings") { _ ->
            Intent(Settings.ACTION_SETTINGS)
        },
        "camera" to AppEntry("Camera") { _ ->
            Intent("android.media.action.STILL_IMAGE_CAMERA")
        },
        "youtube" to AppEntry("YouTube") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        },
        "whatsapp" to AppEntry("WhatsApp") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"))
        },
        "instagram" to AppEntry("Instagram") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.instagram.android")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com"))
        },
        "calculator" to AppEntry("Calculator") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                ?: Intent().apply { action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_APP_CALCULATOR) }
        },
        "clock" to AppEntry("Clock") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                ?: Intent("android.intent.action.SHOW_ALARMS")
        },
        "alarm" to AppEntry("Alarm") { _ ->
            Intent("android.intent.action.SHOW_ALARMS")
        },
        "calendar" to AppEntry("Calendar") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALENDAR) }
        },
        "contacts" to AppEntry("Contacts") { _ ->
            Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))
        },
        "phone" to AppEntry("Phone") { _ ->
            Intent(Intent.ACTION_DIAL)
        },
        "dialer" to AppEntry("Phone") { _ ->
            Intent(Intent.ACTION_DIAL)
        },
        "messages" to AppEntry("Messages") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.messaging")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
        },
        "sms" to AppEntry("Messages") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.messaging")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
        },
        "email" to AppEntry("Email") { _ ->
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_EMAIL) }
        },
        "gmail" to AppEntry("Gmail") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_EMAIL) }
        },
        "gallery" to AppEntry("Gallery") { _ ->
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_GALLERY) }
        },
        "photos" to AppEntry("Google Photos") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_GALLERY) }
        },
        "music" to AppEntry("Music") { _ ->
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
        },
        "spotify" to AppEntry("Spotify") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
        },
        "files" to AppEntry("Files") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                ?: Intent(Intent.ACTION_MAIN).apply { type = "*/*" }
        },
        "play store" to AppEntry("Play Store") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.android.vending")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
        },
        "twitter" to AppEntry("X (Twitter)") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.twitter.android")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com"))
        },
        "x" to AppEntry("X (Twitter)") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.twitter.android")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com"))
        },
        "telegram" to AppEntry("Telegram") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://web.telegram.org"))
        },
        "notes" to AppEntry("Notes") { ctx ->
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.keep")
                ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        }
    )

    /**
     * Detects "open <app>" pattern in user message.
     * Returns the cleaned app name if detected, or null.
     */
    fun detectAppLaunchIntent(message: String): String? {
        val lower = message.lowercase().trim()
        val prefixes = listOf("open ", "launch ", "start ", "run ")
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val appName = lower.removePrefix(prefix).trim()
                    .removeSuffix(" app")
                    .removeSuffix(" application")
                    .trim()
                if (appName.isNotBlank() && appName.length < 40) {
                    return appName
                }
            }
        }
        return null
    }

    /**
     * Attempts to launch the named app. Returns a user-facing confirmation string.
     */
    fun launchApp(context: Context, appName: String): String {
        val entry = APP_MAP[appName.lowercase()]
        if (entry != null) {
            return try {
                val intent = entry.intentBuilder(context)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Launched: ${entry.displayName}")
                "Opening ${entry.displayName} for you! 📱"
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "App not found for: $appName — ${e.message}")
                "${entry.displayName} doesn't seem to be installed on this device."
            } catch (e: Exception) {
                Log.e(TAG, "Error launching ${entry.displayName}: ${e.message}", e)
                "I couldn't open ${entry.displayName} right now. Please try again."
            }
        }

        // Fallback: try to find any installed app matching the name
        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            val match = installedApps.find { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                label.contains(appName.lowercase()) || appName.lowercase().contains(label)
            }
            if (match != null) {
                val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = pm.getApplicationLabel(match)
                    Log.i(TAG, "Launched via fallback search: $label")
                    "Opening $label for you! 📱"
                } else {
                    "I found $appName but it doesn't have a launch screen."
                }
            } else {
                "I couldn't find an app called \"$appName\" on your device. Try the exact app name."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback app search failed: ${e.message}", e)
            "I couldn't find \"$appName\" on your device."
        }
    }
}
