package com.wordpolice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─── Top-level composable ────────────────────────────────────────────────────

@Composable
fun GameApp(viewModel: GameViewModel, soundManager: SoundManager) {
    val state by viewModel.state.collectAsState()

    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackGood by remember { mutableStateOf(true) }
    var feedbackVisible by remember { mutableStateOf(false) }
    var feedbackTick by remember { mutableStateOf(0) }

    // Auto-hide feedback
    LaunchedEffect(feedbackTick) {
        if (feedbackTick > 0) { delay(1900); feedbackVisible = false }
    }

    // Auto-speak new word
    LaunchedEffect(state.question?.word) {
        if (state.screen == Screen.PLAYING) {
            delay(400)
            state.question?.word?.let { soundManager.speakWord(it) }
        }
    }

    // Auto-advance after correct
    LaunchedEffect(state.answeredCorrectly) {
        if (state.answeredCorrectly && state.screen == Screen.PLAYING) {
            delay(1200)
            viewModel.nextQuestion()
        }
    }

    // Round / level audio
    LaunchedEffect(state.screen, state.roundsCompleted) {
        when (state.screen) {
            Screen.ROUND_COMPLETE -> if (state.lastRoundWon) {
                delay(300); soundManager.playWinSiren()
                delay(800); soundManager.speakPhrase("Got them! Keep going!")
            } else {
                soundManager.playLose()
                delay(500); soundManager.speakPhrase("Oh no, they got away!")
            }
            Screen.LEVEL_COMPLETE -> {
                delay(300)
                if (state.scorePercent >= 80) {
                    soundManager.playWinSiren()
                    delay(800); soundManager.speakPhrase("Amazing! Level complete!")
                } else {
                    soundManager.speakPhrase("Well done! Keep practising!")
                }
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFF5BA8D4))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ScoreBar(correctCount = state.correctCount, escapeCount = state.escapeCount)

            Spacer(Modifier.height(8.dp))

            RoadScene(
                policeFraction = state.policeFraction,
                criminalFraction = state.criminalFraction,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Spacer(Modifier.height(8.dp))

            if (state.screen == Screen.PLAYING && state.question != null) {
                val q = state.question!!

                SpeakerButton(
                    onClick = { soundManager.speakWord(q.word) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                q.options.forEachIndexed { index, option ->
                    val isGreyed = index in state.greyedOptions
                    val isCorrect = state.answeredCorrectly && index == q.correctIndex
                    val clickable = !isGreyed && !state.answeredCorrectly
                    val bg = when { isGreyed -> Color(0xFFEEEEEE); isCorrect -> Color(0xFFC8E6C9); else -> Color.White }
                    val border = when { isGreyed -> Color(0xFFBBBBBB); isCorrect -> Color(0xFF2E7D32); else -> Color(0xFFCCCCCC) }
                    val textCol = when { isGreyed -> Color(0xFFAAAAAA); isCorrect -> Color(0xFF1B5E20); else -> Color(0xFF1A1A2E) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .border(3.dp, border, RoundedCornerShape(16.dp))
                            .then(
                                if (clickable) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    when (viewModel.selectOption(index)) {
                                        SelectResult.CORRECT -> {
                                            soundManager.playCorrect()
                                            feedbackMsg = "✅"; feedbackGood = true; feedbackVisible = true; feedbackTick++
                                        }
                                        SelectResult.WRONG -> {
                                            soundManager.playWrong()
                                            feedbackMsg = "❌"; feedbackGood = false; feedbackVisible = true; feedbackTick++
                                        }
                                        SelectResult.ESCAPE -> {
                                            soundManager.playEscape()
                                            feedbackMsg = "💨"; feedbackGood = false; feedbackVisible = true; feedbackTick++
                                        }
                                        SelectResult.NONE -> {}
                                    }
                                }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = textCol)
                    }

                    if (index < q.options.lastIndex) Spacer(Modifier.height(8.dp))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        FeedbackBanner(visible = feedbackVisible, message = feedbackMsg, isGood = feedbackGood)

        when (state.screen) {
            Screen.LEVEL_SELECT -> LevelSelectOverlay(onSelectLevel = { viewModel.selectLevel(it) })
            Screen.ROUND_COMPLETE -> RoundCompleteOverlay(
                roundsCompleted = state.roundsCompleted,
                levelCorrect = state.levelCorrect,
                levelEscapes = state.levelEscapes,
                lastRoundWon = state.lastRoundWon,
                onNext = { viewModel.startNextRound() }
            )
            Screen.LEVEL_COMPLETE -> LevelCompleteOverlay(
                state = state,
                onReplay = { viewModel.replayLevel() },
                onNextLevel = { viewModel.selectLevel(state.levelIndex + 1) },
                onChooseLevel = { viewModel.backToLevelSelect() }
            )
            Screen.PLAYING -> {}
        }
    }
}

// ─── Score bar ───────────────────────────────────────────────────────────────

@Composable
fun ScoreBar(correctCount: Int, escapeCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            repeat(CORRECT_NEEDED) { i ->
                Text("⭐", fontSize = 26.sp,
                    color = if (i < correctCount) Color.Unspecified else Color.White.copy(alpha = 0.3f))
            }
        }
        Row {
            repeat(ESCAPES_TO_LOSE) { i ->
                Text("💨", fontSize = 26.sp,
                    color = if (i < escapeCount) Color.Unspecified else Color.White.copy(alpha = 0.3f))
            }
        }
    }
}

// ─── Road scene ──────────────────────────────────────────────────────────────

@Composable
fun RoadScene(policeFraction: Float, criminalFraction: Float, modifier: Modifier = Modifier) {
    val animPolice by animateFloatAsState(
        targetValue = policeFraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "police"
    )
    val animCriminal by animateFloatAsState(
        targetValue = criminalFraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "criminal"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
    ) {
        val w = maxWidth
        val h = maxHeight
        // Grass starts at 75% height; offset cars so they sit on the road surface
        val carY = -(h.value * 0.27f).dp

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoad()
        }

        // Police car – starts left, chases right
        Text(
            text = "🚓",
            fontSize = 64.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (w.value * animPolice - 32f).dp, y = carY)
                .graphicsLayer { scaleX = -1f }
        )

        // Criminal car – ahead of police, flees right
        Text(
            text = "🚗",
            fontSize = 64.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (w.value * animCriminal - 32f).dp, y = carY)
                .graphicsLayer { scaleX = -1f }
        )
    }
}

private fun DrawScope.drawRoad() {
    // Sky (35%)
    drawRect(color = Color(0xFF87CEEB), size = Size(size.width, size.height * 0.35f))
    // Road surface (35%–75%)
    drawRect(
        color = Color(0xFF555555),
        topLeft = Offset(0f, size.height * 0.35f),
        size = Size(size.width, size.height * 0.40f)
    )
    // Yellow centre-line dashes
    var x = 0f
    while (x < size.width) {
        drawRect(
            color = Color(0xFFFFD700),
            topLeft = Offset(x, size.height * 0.55f),
            size = Size(46f, 6f)
        )
        x += 92f
    }
    // Grass verge at bottom (75%–100%)
    drawRect(
        color = Color(0xFF4A7A2E),
        topLeft = Offset(0f, size.height * 0.75f),
        size = Size(size.width, size.height * 0.25f)
    )
}

// ─── Speaker button ──────────────────────────────────────────────────────────

@Composable
fun SpeakerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
    ) {
        Text("🔊", fontSize = 36.sp)
    }
}

// ─── Feedback banner ─────────────────────────────────────────────────────────

@Composable
fun FeedbackBanner(visible: Boolean, message: String, isGood: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isGood) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

// ─── Overlay screens ─────────────────────────────────────────────────────────

@Composable
fun LevelSelectOverlay(onSelectLevel: (Int) -> Unit) {
    val levelColors = listOf(Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFE65100), Color(0xFF6A1B9A))
    GameOverlay {
        Text("🚓", fontSize = 64.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text("🔊", fontSize = 28.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 16.dp))
        WORD_ROUNDS.forEachIndexed { index, level ->
            BigButton("${level.emoji}   ${index + 1}", levelColors[index]) { onSelectLevel(index) }
            if (index < WORD_ROUNDS.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun RoundCompleteOverlay(
    roundsCompleted: Int,
    levelCorrect: Int,
    levelEscapes: Int,
    lastRoundWon: Boolean,
    onNext: () -> Unit
) {
    GameOverlay {
        Text(if (lastRoundWon) "🚓" else "💨", fontSize = 64.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text("$roundsCompleted / $ROUNDS_PER_LEVEL", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⭐", fontSize = 36.sp)
                Text("$levelCorrect", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💨", fontSize = 36.sp)
                Text("$levelEscapes", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB71C1C))
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton("→", Color(0xFF1565C0), onNext)
    }
}

@Composable
fun LevelCompleteOverlay(state: GameState, onReplay: () -> Unit, onNextLevel: () -> Unit, onChooseLevel: () -> Unit) {
    val percent = state.scorePercent
    val stars = when { percent >= 80 -> 3; percent >= 60 -> 2; else -> 1 }
    GameOverlay {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            repeat(3) { i -> Text("⭐", fontSize = 40.sp, color = if (i < stars) Color.Unspecified else Color.Gray.copy(alpha = 0.2f)) }
        }
        Text("$percent%", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp),
            color = when { percent >= 80 -> Color(0xFF2E7D32); percent >= 60 -> Color(0xFFE65100); else -> Color(0xFFB71C1C) })
        Row(modifier = Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("⭐ ${state.levelCorrect}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            Text("💨 ${state.levelEscapes}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
        }
        if (state.hasNextLevel) { BigButton("→", Color(0xFF1565C0), onNextLevel); Spacer(Modifier.height(10.dp)) }
        BigButton("🔄", Color(0xFFB71C1C), onReplay)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onChooseLevel, shape = RoundedCornerShape(50.dp), modifier = Modifier.fillMaxWidth()) {
            Text("🏠", fontSize = 28.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
fun GameOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
fun BigButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
    }
}
