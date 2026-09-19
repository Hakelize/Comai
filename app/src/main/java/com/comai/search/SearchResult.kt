package com.comai.search

/**
 * Represents a single web search result with citation metadata.
 * Used to provide grounded context from web sources alongside AI responses.
 */
data class SearchResult(
    /** Title of the web result or knowledge panel. */
    val title: String,
    /** Short text snippet or abstract from the source. */
    val snippet: String,
    /** Source URL for the citation link. */
    val url: String,
    /** Human-readable source name (e.g., "Wikipedia", "DuckDuckGo"). */
    val source: String
)
