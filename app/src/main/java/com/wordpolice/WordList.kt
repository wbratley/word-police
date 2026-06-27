package com.wordpolice

data class WordRound(val name: String, val emoji: String, val words: List<String>)

// All 44 Year 1 Common Exception Words, split into 4 rounds by rough difficulty.
val WORD_ROUNDS = listOf(
    WordRound(
        name = "Round 1 – Rookie",
        emoji = "🚔",
        words = listOf(
            "the", "a", "I", "is", "he", "she", "we", "me", "be",
            "do", "to", "no"
        )
    ),
    WordRound(
        name = "Round 2 – Officer",
        emoji = "🚓",
        words = listOf(
            "go", "so", "by", "my", "of", "his", "has",
            "you", "they", "are", "was", "our"
        )
    ),
    WordRound(
        name = "Round 3 – Detective",
        emoji = "🔍",
        words = listOf(
            "your", "were", "said", "says", "today",
            "here", "there", "where", "love", "come", "some"
        )
    ),
    WordRound(
        name = "Round 4 – Chief Inspector",
        emoji = "🏆",
        words = listOf(
            "one", "once", "ask", "put", "push", "pull",
            "full", "house", "friend", "school"
        )
    )
)

val ALL_WORDS: List<String> = WORD_ROUNDS.flatMap { it.words }.distinct()

/** Returns 2 distractor words drawn from the same round where possible. */
fun getDistractors(target: String, roundIndex: Int): List<String> {
    val round = WORD_ROUNDS[roundIndex]
    val pool = buildList {
        addAll(round.words)
        if (roundIndex > 0) addAll(WORD_ROUNDS[roundIndex - 1].words)
        if (roundIndex < WORD_ROUNDS.lastIndex) addAll(WORD_ROUNDS[roundIndex + 1].words)
    }.distinct().filter { it != target }

    return pool.shuffled().take(2)
}
