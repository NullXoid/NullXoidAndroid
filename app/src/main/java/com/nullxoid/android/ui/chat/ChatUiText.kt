package com.nullxoid.android.ui.chat

fun friendlyChatError(error: String): String {
    val normalized = error.lowercase()
    return when {
        "429" in normalized || "rate limit" in normalized || "too many" in normalized ->
            "NullXoid is receiving too many assistant requests right now. Wait a moment, then try again."
        else -> error
    }
}
