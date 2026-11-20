package com.sushant.inknote.ml

import android.graphics.Bitmap
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sushant.inknote.data.Stroke
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Offline handwriting → text. The English pack downloads once (about 20 MB), then works with no internet. */
object Handwriting {
    private val model: DigitalInkRecognitionModel? by lazy {
        runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") }.getOrNull()?.let { DigitalInkRecognitionModel.builder(it).build() }
    }
    private val recognizer by lazy { model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) } }

    suspend fun isDownloaded(): Boolean = model?.let { runCatching { RemoteModelManager.getInstance().isModelDownloaded(it).await() }.getOrDefault(false) } ?: false

    /** Returns null on success, or a plain-words error. */
    suspend fun download(): String? {
        val m = model ?: return "Handwriting reading isn't available on this phone"
        return runCatching { RemoteModelManager.getInstance().download(m, DownloadConditions.Builder().build()).await(); null }
            .getOrElse { "Couldn't download the English handwriting pack. Connect to the internet once and try again." }
    }

    /** Recognises strokes line by line (top to bottom). */
    suspend fun recognize(strokes: List<Stroke>): String? {
        val rec = recognizer ?: return null
        if (strokes.isEmpty() || !isDownloaded()) return null
        val lines = groupLines(strokes)
        val out = StringBuilder()
        for (line in lines) {
            val ink = Ink.builder()
            var t = 0L
            for (s in line) {
                val sb = Ink.Stroke.builder()
                for (i in 0 until s.size) sb.addPoint(Ink.Point.create(s.x(i), s.y(i), t++ * 10))
                ink.addStroke(sb.build())
            }
            val r = runCatching { withTimeoutOrNull(8000) { rec.recognize(ink.build()).await() } }.getOrNull()
            val text = r?.candidates?.firstOrNull()?.text ?: continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(text)
        }
        return out.toString().trim().ifEmpty { null }
    }

    /** Groups strokes into text lines by vertical overlap. */
    fun groupLines(strokes: List<Stroke>): List<List<Stroke>> {
        val sorted = strokes.filter { it.size > 0 }.sortedBy { it.bounds()[1] }
        val lines = ArrayList<MutableList<Stroke>>(); val ranges = ArrayList<FloatArray>()
        for (s in sorted) {
            val b = s.bounds(); val cy = (b[1] + b[3]) / 2
            val idx = ranges.indexOfFirst { cy in it[0]..it[1] }
            if (idx >= 0) { lines[idx] += s; ranges[idx][0] = minOf(ranges[idx][0], b[1]); ranges[idx][1] = maxOf(ranges[idx][1], b[3]) }
            else { lines += mutableListOf(s); ranges += floatArrayOf(b[1], b[3]) }
        }
        return lines.map { l -> l.sortedBy { it.bounds()[0] } }
    }
}

/** Offline text recognition for photos and PDF pages (bundled Latin model). */
object Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bmp: Bitmap): String = runCatching {
        withTimeoutOrNull(15000) { client.process(InputImage.fromBitmap(bmp, 0)).await().text } ?: ""
    }.getOrDefault("")
}
