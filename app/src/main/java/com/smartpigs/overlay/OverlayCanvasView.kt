package com.smartpigs.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

class OverlayCanvasView(context: Context) : View(context) {
    lateinit var engine: PigEngine
    var pigBitmap: Bitmap? = null
    var extraBitmaps: MutableMap<String, Bitmap> = mutableMapOf()
    var passThrough = true

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C2255C")
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val thoughtBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val thoughtStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val thoughtText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        textSize = 32f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 64f
        textAlign = Paint.Align.LEFT
    }
    private val gooPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8FB3") }
    private val ropePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 140, 72, 32)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val matrix = Matrix()
    private val tmpRect = RectF()

    var onPigDown: ((Pig, Float, Float) -> Unit)? = null
    var onPigMove: ((Pig, Float, Float) -> Unit)? = null
    var onPigUp: ((Pig) -> Unit)? = null
    var onItemDown: ((WorldItem, Float, Float) -> Unit)? = null
    var onItemMove: ((WorldItem, Float, Float) -> Unit)? = null
    var onItemUp: ((WorldItem) -> Unit)? = null
    var draggingPig: Pig? = null
    var draggingItem: WorldItem? = null

    override fun onDraw(canvas: Canvas) {
        val eng = if (this::engine.isInitialized) engine else return
        val bmp = pigBitmap ?: return

        if (eng.modifiers["rope"] == true) {
            val x1 = if (eng.pointerActive) eng.pointerX else eng.bubbleX
            val y1 = if (eng.pointerActive) eng.pointerY else eng.bubbleY
            for (pig in eng.pigs) {
                val x2 = pig.x + eng.pigW() / 2
                val y2 = pig.y + 18
                val cx = (x1 + x2) / 2
                val cy = maxOf(y1, y2) + 40
                val path = android.graphics.Path()
                path.moveTo(x1, y1)
                path.quadTo(cx, cy, x2, y2)
                canvas.drawPath(path, ropePaint)
            }
        }

        for (pig in eng.pigs) {
            if (pig.goo) {
                gooPaint.color = Color.parseColor("#FF8FB3")
                for (b in pig.blobs) canvas.drawCircle(b.x, b.y, b.r, gooPaint)
                continue
            }
            val src = canvasBitmapFor(pig, bmp)
            val w = eng.pigW()
            val aspect = if (src.width > 0) src.height.toFloat() / src.width.toFloat() else 1.35f
            val h = w * aspect
            val scale = if (src.width > 0) w / src.width.toFloat() else 1f
            val spinning = eng.modifiers["noGravity"] == true || pig.ragdoll
            matrix.reset()
            matrix.postTranslate(-src.width / 2f, -src.height / 2f)
            matrix.postScale(scale * pig.direction, scale)
            if (spinning) {
                matrix.postRotate(pig.angle * 57.2958f)
            }
            matrix.postTranslate(pig.x + w / 2f, pig.y + h / 2f + pig.bob)
            bitmapPaint.alpha = if (eng.modifiers["ghost"] == true) 140 else 255
            if (eng.modifiers["blank"] == true) bitmapPaint.alpha = 210
            canvas.drawBitmap(src, matrix, bitmapPaint)

            if (eng.modifiers["nameTags"] == true && eng.modifiers["blank"] != true) {
                val tw = tagText.measureText(pig.name) + 24
                tmpRect.set(pig.x + w / 2 - tw / 2, pig.y + pig.bob - 44, pig.x + w / 2 + tw / 2, pig.y + pig.bob - 12)
                canvas.drawRoundRect(tmpRect, 16f, 16f, tagBg)
                canvas.drawText(pig.name, pig.x + w / 2, pig.y + pig.bob - 20, tagText)
            }
            pig.thought?.let { text ->
                if (eng.modifiers["blank"] == true) return@let
                val tw = thoughtText.measureText(text) + 28
                val top = pig.y + pig.bob - 100
                tmpRect.set(pig.x + w / 2 - tw / 2, top, pig.x + w / 2 + tw / 2, top + 44)
                canvas.drawRoundRect(tmpRect, 22f, 22f, thoughtBg)
                canvas.drawRoundRect(tmpRect, 22f, 22f, thoughtStroke)
                canvas.drawText(text, pig.x + w / 2, top + 32, thoughtText)
            }
            if (pig.heartTimer > 0) {
                emojiPaint.textSize = 40f
                canvas.drawText("♥", pig.x + w / 2 - 12, pig.y + pig.bob - 8, emojiPaint)
            }
        }

        emojiPaint.textSize = 64f
        for (it in eng.treats + eng.toys) {
            canvas.save()
            canvas.rotate(it.rot, it.x + 14, it.y + 14)
            canvas.drawText(it.emoji, it.x, it.y + 48, emojiPaint)
            canvas.restore()
        }
    }

    private fun canvasBitmapFor(pig: Pig, fallback: Bitmap): Bitmap {
        extraBitmaps[pig.imageUrl]?.let { return it }
        extraBitmaps[engine.imageUrl]?.let { return it }
        return fallback
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val eng = if (this::engine.isInitialized) engine else return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pig = eng.hitPig(x, y)
                if (pig != null) {
                    draggingPig = pig
                    onPigDown?.invoke(pig, x, y)
                    return true
                }
                val item = eng.hitItem(x, y)
                if (item != null) {
                    draggingItem = item
                    onItemDown?.invoke(item, x, y)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                draggingPig?.let { onPigMove?.invoke(it, x, y); return true }
                draggingItem?.let { onItemMove?.invoke(it, x, y); return true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingPig?.let { onPigUp?.invoke(it); draggingPig = null; return true }
                draggingItem?.let { onItemUp?.invoke(it); draggingItem = null; return true }
            }
        }
        return draggingPig != null || draggingItem != null
    }
}
