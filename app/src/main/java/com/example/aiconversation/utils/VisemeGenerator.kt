package com.example.aiconversation.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Viseme types for lip-sync.
 */
enum class Viseme {
    CLOSED,   // M, B, P
    OPEN,     // A, E, I
    O_SHAPE,  // O, U, W
    WIDE,     // S, T, D, N, Z
    SLIGHTLY_OPEN, // Default / neutral
    F_V,      // F, V
    TH,       // TH
    L_R,      // L, R
    SH_CH     // SH, CH, J
}

/**
 * Generates viseme data aligned with speech.
 * Maps characters/phonemes to visemes.
 */
class VisemeGenerator {

    private val _currentViseme = MutableStateFlow(Viseme.SLIGHTLY_OPEN)
    val currentViseme: StateFlow<Viseme> = _currentViseme.asStateFlow()

    private var visemeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Estimates visemes for a word and animates through them.
     */
    fun startVisemesForWord(word: String, durationMs: Long) {
        visemeJob?.cancel()
        visemeJob = scope.launch {
            val cleanWord = word.lowercase().filter { it.isLetter() }
            if (cleanWord.isEmpty()) {
                _currentViseme.value = Viseme.SLIGHTLY_OPEN
                return@launch
            }

            // Approximate visemes based on vowels/consonants
            val visemes = mutableListOf<Viseme>()
            var i = 0
            while (i < cleanWord.length) {
                val char = cleanWord[i]
                val nextChar = if (i + 1 < cleanWord.length) cleanWord[i + 1] else null

                if (char == 't' && nextChar == 'h') {
                    visemes.add(Viseme.TH)
                    i += 2
                    continue
                }
                if ((char == 's' && nextChar == 'h') || (char == 'c' && nextChar == 'h')) {
                    visemes.add(Viseme.SH_CH)
                    i += 2
                    continue
                }

                val v = when (char) {
                    'a', 'e', 'i' -> Viseme.OPEN
                    'o', 'u', 'w' -> Viseme.O_SHAPE
                    'm', 'b', 'p' -> Viseme.CLOSED
                    'f', 'v' -> Viseme.F_V
                    'l', 'r' -> Viseme.L_R
                    's', 't', 'd', 'n', 'z', 'k', 'g', 'j', 'c' -> Viseme.WIDE
                    else -> Viseme.SLIGHTLY_OPEN
                }
                visemes.add(v)
                i++
            }

            if (visemes.isEmpty()) return@launch

            val delayPerViseme = maxOf(10, durationMs / visemes.size)

            visemes.forEach { viseme ->
                _currentViseme.value = viseme
                delay(delayPerViseme)
            }

            // Return to neutral after word
            _currentViseme.value = Viseme.SLIGHTLY_OPEN
        }
    }

    fun stop() {
        visemeJob?.cancel()
        _currentViseme.value = Viseme.CLOSED
    }
}
