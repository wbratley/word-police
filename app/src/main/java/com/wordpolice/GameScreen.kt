package com.wordpolice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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

    // Win / lose audio
    LaunchedEffect(state.screen) {
        when (state.screen) {
            Screen.WIN -> {
                delay(300)
                soundManager.playWinSiren()
                delay(800)
                soundManager.speakPhrase("You caught them! Brilliant spelling!")
            }
            Screen.LOSE -> {
                soundManager.playLose()
                delay(500)
                soundManager.speakPhrase("Oh no, they got away! Keep practising!")
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFF5BA8D4))))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🚓 Word Police",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1A2E),
                modifier = Modifier.padding(top = 8.dp)
            )

            if (state.screen != Screen.START) {
                Text(
                    text = "${state.currentRound.emoji}  ${state.currentRound.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E).copy(alpha = 0.75f)
                )
            }

            ScoreRow(correctCount = state.correctCount, escapeCount = state.escapeCount)
            RoadScene(policeFraction = state.policeFraction, criminalFraction = state.criminalFraction)
            ProgressRow(correctCount = state.correctCount)

            if (state.screen == Screen.PLAYING && state.question != null) {
                QuestionCard(
                    question = state.question!!,
                    greyedOptions = state.greyedOptions,
                    answeredCorrectly = state.answeredCorrectly,
                    onSpeak = { state.question?.word?.let { soundManager.speakWord(it) } },
                    onSelect = { index ->
                        when (viewModel.selectOption(index)) {
                            SelectResult.CORRECT -> {
                                soundManager.playCorrect()
                                feedbackMsg = "✅ Correct! Well done!"
                                feedbackGood = true; feedbackVisible = true; feedbackTick++
                            }
                            SelectResult.WRONG -> {
                                soundManager.playWrong()
                                feedbackMsg = "❌ Not quite — try again!"
                                feedbackGood = false; feedbackVisible = true; feedbackTick++
                            }
                            SelectResult.ESCAPE -> {
                                soundManager.playEscape()
                                feedbackMsg = "❌ Two wrong! Criminal is getting away!"
                                feedbackGood = false; feedbackVisible = true; feedbackTick++
                            }
                            SelectResult.NONE -> {}
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Feedback banner slides in from top
        FeedbackBanner(visible = feedbackVisible, message = feedbackMsg, isGood = feedbackGood)

        // Overlay screens
        when (state.screen) {
            Screen.START -> StartOverlay(onStart = { viewModel.startGame(0) })
            Screen.WIN -> WinOverlay(
                hasNextRound = state.hasNextRound,
                onNextRound = { viewModel.advanceRound() },
                onReplay = { viewModel.replayRound() }
            )
            Screen.LOSE -> LoseOverlay(onRetry = { viewModel.replayRound() })
            Screen.PLAYING -> {}
        }
    }
}

// ─── Score row ───────────────────────────────────────────────────────────────

@Composable
fun ScoreRow(correctCount: Int, escapeCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚓 Catches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Row {
                    repeat(CORRECT_NEEDED) { i ->
                        Text(
                            "⭐",
                            fontSize = 18.sp,
                            color = if (i < correctCount) Color.Unspecified else Color.Gray.copy(alpha = 0.25f)
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(1.dp).height(38.dp).background(Color(0xFFDDDDDD)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💨 Escapes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFc62828))
                Row {
                    repeat(ESCAPES_TO_LOSE) { i ->
                        Text(
                            "💨",
                            fontSize = 18.sp,
                            color = if (i < escapeCount) Color.Unspecified else Color.Gray.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Road scene ──────────────────────────────────────────────────────────────

@Composable
fun RoadScene(policeFraction: Float, criminalFraction: Float) {
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
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
    ) {
        val w = maxWidth // Dp

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoad()
        }

        // Chase label
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text("🚨 POLICE PURSUIT 🚨", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
        }

        // Police car – starts left, chases right
        Text(
            text = "🚓",
            fontSize = 32.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (w.value * animPolice - 18f).dp, y = (-6).dp)
        )

        // Criminal car – ahead of police, flees right
        Text(
            text = "🚗",
            fontSize = 32.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (w.value * animCriminal - 18f).dp, y = (-6).dp)
        )
    }
}

private fun DrawScope.drawRoad() {
    // Sky
    drawRect(color = Color(0xFF87CEEB), size = Size(size.width, size.height * 0.44f))
    // Ground strip below road
    drawRect(
        color = Color(0xFF4A7A2E),
        topLeft = Offset(0f, size.height * 0.85f),
        size = Size(size.width, size.height * 0.15f)
    )
    // Road surface
    drawRect(
        color = Color(0xFF555555),
        topLeft = Offset(0f, size.height * 0.44f),
        size = Size(size.width, size.height * 0.41f)
    )
    // Yellow centre-line dashes
    var x = 0f
    while (x < size.width) {
        drawRect(
            color = Color(0xFFFFD700),
            topLeft = Offset(x, size.height * 0.625f),
            size = Size(46f, 5f)
        )
        x += 92f
    }
}

// ─── Progress bar ────────────────────────────────────────────────────────────

@Composable
fun ProgressRow(correctCount: Int) {
    val progress by animateFloatAsState(
        targetValue = correctCount.toFloat() / CORRECT_NEEDED,
        animationSpec = tween(500),
        label = "progress"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                color = Color(0xFF2196F3),
                trackColor = Color(0xFFE0E0E0)
            )
            Text("$correctCount / $CORRECT_NEEDED correct", fontSize = 12.sp, color = Color(0xFF666666), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ─── Question card ───────────────────────────────────────────────────────────

@Composable
fun QuestionCard(
    question: Question,
    greyedOptions: Set<Int>,
    answeredCorrectly: Boolean,
    onSpeak: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Listen to the word, then choose the correct spelling!",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Button(
                onClick = onSpeak,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Text(
                    "🔊  Hear the word",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEachIndexed { index, option ->
                    val isGreyed = index in greyedOptions
                    val isCorrectAndDone = answeredCorrectly && index == question.correctIndex

                    val bg = when {
                        isGreyed -> Color(0xFFEEEEEE)
                        isCorrectAndDone -> Color(0xFFC8E6C9)
                        else -> Color(0xFFF5F5F5)
                    }
                    val border = when {
                        isGreyed -> Color(0xFFBBBBBB)
                        isCorrectAndDone -> Color(0xFF2E7D32)
                        else -> Color(0xFFCCCCCC)
                    }
                    val textCol = when {
                        isGreyed -> Color(0xFFAAAAAA)
                        isCorrectAndDone -> Color(0xFF1B5E20)
                        else -> Color(0xFF1A1A2E)
                    }
                    val clickable = !isGreyed && !answeredCorrectly

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(2.dp, border, RoundedCornerShape(12.dp))
                            .then(
                                if (clickable) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(index) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textCol, textAlign = TextAlign.Center)
                    }
                }
            }
        }
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
fun StartOverlay(onStart: () -> Unit) {
    GameOverlay {
        Text("🚓", fontSize = 56.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text("Word Police!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            "Listen to the word, then pick the right spelling.\n\nGet 5 correct in a row to catch the criminal!",
            textAlign = TextAlign.Center,
            color = Color(0xFF555555),
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("🔊  Turn your sound on!", fontSize = 13.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 24.dp))
        BigButton("Start Chase! 🚨", Color(0xFF2E7D32), onStart)
    }
}

@Composable
fun WinOverlay(hasNextRound: Boolean, onNextRound: () -> Unit, onReplay: () -> Unit) {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(350); phase = 1
        delay(550); phase = 2
        delay(600); phase = 3
        delay(550); phase = 4
    }

    GameOverlay {
        Text("🎉", fontSize = 48.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text("Caught them!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))

        // Arrest animation sequence
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            AnimatedVisibility(visible = phase >= 1, enter = slideInHorizontally { -80 } + fadeIn()) {
                Text("🚓", fontSize = 38.sp)
            }
            AnimatedVisibility(visible = phase >= 2, enter = fadeIn(tween(300))) {
                Text("  →  ", fontSize = 20.sp, color = Color(0xFF666666))
            }
            AnimatedVisibility(visible = phase >= 3, enter = slideInHorizontally { 80 } + fadeIn()) {
                Text("🧑‍🦲", fontSize = 38.sp)
            }
            AnimatedVisibility(visible = phase >= 4, enter = scaleIn(tween(350))) {
                Text("  🔒", fontSize = 38.sp)
            }
        }

        Text(
            "Amazing spelling! The criminal is going to jail!",
            textAlign = TextAlign.Center,
            color = Color(0xFF555555),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (hasNextRound) {
            BigButton("Next Round! ➡️", Color(0xFF1565C0), onNextRound)
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onReplay,
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Play Again", fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun LoseOverlay(onRetry: () -> Unit) {
    GameOverlay {
        Text("😬", fontSize = 48.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(
            "They got away!",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFB71C1C),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "The criminals escaped this time...\nBut the police never give up!\n\nTry again and get all 5 right!",
            textAlign = TextAlign.Center,
            color = Color(0xFF555555),
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        BigButton("Try Again! 🚓", Color(0xFFB71C1C), onRetry)
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
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
    }
}
