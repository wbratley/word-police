package com.wordpolice

data class WordRound(val name: String, val emoji: String, val words: List<String>)

val WORD_ROUNDS = listOf(
    WordRound(
        name = "Round 1 – First Words",
        emoji = "🌟",
        words = listOf(
            "the", "a", "I", "is", "it", "he", "she", "we", "me", "be",
            "do", "go", "no", "so", "to", "by", "my", "of", "if", "in",
            "on", "at", "an", "as", "up"
        )
    ),
    WordRound(
        name = "Round 2 – Common Words",
        emoji = "🏅",
        words = listOf(
            "was", "his", "has", "you", "are", "our", "one", "ask", "put",
            "who", "oh", "her", "him", "all", "but", "had", "not", "can",
            "did", "its", "off", "out", "too", "use"
        )
    ),
    WordRound(
        name = "Round 3 – Tricky Words",
        emoji = "🎯",
        words = listOf(
            "said", "says", "were", "they", "your", "love", "come", "some",
            "once", "push", "pull", "full", "house", "here", "there", "where",
            "today", "many", "door", "floor", "poor", "both", "only", "also",
            "most", "move", "sure", "hour"
        )
    ),
    WordRound(
        name = "Round 4 – Exception Words",
        emoji = "🥈",
        words = listOf(
            "could", "would", "should", "people", "water", "friend", "school",
            "again", "half", "money", "every", "any", "their", "because",
            "beautiful", "pretty", "busy", "whole", "clothes", "sugar",
            "prove", "improve", "eye", "find", "kind", "mind"
        )
    ),
    WordRound(
        name = "Round 5 – Master Level",
        emoji = "🏆",
        words = listOf(
            "behind", "child", "children", "wild", "climb", "after", "fast",
            "last", "past", "father", "class", "grass", "pass", "plant",
            "path", "bath", "called", "asked", "looked", "people", "father",
            "water", "again", "every", "beautiful", "because", "should",
            "friend", "school", "clothes"
        )
    )
)

val ALL_WORDS: List<String> = WORD_ROUNDS.flatMap { it.words }.distinct()

/** Returns 2 distractor words from the same round (or adjacent rounds). */
fun getDistractors(target: String, roundIndex: Int): List<String> {
    val round = WORD_ROUNDS[roundIndex]
    val pool = buildList {
        addAll(round.words)
        if (roundIndex > 0) addAll(WORD_ROUNDS[roundIndex - 1].words)
        if (roundIndex < WORD_ROUNDS.lastIndex) addAll(WORD_ROUNDS[roundIndex + 1].words)
    }.distinct().filter { it != target }

    return pool.shuffled().take(2)
}
