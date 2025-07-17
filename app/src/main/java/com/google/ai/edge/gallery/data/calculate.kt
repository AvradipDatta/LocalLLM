//changed in file
package com.google.ai.edge.gallery.utils

object RandomQuoteGenerator {
    private val quotes = listOf(
        "Stay hungry, stay foolish.",
        "Code is like humor. When you have to explain it, it’s bad.",
        "Experience is the name everyone gives to their mistakes.",
        "In order to be irreplaceable, one must always be different.",
        "Fix the cause, not the symptom."
    )

    fun getRandomQuote(): String {
        return quotes.random()
    }
        fun getQuoteWithIndex(): Pair<Int, String> {
        val index = quotes.indices.random()
        return Pair(index, quotes[index])
    }

}
