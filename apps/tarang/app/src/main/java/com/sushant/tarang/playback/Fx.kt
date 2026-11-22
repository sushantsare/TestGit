package com.sushant.tarang.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.*

data class FxParams(
    val enabled: Boolean = false,
    val bandsDb: List<Float> = List(10) { 0f },
    val bassBoost: Int = 0,      // 0..100
    val virtualizer: Int = 0,    // 0..100
)

/** RBJ cookbook biquad. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun peaking(fs: Double, f0: Double, q: Double, db: Double) {
        val a = 10.0.pow(db / 40); val w = 2 * PI * f0 / fs; val al = sin(w) / (2 * q)
        set(1 + al * a, -2 * cos(w), 1 - al * a, 1 + al / a, -2 * cos(w), 1 - al / a)
    }
    fun lowShelf(fs: Double, f0: Double, db: Double) {
        val a = 10.0.pow(db / 40); val w = 2 * PI * f0 / fs; val cs = cos(w)
        val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        set(a * ((a + 1) - (a - 1) * cs + sa), 2 * a * ((a - 1) - (a + 1) * cs), a * ((a + 1) - (a - 1) * cs - sa),
            (a + 1) + (a - 1) * cs + sa, -2 * ((a - 1) + (a + 1) * cs), (a + 1) + (a - 1) * cs - sa)
    }
    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    /** Magnitude in dB at frequency f (used by tests and the EQ curve). */
    fun responseDb(fs: Double, f: Double): Double {
        val w = 2 * PI * f / fs
        val nr = b0 + b1 * cos(w) + b2 * cos(2 * w); val ni = -(b1 * sin(w) + b2 * sin(2 * w))
        val dr = 1 + a1 * cos(w) + a2 * cos(2 * w); val di = -(a1 * sin(w) + a2 * sin(2 * w))
        return 20 * log10(sqrt(nr * nr + ni * ni) / sqrt(dr * dr + di * di))
    }
}

/**
 * Tarang's own sound processing: 10-band equalizer, bass boost and a stereo virtualizer, applied inside the player.
 * (Many phones' built-in equalizer has only 5 bands, so the 10 bands are done here.)
 */
@UnstableApi
class TarangFx : BaseAudioProcessor() {
    @Volatile private var params = FxParams()
    @Volatile private var dirty = true
    private var fs = 44100.0
    private var ch = 2
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var bass: Array<Biquad> = emptyArray()
    private var preGain = 1.0

    fun update(p: FxParams) { if (p != params) { params = p; dirty = true } }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        fs = inputAudioFormat.sampleRate.toDouble(); ch = inputAudioFormat.channelCount
        dirty = true
        return inputAudioFormat
    }

    private fun rebuild() {
        val p = params
        filters = Array(ch) { Array(10) { Biquad() } }
        bass = Array(ch) { Biquad() }
        val hz = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        for (c in 0 until ch) for (b in 0 until 10) {
            val f = min(hz[b], fs * 0.45)
            filters[c][b].peaking(fs, f, 1.2, p.bandsDb.getOrElse(b) { 0f }.toDouble())
        }
        val bassDb = p.bassBoost / 100.0 * 12.0
        for (c in 0 until ch) bass[c].lowShelf(fs, 110.0, bassDb)
        // keep loud boosts from clipping: lower the input by the biggest boost (half of it; the soft limiter does the rest)
        val maxBoost = max(p.bandsDb.maxOrNull()?.toDouble() ?: 0.0, 0.0) + bassDb * 0.6
        preGain = 10.0.pow(-maxBoost / 2 / 20)
        dirty = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val p = params
        val virtualOn = p.virtualizer > 0 && ch == 2
        if (!p.enabled && !virtualOn && p.bassBoost == 0) { out.put(inputBuffer); out.flip(); return }
        if (dirty) rebuild()
        val width = 1.0 + p.virtualizer / 100.0 * 0.8
        val frames = size / (2 * ch)
        val tmp = DoubleArray(ch)
        repeat(frames) {
            for (c in 0 until ch) {
                var x = inputBuffer.short / 32768.0 * preGain
                if (p.enabled) for (b in 0 until 10) x = filters[c][b].process(x)
                if (p.bassBoost > 0) x = bass[c].process(x)
                tmp[c] = x
            }
            if (virtualOn) {
                val m = (tmp[0] + tmp[1]) / 2; val s = (tmp[0] - tmp[1]) / 2 * width
                tmp[0] = m + s; tmp[1] = m - s
            }
            for (c in 0 until ch) out.putShort((softClip(tmp[c]) * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
        }
        // leftover bytes (never expected for whole frames) pass through
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    private fun softClip(x: Double) = if (abs(x) < 0.9) x else sign(x) * (0.9 + 0.1 * tanh((abs(x) - 0.9) / 0.1))

    override fun onReset() { filters = emptyArray(); bass = emptyArray(); dirty = true }
}
