package com.example.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.example.model.ThemeStyle

class BubbleItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class IconType {
        MAIN_ORB,
        TIME_CLOCK,
        PASTE,
        COLLAPSE_DASH,
        BACK_ARROW,
        PLUS,
        MINUS,
        TEXT
    }

    private var iconType: IconType = IconType.MAIN_ORB
    private var customText: String = ""
    private var theme: ThemeStyle = ThemeStyle.NEON_DARK
    private var opacity: Float = 0.95f

    // Cached paints & objects - zero allocation in onDraw
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val boundsRect = RectF()
    private val clipRect = RectF()
    private val clipTopRect = RectF()
    private val arrowPath = Path()
    private val textBounds = Rect()

    private var cachedBgShader: Shader? = null
    private var cachedStrokeShader: Shader? = null

    init {
        isClickable = true
        isFocusable = false
    }

    fun setup(iconType: IconType, theme: ThemeStyle, opacity: Float) {
        this.iconType = iconType
        this.theme = theme
        this.opacity = opacity
        rebuildPaintsAndShaders()
        invalidate()
    }

    fun setupText(text: String, theme: ThemeStyle, opacity: Float) {
        this.iconType = IconType.TEXT
        this.customText = text
        this.theme = theme
        this.opacity = opacity
        rebuildPaintsAndShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPaintsAndShaders()
    }

    private fun rebuildPaintsAndShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        boundsRect.set(4f, 4f, w - 4f, h - 4f)
        cachedBgShader = ThemeDrawer.createBackgroundShader(theme, boundsRect)
        cachedStrokeShader = ThemeDrawer.createStrokeShader(theme, boundsRect)

        val cx = boundsRect.centerX()
        val cy = boundsRect.centerY()
        val size = boundsRect.width()
        val pw = size * 0.32f
        val ph = size * 0.40f

        clipRect.set(cx - pw / 2f, cy - ph / 2f + size * 0.04f, cx + pw / 2f, cy + ph / 2f)
        clipTopRect.set(cx - pw * 0.35f, cy - ph / 2f - size * 0.02f, cx + pw * 0.35f, cy - ph / 2f + size * 0.06f)

        val arrowSize = size * 0.22f
        arrowPath.reset()
        arrowPath.moveTo(cx + arrowSize * 0.6f, cy - arrowSize)
        arrowPath.lineTo(cx - arrowSize * 0.6f, cy)
        arrowPath.lineTo(cx + arrowSize * 0.6f, cy + arrowSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = boundsRect.centerX()
        val cy = boundsRect.centerY()
        val radius = boundsRect.width() / 2f
        val size = boundsRect.width()
        val effectiveAlpha = (opacity * 255).toInt().coerceIn(20, 255)

        // Draw background
        when (theme) {
            ThemeStyle.GLASS -> {
                bgPaint.shader = null
                bgPaint.style = Paint.Style.FILL
                bgPaint.color = if (isPressed) 0x66FFFFFF else 0x33CBD5E1
                bgPaint.alpha = ((bgPaint.alpha / 255f) * effectiveAlpha).toInt()
                canvas.drawCircle(cx, cy, radius, bgPaint)

                strokePaint.shader = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 3f
                strokePaint.color = if (isPressed) 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt()
                strokePaint.alpha = ((strokePaint.alpha / 255f) * effectiveAlpha).toInt()
                canvas.drawCircle(cx, cy, radius - 1.5f, strokePaint)
            }

            ThemeStyle.NEON_DARK -> {
                bgPaint.shader = null
                bgPaint.style = Paint.Style.FILL
                bgPaint.color = if (isPressed) 0xFF161F30.toInt() else 0xFF0B0E14.toInt()
                bgPaint.alpha = effectiveAlpha
                canvas.drawCircle(cx, cy, radius, bgPaint)

                strokePaint.shader = cachedStrokeShader
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 4.5f
                strokePaint.alpha = effectiveAlpha
                canvas.drawCircle(cx, cy, radius - 2.25f, strokePaint)
            }

            ThemeStyle.MINIMAL_MONO -> {
                bgPaint.shader = null
                bgPaint.style = Paint.Style.FILL
                bgPaint.color = if (isPressed) 0xFF27272A.toInt() else 0xFF18181B.toInt()
                bgPaint.alpha = effectiveAlpha
                canvas.drawCircle(cx, cy, radius, bgPaint)

                strokePaint.shader = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 2f
                strokePaint.color = if (isPressed) 0xFFFFFFFF.toInt() else 0xFF52525B.toInt()
                strokePaint.alpha = effectiveAlpha
                canvas.drawCircle(cx, cy, radius - 1f, strokePaint)
            }

            ThemeStyle.AURORA -> {
                bgPaint.shader = cachedBgShader
                bgPaint.style = Paint.Style.FILL
                bgPaint.alpha = effectiveAlpha
                canvas.drawCircle(cx, cy, radius, bgPaint)

                strokePaint.shader = null
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 3f
                strokePaint.color = 0xAAFFFFFF.toInt()
                strokePaint.alpha = ((strokePaint.alpha / 255f) * effectiveAlpha).toInt()
                canvas.drawCircle(cx, cy, radius - 1.5f, strokePaint)
            }
        }

        val contentColor = ThemeDrawer.getContentColor(theme)
        iconPaint.color = contentColor
        iconPaint.alpha = effectiveAlpha
        iconPaint.strokeWidth = (size * 0.07f).coerceAtLeast(3.5f)

        textPaint.color = contentColor
        textPaint.alpha = effectiveAlpha
        textPaint.textSize = size * 0.36f

        when (iconType) {
            IconType.MAIN_ORB -> {
                iconPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, size * 0.16f, iconPaint)
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = size * 0.05f
                canvas.drawCircle(cx, cy, size * 0.28f, iconPaint)
            }

            IconType.TIME_CLOCK -> {
                val r = size * 0.26f
                iconPaint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, r, iconPaint)
                canvas.drawLine(cx, cy, cx, cy - r * 0.65f, iconPaint)
                canvas.drawLine(cx, cy, cx + r * 0.55f, cy, iconPaint)
            }

            IconType.PASTE -> {
                iconPaint.style = Paint.Style.STROKE
                canvas.drawRoundRect(clipRect, 6f, 6f, iconPaint)
                canvas.drawRoundRect(clipTopRect, 4f, 4f, iconPaint)

                val pw = size * 0.32f
                val ph = size * 0.40f
                val lineY1 = cy - ph * 0.05f
                val lineY2 = cy + ph * 0.20f
                canvas.drawLine(cx - pw * 0.25f, lineY1, cx + pw * 0.25f, lineY1, iconPaint)
                canvas.drawLine(cx - pw * 0.25f, lineY2, cx + pw * 0.10f, lineY2, iconPaint)
            }

            IconType.COLLAPSE_DASH -> {
                val len = size * 0.36f
                canvas.drawLine(cx - len / 2f, cy, cx + len / 2f, cy, iconPaint)
            }

            IconType.BACK_ARROW -> {
                canvas.drawPath(arrowPath, iconPaint)
            }

            IconType.PLUS -> {
                val len = size * 0.32f
                canvas.drawLine(cx - len / 2f, cy, cx + len / 2f, cy, iconPaint)
                canvas.drawLine(cx, cy - len / 2f, cx, cy + len / 2f, iconPaint)
            }

            IconType.MINUS -> {
                val len = size * 0.32f
                canvas.drawLine(cx - len / 2f, cy, cx + len / 2f, cy, iconPaint)
            }

            IconType.TEXT -> {
                textPaint.getTextBounds(customText, 0, customText.length, textBounds)
                val textY = cy - textBounds.exactCenterY()
                canvas.drawText(customText, cx, textY, textPaint)
            }
        }
    }
}
