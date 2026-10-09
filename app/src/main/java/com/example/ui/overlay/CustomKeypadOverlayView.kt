package com.example.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.example.model.ThemeStyle

class CustomKeypadOverlayView(
    context: Context,
    private val onDigit: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClear: () -> Unit,
    private val onConfirm: () -> Unit,
    private val onBack: () -> Unit,
    private val getInputText: () -> String
) : View(context) {

    private var theme: ThemeStyle = ThemeStyle.NEON_DARK
    private var opacity: Float = 0.95f
    private var isPlusMode: Boolean = true

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val rootRect = RectF()
    private val textBounds = Rect()

    data class KeyButton(
        val label: String,
        val rect: RectF = RectF(),
        val action: () -> Unit,
        var isPressed: Boolean = false
    )

    private val buttons = ArrayList<KeyButton>(14)
    private var activePressedBtn: KeyButton? = null

    init {
        isFocusable = false
        isClickable = true
    }

    fun configure(theme: ThemeStyle, opacity: Float, isPlusMode: Boolean) {
        this.theme = theme
        this.opacity = opacity
        this.isPlusMode = isPlusMode
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupButtons(w.toFloat(), h.toFloat())
    }

    private fun setupButtons(w: Float, h: Float) {
        buttons.clear()
        val margin = 12f
        val topHeaderHeight = 54f
        val gridTop = topHeaderHeight + 10f
        val bottomActionsHeight = 44f
        val gridHeight = h - gridTop - bottomActionsHeight - margin * 2

        val cols = 3
        val rows = 4
        val colWidth = (w - margin * 2 - (cols - 1) * 8f) / cols
        val rowHeight = (gridHeight - (rows - 1) * 8f) / rows

        val keyLabels = arrayOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "C", "0", "⌫"
        )

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val index = r * cols + c
                val label = keyLabels[index]
                val left = margin + c * (colWidth + 8f)
                val top = gridTop + r * (rowHeight + 8f)
                val rect = RectF(left, top, left + colWidth, top + rowHeight)

                val action: () -> Unit = when (label) {
                    "C" -> ({ onClear(); invalidate() })
                    "⌫" -> ({ onBackspace(); invalidate() })
                    else -> ({ onDigit(label); invalidate() })
                }
                buttons.add(KeyButton(label, rect, action))
            }
        }

        val bottomY = h - bottomActionsHeight - margin
        val halfW = (w - margin * 2 - 8f) / 2f
        buttons.add(
            KeyButton(
                label = "Back",
                rect = RectF(margin, bottomY, margin + halfW, bottomY + bottomActionsHeight),
                action = onBack
            )
        )
        buttons.add(
            KeyButton(
                label = "Apply ✓",
                rect = RectF(margin + halfW + 8f, bottomY, w - margin, bottomY + bottomActionsHeight),
                action = onConfirm
            )
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val btn = buttons.firstOrNull { it.rect.contains(event.x, event.y) }
                if (btn != null) {
                    btn.isPressed = true
                    activePressedBtn = btn
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                activePressedBtn?.let { btn ->
                    val inside = btn.rect.contains(event.x, event.y)
                    if (btn.isPressed != inside) {
                        btn.isPressed = inside
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                activePressedBtn?.let { btn ->
                    if (btn.rect.contains(event.x, event.y)) {
                        btn.action.invoke()
                    }
                    btn.isPressed = false
                    activePressedBtn = null
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                activePressedBtn?.let {
                    it.isPressed = false
                    activePressedBtn = null
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        rootRect.set(4f, 4f, w - 4f, h - 4f)
        val effectiveAlpha = (opacity * 255).toInt().coerceIn(40, 255)
        val contentColor = ThemeDrawer.getContentColor(theme)

        bgPaint.style = Paint.Style.FILL
        bgPaint.color = 0xFF0B0E14.toInt()
        bgPaint.alpha = effectiveAlpha
        canvas.drawRoundRect(rootRect, 24f, 24f, bgPaint)

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 3f
        borderPaint.color = if (theme == ThemeStyle.NEON_DARK) 0xFF00F2FE.toInt() else 0x88CBD5E1.toInt()
        borderPaint.alpha = effectiveAlpha
        canvas.drawRoundRect(rootRect, 24f, 24f, borderPaint)

        val currentInput = getInputText()
        val sign = if (isPlusMode) "+" else "-"
        val displayStr = "$sign ${if (currentInput.isEmpty()) "0" else currentInput} min"

        headerPaint.textSize = 24f
        headerPaint.color = contentColor
        headerPaint.alpha = effectiveAlpha
        headerPaint.getTextBounds(displayStr, 0, displayStr.length, textBounds)
        canvas.drawText(displayStr, w / 2f, 38f, headerPaint)

        textPaint.textSize = 20f
        textPaint.color = contentColor
        textPaint.alpha = effectiveAlpha

        for (btn in buttons) {
            buttonBgPaint.style = Paint.Style.FILL
            buttonBgPaint.color = if (btn.isPressed) 0xFF27272A.toInt() else 0xFF18181B.toInt()
            buttonBgPaint.alpha = effectiveAlpha
            canvas.drawRoundRect(btn.rect, 14f, 14f, buttonBgPaint)

            buttonBorderPaint.style = Paint.Style.STROKE
            buttonBorderPaint.strokeWidth = 1.5f
            buttonBorderPaint.color = if (btn.isPressed) contentColor else 0x4471717A.toInt()
            buttonBorderPaint.alpha = effectiveAlpha
            canvas.drawRoundRect(btn.rect, 14f, 14f, buttonBorderPaint)

            textPaint.getTextBounds(btn.label, 0, btn.label.length, textBounds)
            val ty = btn.rect.centerY() - textBounds.exactCenterY()
            canvas.drawText(btn.label, btn.rect.centerX(), ty, textPaint)
        }
    }
}
