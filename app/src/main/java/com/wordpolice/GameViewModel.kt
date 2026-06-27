package com.wordpolice

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val CORRECT_NEEDED = 5
const val ESCAPES_TO_LOSE = 3
const val ROUNDS_PER_LEVEL = 3

enum class Screen { LEVEL_SELECT, PLAYING, ROUND_COMPLETE, LEVEL_COMPLETE }

enum class SelectResult { NONE, CORRECT, WRONG, ESCAPE }

data class Question(
    val word: String,
    val options: List<String>,
    val correctIndex: Int
)

data class GameState(
    val screen: Screen = Screen.LEVEL_SELECT,
    val levelIndex: Int = 0,
    val roundsCompleted: Int = 0,
    val question: Question? = null,
    val correctCount: Int = 0,
    val escapeCount: Int = 0,
    val greyedOptions: Set<Int> = emptySet(),
    val answeredCorrectly: Boolean = false,
    val wrongAttempts: Int = 0,
    val lastRoundWon: Boolean = false,
    val levelCorrect: Int = 0,
    val levelEscapes: Int = 0,
) {
    val policeFraction: Float get() = 0.04f + (correctCount.toFloat() / CORRECT_NEEDED) * 0.58f
    val criminalFraction: Float get() = 0.72f + (escapeCount.toFloat() / ESCAPES_TO_LOSE) * 0.22f
    val currentLevel: WordRound get() = WORD_ROUNDS.getOrElse(levelIndex) { WORD_ROUNDS.last() }
    val hasNextLevel: Boolean get() = levelIndex < WORD_ROUNDS.lastIndex
    val levelTotal: Int get() = levelCorrect + levelEscapes
    val scorePercent: Int get() = if (levelTotal == 0) 0 else (levelCorrect * 100 / levelTotal)
}

class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var remainingWords = mutableListOf<String>()

    fun selectLevel(levelIndex: Int) {
        _state.value = GameState(screen = Screen.PLAYING, levelIndex = levelIndex)
        loadRound()
    }

    fun startNextRound() {
        _state.value = _state.value.copy(
            screen = Screen.PLAYING,
            correctCount = 0,
            escapeCount = 0,
            greyedOptions = emptySet(),
            answeredCorrectly = false,
            wrongAttempts = 0,
        )
        loadRound()
    }

    fun replayLevel() = selectLevel(_state.value.levelIndex)

    fun backToLevelSelect() {
        _state.value = GameState()
    }

    private fun loadRound() {
        remainingWords = _state.value.currentLevel.words.shuffled().toMutableList()
        nextQuestion()
    }

    fun nextQuestion() {
        val levelIndex = _state.value.levelIndex
        if (remainingWords.isEmpty()) {
            remainingWords = WORD_ROUNDS.getOrElse(levelIndex) { WORD_ROUNDS.last() }
                .words.shuffled().toMutableList()
        }
        val word = remainingWords.removeFirst()
        val distractors = getDistractors(word, levelIndex)
        val options = (listOf(word) + distractors).shuffled()
        _state.value = _state.value.copy(
            question = Question(word, options, options.indexOf(word)),
            greyedOptions = emptySet(),
            answeredCorrectly = false,
            wrongAttempts = 0,
        )
    }

    fun selectOption(index: Int): SelectResult {
        val s = _state.value
        val q = s.question ?: return SelectResult.NONE
        if (s.answeredCorrectly || index in s.greyedOptions || s.screen != Screen.PLAYING) {
            return SelectResult.NONE
        }

        return if (index == q.correctIndex) {
            val newCorrect = s.correctCount + 1
            val newLevelCorrect = s.levelCorrect + 1
            if (newCorrect >= CORRECT_NEEDED) {
                val newRoundsCompleted = s.roundsCompleted + 1
                _state.value = s.copy(
                    correctCount = newCorrect,
                    answeredCorrectly = true,
                    levelCorrect = newLevelCorrect,
                    roundsCompleted = newRoundsCompleted,
                    lastRoundWon = true,
                    screen = if (newRoundsCompleted >= ROUNDS_PER_LEVEL) Screen.LEVEL_COMPLETE else Screen.ROUND_COMPLETE,
                )
            } else {
                _state.value = s.copy(
                    correctCount = newCorrect,
                    answeredCorrectly = true,
                    levelCorrect = newLevelCorrect,
                )
            }
            SelectResult.CORRECT
        } else {
            val newWrong = s.wrongAttempts + 1
            val newGreyed = s.greyedOptions + index
            if (newWrong >= 2) {
                val newEscapes = s.escapeCount + 1
                val newLevelEscapes = s.levelEscapes + 1
                if (newEscapes >= ESCAPES_TO_LOSE) {
                    val newRoundsCompleted = s.roundsCompleted + 1
                    _state.value = s.copy(
                        wrongAttempts = newWrong,
                        greyedOptions = newGreyed,
                        escapeCount = newEscapes,
                        levelEscapes = newLevelEscapes,
                        roundsCompleted = newRoundsCompleted,
                        lastRoundWon = false,
                        screen = if (newRoundsCompleted >= ROUNDS_PER_LEVEL) Screen.LEVEL_COMPLETE else Screen.ROUND_COMPLETE,
                    )
                } else {
                    _state.value = s.copy(
                        wrongAttempts = newWrong,
                        greyedOptions = newGreyed,
                        escapeCount = newEscapes,
                        levelEscapes = newLevelEscapes,
                    )
                }
                SelectResult.ESCAPE
            } else {
                _state.value = s.copy(wrongAttempts = newWrong, greyedOptions = newGreyed)
                SelectResult.WRONG
            }
        }
    }
}
