package com.comai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.comai.data.models.ChatMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists and restores chat message history across app restarts and swiping from recents.
 */
class ChatHistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadMessages(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        try {
            // Keep up to 200 most recent messages for performance
            val trimmed = if (messages.size > 200) messages.takeLast(200) else messages
            val json = gson.toJson(trimmed)
            prefs.edit().putString(KEY_MESSAGES, json).apply()
        } catch (_: Exception) {}
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    companion object {
        private const val PREFS_NAME = "comai_chat_history_prefs"
        private const val KEY_MESSAGES = "saved_chat_messages"
    }
}
