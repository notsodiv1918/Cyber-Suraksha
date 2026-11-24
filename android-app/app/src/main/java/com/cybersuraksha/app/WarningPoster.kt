package com.cybersuraksha.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Turns a scam verdict into a simple, big-text poster-style image the user can
 *  forward to a family WhatsApp group in one tap — every catch becomes free
 *  community protection. Deliberately plain (no photos/branding assets needed):
 *  a colour banner, one big warning line, and the message text underneath. */
object WarningPoster {

    private const val W = 900
    private const val H = 1100

    fun generate(context: Context, verdict: String, messageText: String): Bitmap {
        val bannerColor = when (verdict) {
            "scam" -> Color.parseColor("#C62828")
            "suspicious" -> Color.parseColor("#9A6700")
            else -> Color.parseColor("#0C6B05")
        }
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val bannerHeight = 260f
        canvas.drawRect(0f, 0f, W.toFloat(), bannerHeight, Paint().apply { color = bannerColor })

        val warnPaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val headline = when (verdict) {
            "scam" -> T.posterHeadlineScam
            else -> T.posterHeadlineSuspicious
        }
        drawWrapped(canvas, headline, W / 2f, 110f, warnPaint, W - 80f, 54f)

        val subPaint = Paint(warnPaint).apply { textSize = 26f; typeface = Typeface.DEFAULT }
        canvas.drawText(T.posterSub, W / 2f, bannerHeight - 30f, subPaint)

        val cardPaint = Paint().apply { color = Color.parseColor("#F4F6F9") }
        val cardRect = RectF(50f, bannerHeight + 50f, W - 50f, H - 160f)
        canvas.drawRoundRect(cardRect, 16f, 16f, cardPaint)

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1B2430")
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val shown = if (messageText.length > 220) messageText.take(220) + "…" else messageText
        drawWrapped(canvas, "“$shown”", cardRect.left + 30f, cardRect.top + 60f, bodyPaint, cardRect.width() - 60f, 40f, Paint.Align.LEFT)

        val footerPaint = Paint().apply {
            color = Color.parseColor("#5A6472")
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(T.posterFooter, W / 2f, H - 60f, footerPaint)

        return bitmap
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        paint: Paint,
        maxWidth: Float,
        lineHeight: Float,
        align: Paint.Align = Paint.Align.CENTER,
    ) {
        paint.textAlign = align
        val words = text.split(" ")
        var line = StringBuilder()
        var y = startY
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                line = StringBuilder(word)
                y += lineHeight
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line.toString(), x, y, paint)
    }

    /** Saves to cache and returns a share Intent via FileProvider. */
    fun shareIntent(context: Context, verdict: String, messageText: String): Intent {
        val bitmap = generate(context, verdict, messageText)
        val dir = File(context.cacheDir, "posters").apply { mkdirs() }
        val file = File(dir, "cyber-suraksha-warning-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, out) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
