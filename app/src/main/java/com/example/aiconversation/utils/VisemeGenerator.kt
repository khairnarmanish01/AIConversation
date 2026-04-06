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
 * Legacy core viseme enum — kept for backward compatibility with existing code.
 * New code should prefer [DetailedViseme] + [VisemeWeights] from LipSyncEngine.kt.
 */
enum class Viseme {
    CLOSED,        // M, B, P
    OPEN,          // A, E, I
    O_SHAPE,       // O, U, W
    WIDE,          // S, T, D, N, Z
    SLIGHTLY_OPEN, // Default / neutral
    F_V,           // F, V
    TH,            // TH
    L_R,           // L, R
    SH_CH          // SH, CH, J
}

/**
 * Maps characters/phonemes to visemes for TTS word-callback driven lip sync.
 *
 * In addition to the legacy [Viseme] enum this class now also emits
 * [VisemeWeights] (21-entry map) via [currentVisemeWeights] so the new
 * PNG-based avatar can blend detailed mouth shapes.
 *
 * The weights are derived from the same expansion tables used by [LipSyncEngine].
 */
class VisemeGenerator {

    // ── Legacy state (kept for component compatibility) ────────────────────────
    private val _currentViseme = MutableStateFlow(Viseme.SLIGHTLY_OPEN)
    val currentViseme: StateFlow<Viseme> = _currentViseme.asStateFlow()

    // ── New 21-viseme weight state ─────────────────────────────────────────────
    private val _currentVisemeWeights = MutableStateFlow<VisemeWeights>(restWeightsMap())
    val currentVisemeWeights: StateFlow<VisemeWeights> = _currentVisemeWeights.asStateFlow()

    private var visemeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // ── Expansion tables (matches LipSyncEngine) ───────────────────────────────
    private val coreToWeights: Map<CoreViseme, VisemeWeights> = mapOf(
        CoreViseme.REST to mapOf(DetailedViseme.REST to 1.0f),

        CoreViseme.OPEN to mapOf(
            DetailedViseme.AH to 0.55f,
            DetailedViseme.AA to 0.25f,
            DetailedViseme.AY to 0.10f,
            DetailedViseme.EH to 0.10f
        ),

        CoreViseme.WIDE to mapOf(
            DetailedViseme.IY to 0.40f,
            DetailedViseme.IH to 0.25f,
            DetailedViseme.EH to 0.20f,
            DetailedViseme.AY to 0.10f,
            DetailedViseme.S_Z to 0.05f
        ),

        CoreViseme.ROUND to mapOf(
            DetailedViseme.OH to 0.45f,
            DetailedViseme.OO to 0.35f,
            DetailedViseme.W to 0.15f,
            DetailedViseme.SH_CH to 0.05f
        ),

        CoreViseme.CLOSED to mapOf(
            DetailedViseme.M_B_P to 0.50f,
            DetailedViseme.F_V to 0.20f,
            DetailedViseme.TH to 0.15f,
            DetailedViseme.D_T_N to 0.10f,
            DetailedViseme.REST to 0.05f
        )
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Estimates visemes for a word and animates through them.
     * Updates both [currentViseme] and [currentVisemeWeights].
     */
    fun startVisemesForWord(word: String, durationMs: Long) {
        visemeJob?.cancel()
        visemeJob = scope.launch {
            val cleanWord = word.lowercase().filter { it.isLetter() }
            if (cleanWord.isEmpty()) {
                _currentViseme.value = Viseme.SLIGHTLY_OPEN
                _currentVisemeWeights.value = restWeightsMap()
                return@launch
            }

            val visemes = mutableListOf<Viseme>()
            var i = 0
            while (i < cleanWord.length) {
                val char = cleanWord[i]
                val next = if (i + 1 < cleanWord.length) cleanWord[i + 1] else null

                if (char == 't' && next == 'h') {
                    visemes.add(Viseme.TH); i += 2; continue
                }
                if ((char == 's' && next == 'h') || (char == 'c' && next == 'h')) {
                    visemes.add(Viseme.SH_CH); i += 2; continue
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

            val delayPerViseme = maxOf(10L, durationMs / visemes.size)

            visemes.forEach { viseme ->
                _currentViseme.value = viseme
                _currentVisemeWeights.value = expandViseme(viseme)
                delay(delayPerViseme)
            }

            _currentViseme.value = Viseme.SLIGHTLY_OPEN
            _currentVisemeWeights.value = restWeightsMap()
        }
    }

    fun stop() {
        visemeJob?.cancel()
        _currentViseme.value = Viseme.CLOSED
        _currentVisemeWeights.value = closedWeightsMap()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun expandViseme(v: Viseme): VisemeWeights {
        val core = when (v) {
            Viseme.CLOSED, Viseme.F_V, Viseme.TH -> CoreViseme.CLOSED
            Viseme.OPEN -> CoreViseme.OPEN
            Viseme.O_SHAPE, Viseme.SH_CH -> CoreViseme.ROUND
            Viseme.WIDE, Viseme.L_R -> CoreViseme.WIDE
            Viseme.SLIGHTLY_OPEN -> CoreViseme.REST
        }
        return coreToWeights[core] ?: restWeightsMap()
    }

    private fun restWeightsMap(): VisemeWeights =
        DetailedViseme.entries.associateWith { if (it == DetailedViseme.REST) 1f else 0f }

    private fun closedWeightsMap(): VisemeWeights =
        DetailedViseme.entries.associateWith { if (it == DetailedViseme.M_B_P) 1f else 0f }
}

/** Helper used outside the class. */
fun restWeightsMap(): VisemeWeights =
    DetailedViseme.entries.associateWith { if (it == DetailedViseme.REST) 1f else 0f }
