package com.wordpolice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.wordpolice.ui.theme.WordPoliceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)

        setContent {
            WordPoliceTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF87CEEB)) {
                    GameApp(viewModel = viewModel, soundManager = soundManager)
                }
            }
        }
    }

    override fun onDestroy() {
        soundManager.shutdown()
        super.onDestroy()
    }
}
