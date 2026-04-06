package com.example.aiconversation.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Detailed 21-Viseme Enum ────────────────────────────────────────────────────

/**
 * 21 detailed mouth shapes exported from Spine (or equivalent).
 * The names correspond to the primary phoneme each shape represents.
 */
enum class DetailedViseme {
    // Silence / rest
    REST,           // 0 — mouth closed, resting

    // Bilabials
    M_B_P,          // 1 — lips pressed together
    W,              // 2 — small rounded

    // Labiodentals
    F_V,            // 3 — upper teeth on lower lip

    // Dentals
    TH,             // 4 — tongue tip near upper teeth

    // Alveolars
    D_T_N,          // 5 — tongue tip to alveolar ridge
    L,              // 6 — tongue tip raised
    S_Z,            // 7 — teeth close, air through narrow gap

    // Palatals / Postalveolars
    SH_CH,          // 8 — rounded lips, teeth close
    J_Y,            // 9 — tongue near palate

    // Velars
    K_G,            // 10 — tongue back raised
    NG,             // 11 — nasal, tongue back

    // Rounded vowels
    OH,             // 12 — round, medium open (O)
    OO,             // 13 — round, very small (U / OO)

    // Open vowels
    AH,             // 14 — wide open (A, Ä)
    AA,             // 15 — open, slightly backer

    // Front vowels
    EH,             // 16 — mid front (E, short)
    AY,             // 17 — mid front diphthong (A-Y)
    IY,             // 18 — high front (EE, I)
    IH,             // 19 — high front, lax (short I)

    // Retroflex / R-coloured
    RR              // 20 — retroflex R
}

// ── Weights Map Type ───────────────────────────────────────────────────────────

/** Maps each [DetailedViseme] to a blend weight in [0.0, 1.0]. */
typealias VisemeWeights = Map<DetailedViseme, Float>

private fun zeroWeights() = DetailedViseme.entries.associateWith { 0f }
private fun restWeights() = zeroWeights().toMutableMap().also { it[DetailedViseme.REST] = 1f }

// ── Core Viseme (5 types) ──────────────────────────────────────────────────────

/**
 * Simplified 5-type classification derived from audio features.
 * Stable enough for reliable real-time detection.
 */
enum class CoreViseme {
    REST,   // silence
    OPEN,   // A, AH — wide open mouth
    WIDE,   // E, I  — wide but less vertical
    ROUND,  // O, U  — round/small opening
    CLOSED  // M, B, P — lips together (fricatives, plosives)
}

// ── Expanded Viseme Tables ─────────────────────────────────────────────────────

/**
 * Each [CoreViseme] distributes weights across multiple [DetailedViseme]s.
 * Weights within each table should sum to ≤ 1.0 (remainder appears as REST).
 */
private val EXPANSION_TABLES: Map<CoreViseme, Map<DetailedViseme, Float>> = mapOf(

    CoreViseme.REST to mapOf(
        DetailedViseme.REST to 1.0f
    ),

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

// ── LipSyncEngine ─────────────────────────────────────────────────────────────

/**
 * Converts [AudioFeatures] from [AudioAnalyzer] into smoothed [VisemeWeights].
 *
 * Processing pipeline:
 *  1. [AudioFeatures] → [CoreViseme] via threshold rules
 *  2. [CoreViseme] → raw [VisemeWeights] via expansion table
 *  3. Raw weights are exponentially blended with previous frame (smoothing)
 *  4. Smoothed [VisemeWeights] exposed via [visemeWeights] StateFlow
 *
 * @param smoothingFactor How much to retain the previous frame [0=instant, 1=never change].
 *                        Default 0.55 ≈ ~50 ms lag at 30fps.
 */
class LipSyncEngine(
    private val audioAnalyzer: AudioAnalyzer,
    private val smoothingFactor: Float = 0.55f
) {
    companion object {
        private const val TAG = "LipSyncEngine"

        // Thresholds for CoreViseme classification
        private const val SILENCE_RMS = 0.02f
        private const val OPEN_RMS = 0.25f
        private const val WIDE_RMS = 0.08f
        private const val ROUND_RMS = 0.06f
        private const val CLOSED_RMS = 0.04f

        // High-frequency bias means E/I sounds (WIDE)
        private const val HIGH_BIAS = 0.35f

        // Low-frequency dominance means O/U sounds (ROUND)
        private const val LOW_BIAS = 0.40f
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

    private val _visemeWeights = MutableStateFlow<VisemeWeights>(restWeights())
    val visemeWeights: StateFlow<VisemeWeights> = _visemeWeights.asStateFlow()

    private val _currentCore = MutableStateFlow(CoreViseme.REST)
    val currentCore: StateFlow<CoreViseme> = _currentCore.asStateFlow()

    private var smoothedWeights: MutableMap<DetailedViseme, Float> = restWeights().toMutableMap()

    // ── Public API ─────────────────────────────────────────────────────────────

    fun start() {
        if (processingJob?.isActive == true) return
        processingJob = scope.launch { processLoop() }
        Log.d(TAG, "LipSyncEngine started")
    }

    fun stop() {
        processingJob?.cancel()
        processingJob = null
        smoothedWeights = restWeights().toMutableMap()
        _visemeWeights.value = restWeights()
        _currentCore.value = CoreViseme.REST
        Log.d(TAG, "LipSyncEngine stopped")
    }

    /** Force-set weights from external volume source (e.g. STT RMS). */
    fun updateFromRMS(rms: Float) {
        val core = if (rms > 0.1f) CoreViseme.OPEN else CoreViseme.REST
        _currentCore.value = core
        val targetWeights = EXPANSION_TABLES[core] ?: restWeights()
        // Scale weights by the volume for natural movement
        val scaled = targetWeights.mapValues { it.value * (rms * 2f).coerceIn(0f, 1f) }
        applySmoothing(scaled)
    }

    /** Override with fully REST weights (silence / idle). */
    fun setRest() {
        _currentCore.value = CoreViseme.REST
        applySmoothing(restWeights())
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private suspend fun processLoop() {
        audioAnalyzer.features.collect { features ->
            if (!audioAnalyzer.isRunning) return@collect

            val core = classify(features)
            _currentCore.value = core

            val targetWeights = EXPANSION_TABLES[core] ?: restWeights()
            applySmoothing(targetWeights)
        }
    }

    private fun classify(f: AudioFeatures): CoreViseme {
        if (f.isSilent || f.rms < SILENCE_RMS) return CoreViseme.REST

        return when {
            f.rms >= OPEN_RMS -> CoreViseme.OPEN

            f.rms >= WIDE_RMS && f.highEnergy > HIGH_BIAS -> CoreViseme.WIDE

            f.rms >= ROUND_RMS && f.lowEnergy > LOW_BIAS -> CoreViseme.ROUND

            f.rms < CLOSED_RMS -> CoreViseme.CLOSED

            else -> CoreViseme.OPEN  // fallback: some speech sound
        }.also {
            Log.v(
                TAG, "rms=${f.rms.format(3)} lo=${f.lowEnergy.format(3)} " +
                        "hi=${f.highEnergy.format(3)} → $it"
            )
        }
    }

    private fun applySmoothing(target: Map<DetailedViseme, Float>) {
        val alpha = 1f - smoothingFactor   // larger alpha = faster response
        for (viseme in DetailedViseme.entries) {
            val targetVal = target[viseme] ?: 0f
            val prev = smoothedWeights[viseme] ?: 0f
            smoothedWeights[viseme] = prev + alpha * (targetVal - prev)
        }
        _visemeWeights.value = smoothedWeights.toMap()
    }

    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
}
