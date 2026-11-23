package com.cybersuraksha.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device OCR for the Message Detector's "upload photo/video" flow — a Kotlin counterpart to
 * site/js/ocr-upload.js, but using ML Kit's bundled Latin + Devanagari text recognizers instead
 * of Tesseract.js (both fully on-device, no network, matching this app's offline-first rule).
 * Runs on a background thread; call off the main thread (e.g. from a coroutine or executor).
 */
object OcrUpload {

    /** Recognizes text from a single image/photo Uri. */
    fun recognizeImage(context: Context, uri: Uri): String {
        val bitmap = loadBitmap(context, uri) ?: return ""
        return recognizeBitmap(bitmap)
    }

    /** Grabs a few frames from a video Uri and OCRs each, merging the deduped lines — mirrors
     *  the website's "play the video, grab a frame every ~600ms" approach (fixed-duration seeks
     *  were found unreliable on some recordings there too). */
    fun recognizeVideo(context: Context, uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        val lines = LinkedHashSet<String>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val timestamps = if (durationMs > 0) {
                listOf(durationMs * 150, durationMs * 500, durationMs * 850).map { it / 1000 }
            } else {
                listOf(0L, 600_000L, 1_200_000L)
            }
            for (tUs in timestamps) {
                val frame = try {
                    retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) { null } ?: continue
                val text = recognizeBitmap(frame)
                text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
            }
        } catch (e: Exception) {
            // leave lines as whatever was collected so far
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        return lines.joinToString("\n")
    }

    private fun recognizeBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val latinText = try { Tasks.await(latinRecognizer.process(image)).text } catch (e: Exception) { "" }
        val devText = try { Tasks.await(devanagariRecognizer.process(image)).text } catch (e: Exception) { "" }

        val lines = LinkedHashSet<String>()
        (latinText.lines() + devText.lines())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { lines.add(it) }
        return lines.joinToString("\n")
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }
}
