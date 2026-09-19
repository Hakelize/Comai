package com.comai.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight web search service using free, no-API-key endpoints:
 *
 * 1. **DuckDuckGo Instant Answer API** — knowledge graph / topic summaries
 * 2. **Wikipedia Summary API** — factual article summaries as fallback
 *
 * Returns structured [SearchResult] items that can be injected as grounded
 * context into the LLM prompt and rendered as citation cards in the UI.
 */
object WebSearchService {

    private const val TAG = "WebSearchService"
    private const val DDG_API = "https://api.duckduckgo.com/"
    private const val WIKI_API = "https://en.wikipedia.org/api/rest_v1/page/summary/"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * Detects whether the user message has "search intent" — i.e. the user is asking
     * a factual question that would benefit from web-sourced context.
     */
    fun hasSearchIntent(message: String): Boolean {
        val lower = message.lowercase().trim()

        // 1. Never web search for system / time / clock / date queries
        if (lower.contains("time") || lower.contains("date") || lower.contains("clock") ||
            lower.contains("today") || lower.contains("now") || lower.contains("battery") ||
            lower.contains("map") || lower.contains("location")) {
            return false
        }

        // 2. Never web search for personal / memory / schedule queries
        if (lower.contains("my ") || lower.contains(" i ") || lower.contains(" me") ||
            lower.contains("myself") || lower.contains("mine") || lower.contains("who am i") ||
            lower.contains("remember") || lower.contains("memory") || lower.contains("memories") ||
            lower.contains("routine") || lower.contains("schedule") || lower.contains("plan") ||
            lower.contains("office") || lower.contains("work") || lower.contains("college") ||
            lower.contains("wake") || lower.contains("sleep")) {
            return false
        }

        // 3. Never web search for common conversational greetings / affirmations
        if (lower.startsWith("hi") || lower.startsWith("hello") || lower.startsWith("hey") ||
            lower.startsWith("good morning") || lower.startsWith("good night") ||
            lower.startsWith("thanks") || lower.startsWith("thank you") || lower == "ok" || lower == "yes") {
            return false
        }

        val searchPrefixes = listOf(
            "search ", "find ", "look up ", "google ",
            "what is ", "what are ", "who is ", "who are ",
            "when was ", "when did ", "when is ",
            "where is ", "where are ",
            "how does ", "how do ", "how is ",
            "tell me about ", "explain ",
            "define ", "meaning of ",
            "latest news ", "news about ",
            "history of ", "why is ", "why do ", "why does "
        )
        return searchPrefixes.any { lower.startsWith(it) }
    }

    /**
     * Extracts a clean search query from the user message by stripping the intent prefix.
     */
    fun extractQuery(message: String): String {
        val lower = message.lowercase().trim()
        val prefixes = listOf(
            "search for ", "search ",
            "find out about ", "find ",
            "look up ", "google ",
            "what is a ", "what is an ", "what is the ", "what is ",
            "what are ", "who is ", "who are ",
            "when was ", "when did ", "when is ",
            "where is ", "where are ",
            "how does ", "how do ", "how is ",
            "tell me about ", "explain ",
            "define ", "meaning of ",
            "latest news on ", "latest news about ", "latest news ",
            "news about ", "news on ",
            "history of ", "why is ", "why do ", "why does "
        )
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                return message.substring(prefix.length).trim().removeSuffix("?").removeSuffix(".")
            }
        }
        return message.trim().removeSuffix("?").removeSuffix(".")
    }

    /**
     * Performs a search across DuckDuckGo and Wikipedia.
     * Returns up to 3 [SearchResult] items. Returns an empty list on failure.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()

        // 1. DuckDuckGo Instant Answer
        try {
            val ddgResults = searchDuckDuckGo(query)
            results.addAll(ddgResults)
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo search failed: ${e.message}")
        }

        // 2. Wikipedia Summary (always try as supplement / fallback)
        try {
            val wikiResult = searchWikipedia(query)
            if (wikiResult != null && results.none { it.source == "Wikipedia" }) {
                results.add(wikiResult)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search failed: ${e.message}")
        }

        Log.i(TAG, "Search for '$query' returned ${results.size} results")
        results.take(3)
    }

    /**
     * Formats search results as grounded context text for injection into the LLM prompt.
     */
    fun formatAsContext(results: List<SearchResult>): String {
        if (results.isEmpty()) return ""
        val sb = StringBuilder("Web Search Results:\n")
        results.forEachIndexed { i, r ->
            sb.append("[${i + 1}] ${r.title} (${r.source})\n")
            sb.append("   ${r.snippet}\n")
            sb.append("   Source: ${r.url}\n\n")
        }
        return sb.toString().trim()
    }

    private fun searchDuckDuckGo(query: String): List<SearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("$DDG_API?q=$encoded&format=json&no_html=1&skip_disambig=1")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Comai/1.0 (Android AI Companion)")
        }

        val results = mutableListOf<SearchResult>()
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            // Abstract (main topic summary)
            val abstractText = json.optString("AbstractText", "")
            val abstractUrl = json.optString("AbstractURL", "")
            val abstractSource = json.optString("AbstractSource", "DuckDuckGo")
            if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                results.add(
                    SearchResult(
                        title = json.optString("Heading", query),
                        snippet = abstractText.take(300),
                        url = abstractUrl,
                        source = abstractSource.ifBlank { "DuckDuckGo" }
                    )
                )
            }

            // Related Topics (first 2)
            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                for (i in 0 until minOf(relatedTopics.length(), 2)) {
                    val topic = relatedTopics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val topicUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank() && topicUrl.isNotBlank()) {
                        results.add(
                            SearchResult(
                                title = text.take(80),
                                snippet = text.take(250),
                                url = topicUrl,
                                source = "DuckDuckGo"
                            )
                        )
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return results
    }

    private fun searchWikipedia(query: String): SearchResult? {
        val encoded = URLEncoder.encode(query.replace(" ", "_"), "UTF-8")
        val url = URL("$WIKI_API$encoded")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Comai/1.0 (Android AI Companion)")
        }

        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            val type = json.optString("type", "")
            if (type == "disambiguation" || type == "not_found") return null

            val title = json.optString("title", "")
            val extract = json.optString("extract", "")
            val pageUrl = json.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page", "") ?: ""

            if (extract.isNotBlank() && title.isNotBlank()) {
                return SearchResult(
                    title = title,
                    snippet = extract.take(350),
                    url = pageUrl.ifBlank { "https://en.wikipedia.org/wiki/${query.replace(" ", "_")}" },
                    source = "Wikipedia"
                )
            }
        } finally {
            conn.disconnect()
        }
        return null
    }
}
