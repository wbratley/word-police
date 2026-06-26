package com.wordpolice

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val CORRECT_NEEDED = 5
const val ESCAPES_TO_LOSE = 3

enum class Screen { START, PLAYING, WIN, LOSE }

enum class SelectResult { NONE, CORRECT, WRONG, ESCAPE }

data class Question(
    val word: String,
    val options: List<String>,
    val correctIndex: Int
)

data class GameState(
    val screen: Screen = Screen.START,
    val roundIndex: Int = 0,
    val question: Question? = null,
    val correctCount: Int = 0,
    val escapeCount: Int = 0,
    val greyedOptions: Set<Int> = emptySet(),
    val answeredCorrectly: Boolean = false,
    val wrongAttempts: Int = 0
) {
    val policeFraction: Float get() = 0.04f + (correctCount.toFloat() / CORRECT_NEEDED) * 0.58f
    val criminalFraction: Float get() = 0.72f + (escapeCount.toFloat() / ESCAPES_TO_LOSE) * 0.22f
    val currentRound: WordRound get() = WORD_ROUNDS.getOrElse(roundIndex) { WORD_ROUNDS.last() }
    val hasNextRound: Boolean get() = roundIndex < WORD_ROUNDS.lastIndex
}

class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var remainingWords = mutableListOf<String>()

    fun startGame(roundIndex: Int = _state.value.roundIndex) {
        val words = WORD_ROUNDS.getOrElse(roundIndex) { WORD_ROUNDS.last() }.words
        remainingWords = words.shuffled().toMutableList()
        _state.value = GameState(screen = Screen.PLAYING, roundIndex = roundIndex)
        nextQuestion()
    }

    fun nextQuestion() {
        val roundIndex = _state.value.roundIndex
        if (remainingWords.isEmpty()) {
            remainingWords = WORD_ROUNDS.getOrElse(roundIndex) { WORD_ROUNDS.last() }
                .words.shuffled().toMutableList()
        }
        val word = remainingWords.removeFirst()
        val distractors = getDistractors(word, roundIndex)
        val options = (listOf(word) + distractors).shuffled()

        _state.value = _state.value.copy(
            question = Question(word, options, options.indexOf(word)),
            greyedOptions = emptySet(),
            answeredCorrectly = false,
            wrongAttempts = 0
        )
    }

    fun selectOption(index: Int): SelectResult {
        val s = _state.value
        val q = s.question ?: return SelectResult.NONE
        if (s.answeredCorrectly || index in s.greyedOptions || s.screen == Screen.LOSE) {
            return SelectResult.NONE
        }

        return if (index == q.correctIndex) {
            val newCorrect = s.correctCount + 1
            _state.value = s.copy(
                correctCount = newCorrect,
                answeredCorrectly = true,
                screen = if (newCorrect >= CORRECT_NEEDED) Screen.WIN else Screen.PLAYING
            )
            SelectResult.CORRECT
        } else {
            val newWrong = s.wrongAttempts + 1
            val newGreyed = s.greyedOptions + index
            if (newWrong >= 2) {
                val newEscapes = s.escapeCount + 1
                _state.value = s.copy(
                    wrongAttempts = newWrong,
                    greyedOptions = newGreyed,
                    escapeCount = newEscapes,
                    screen = if (newEscapes >= ESCAPES_TO_LOSE) Screen.LOSE else Screen.PLAYING
                )
                SelectResult.ESCAPE
            } else {
                _state.value = s.copy(wrongAttempts = newWrong, greyedOptions = newGreyed)
                SelectResult.WRONG
            }
        }
    }

    fun advanceRound() = startGame((_state.value.roundIndex + 1).coerceAtMost(WORD_ROUNDS.lastIndex))

    fun replayRound() = startGame(_state.value.roundIndex)
}
