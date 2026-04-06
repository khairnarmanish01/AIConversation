package com.example.aiconversation.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio feature snapshot emitted every analysis frame (~30fps).
 *
 * @param rms          Normalized root-mean-square energy [0.0 – 1.0]. Drives mouth openness.
 * @param lowEnergy    Normalized energy in low-frequency band (bass/rounded vowels like O, U).
 * @param midEnergy    Normalized energy in mid-frequency band (voiced consonants).
 * @param highEnergy   Normalized energy in high-frequency band (sibilants, bright vowels E, I).
 * @param isSilent     True when RMS is below the silence threshold.
 */
data class AudioFeatures(
    val rms: Float = 0f,
    val lowEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val highEnergy: Float = 0f,
    val isSilent: Boolean = true
)

/**
 * Captures microphone audio and analyses it in real-time.
 *
 * Runs its own [CoroutineScope] on [Dispatchers.IO] so it never blocks the main thread.
 * Call [start] to begin capture and [stop] to release resources.
 */
class AudioAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 16_000          // 16 kHz — adequate for speech
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ANALYSIS_INTERVAL_MS = 33L    // ~30fps
        private const val SILENCE_THRESHOLD = 0.015f    // RMS below this → silence

        // Number of FFT bins — must be power of two
        private const val FFT_SIZE = 512
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _features = MutableStateFlow(AudioFeatures())
    val features: StateFlow<AudioFeatures> = _features.asStateFlow()

    val isRunning: Boolean get() = captureJob?.isActive == true

    // ── Public API ─────────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        if (!hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted — analyzer not started")
            return
        }
        captureJob = scope.launch { runCapture() }
        Log.d(TAG, "AudioAnalyzer started")
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _features.value = AudioFeatures()
        Log.d(TAG, "AudioAnalyzer stopped")
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private suspend fun runCapture() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuf, FFT_SIZE * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return
        }

        audioRecord = record
        record.startRecording()

        val buffer = ShortArray(FFT_SIZE)
        val fftInput = FloatArray(FFT_SIZE)

        while (captureJob?.isActive == true) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                // Convert shorts → floats in [-1, 1]
                for (i in 0 until read) {
                    fftInput[i] = buffer[i] / 32768f
                }

                val rms = computeRms(fftInput, read)
                val fftOut = computeFft(fftInput)
                val bands = extractBands(fftOut)

                _features.value = AudioFeatures(
                    rms = rms.coerceIn(0f, 1f),
                    lowEnergy = bands[0].coerceIn(0f, 1f),
                    midEnergy = bands[1].coerceIn(0f, 1f),
                    highEnergy = bands[2].coerceIn(0f, 1f),
                    isSilent = rms < SILENCE_THRESHOLD
                )
            }
            delay(ANALYSIS_INTERVAL_MS)
        }

        record.stop()
        record.release()
    }

    /** Root-mean-square over [count] samples, normalised to [0, 1]. */
    private fun computeRms(samples: FloatArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) sum += samples[i] * samples[i]
        val raw = sqrt(sum / count).toFloat()
        // Apply modest gain so quiet voices register, but cap at 1
        return (raw * 8f).coerceIn(0f, 1f)
    }

    /**
     * Lightweight Cooley-Tukey FFT (in-place, radix-2, DIF).
     * Returns magnitude spectrum (only positive frequencies, length = FFT_SIZE/2).
     */
    private fun computeFft(samples: FloatArray): FloatArray {
        val n = FFT_SIZE
        val real = samples.copyOf(n)
        val imag = FloatArray(n)

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
            }
        }

        // FFT butterfly
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val wRe = FloatArray(halfLen)
            val wIm = FloatArray(halfLen)
            for (k in 0 until halfLen) {
                val angle = -2.0 * Math.PI * k / len
                wRe[k] = kotlin.math.cos(angle).toFloat()
                wIm[k] = kotlin.math.sin(angle).toFloat()
            }
            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val uRe = real[i + k]
                    val uIm = imag[i + k]
                    val vRe = real[i + k + halfLen] * wRe[k] - imag[i + k + halfLen] * wIm[k]
                    val vIm = real[i + k + halfLen] * wIm[k] + imag[i + k + halfLen] * wRe[k]
                    real[i + k] = uRe + vRe
                    imag[i + k] = uIm + vIm
                    real[i + k + halfLen] = uRe - vRe
                    imag[i + k + halfLen] = uIm - vIm
                }
                i += len
            }
            len *= 2
        }

        // Magnitude spectrum (positive frequencies only)
        val half = n / 2
        val mag = FloatArray(half)
        for (i in 0 until half) {
            mag[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]) / half
        }
        return mag
    }

    /**
     * Divide the magnitude spectrum into 3 perceptual bands.
     * At 16kHz with 512-point FFT, each bin = 16000/512 ≈ 31.25 Hz.
     *  - Low : 0–500 Hz   → bins 0–16
     *  - Mid : 500–2000Hz → bins 16–64
     *  - High: 2000Hz+    → bins 64–256
     */
    private fun extractBands(mag: FloatArray): FloatArray {
        val binHz = SAMPLE_RATE.toFloat() / FFT_SIZE
        val lowEnd = (500f / binHz).toInt().coerceIn(1, mag.size - 1)
        val midEnd = (2000f / binHz).toInt().coerceIn(lowEnd + 1, mag.size - 1)
        val highEnd = mag.size

        fun avg(from: Int, to: Int): Float {
            if (from >= to) return 0f
            var s = 0f
            for (i in from until to) s += abs(mag[i])
            return (s / (to - from)) * 20f  // scale up for usable range
        }

        return floatArrayOf(
            avg(0, lowEnd),
            avg(lowEnd, midEnd),
            avg(midEnd, highEnd)
        )
    }
}
