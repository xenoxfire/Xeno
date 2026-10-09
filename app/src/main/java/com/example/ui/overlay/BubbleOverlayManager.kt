package com.example.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import com.example.R
import com.example.data.SettingsRepository
import com.example.model.SettingsModel
import com.example.service.XenoAccessibilityService
import com.example.time.TimeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.hypot

enum class OverlayState {
    MAIN,
    EXPANDED,
    TIME_SIGNS,
    TIME_MINUTES,
    CUSTOM_KEYPAD
}

class BubbleOverlayManager(
    private val context: Context,
    private val onStopRequested: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settingsRepository = SettingsRepository.getInstance(context)

    private var currentSettings = SettingsModel()
    private var currentState = OverlayState.MAIN
    private var isTimeModePlus = true
    private var customMinutesInput = ""

    // Root overlay container
    private var rootLayout: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // Cached reusable view instances per state
    private lateinit var mainBubbleView: BubbleItemView
    private lateinit var expandedContainer: FrameLayout
    private lateinit var timeSignsContainer: FrameLayout
    private lateinit var timeMinutesContainer: FrameLayout
    private lateinit var keypadView: CustomKeypadOverlayView

    // Sub-bubble views for time minutes
    private lateinit var min1Bubble: BubbleItemView
    private lateinit var min2Bubble: BubbleItemView
    private lateinit var min6Bubble: BubbleItemView

    // Touch & Position
    private var bubblePosX = 100
    private var bubblePosY = 300
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var isDragging = false
    private var downTime = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Screen dimensions
    private var screenWidth = 1080
    private var screenHeight = 2400

    fun show() {
        updateScreenDimensions()

        TimeController.onShiftFailedListener = {
            Toast.makeText(context, context.getString(R.string.toast_shizuku_not_ready), Toast.LENGTH_SHORT).show()
        }

        coroutineScope.launch {
            // Load saved position once at startup
            val savedPos = settingsRepository.getSavedBubblePosition()
            if (savedPos != null) {
                bubblePosX = savedPos.first.coerceIn(10, screenWidth - 100)
                bubblePosY = savedPos.second.coerceIn(50, screenHeight - 150)
            }

            initOverlayWindow()

            // Distinct until changed on visual attributes only; position saves never trigger re-renders
            settingsRepository.settingsFlow
                .distinctUntilChanged { old, new ->
                    old.bubbleSizeDp == new.bubbleSizeDp &&
                            old.bubbleOpacity == new.bubbleOpacity &&
                            old.themeStyle == new.themeStyle
                }
                .collect { settings ->
                    currentSettings = settings
                    applySettingsToViews()
                }
        }
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        val defaultDisplay = windowManager.defaultDisplay
        defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun initOverlayWindow() {
        if (rootLayout != null) return

        val bubbleSizePx = dpToPx(currentSettings.bubbleSizeDp.toFloat())

        windowParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            // Retain FLAG_NOT_FOCUSABLE and add FLAG_NOT_TOUCH_MODAL
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = bubblePosX
            y = bubblePosY
            width = bubbleSizePx
            height = bubbleSizePx
        }

        rootLayout = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        buildReusableViews()
        switchState(OverlayState.MAIN)

        try {
            windowManager.addView(rootLayout, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window: ${e.message}", e)
        }
    }

    private fun buildReusableViews() {
        val root = rootLayout ?: return
        val bubbleSizePx = dpToPx(currentSettings.bubbleSizeDp.toFloat())
        val spacingPx = dpToPx(16f)

        // 1. Main Bubble
        mainBubbleView = BubbleItemView(context).apply {
            setup(
                iconType = BubbleItemView.IconType.MAIN_ORB,
                theme = currentSettings.themeStyle,
                opacity = currentSettings.bubbleOpacity
            )
        }
        setupDraggableMainBubble(mainBubbleView)
        root.addView(mainBubbleView, FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx))

        // 2. Expanded Container: [Time] [Paste] [—]
        expandedContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        val timeBubble = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.TIME_CLOCK, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                switchState(OverlayState.TIME_SIGNS)
            }
        }
        val pasteBubble = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.PASTE, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                handlePasteAction()
            }
        }
        val collapseBubble = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.COLLAPSE_DASH, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                switchState(OverlayState.MAIN)
            }
            setOnLongClickListener {
                triggerHaptic()
                onStopRequested()
                true
            }
        }
        populateHorizontalContainer(expandedContainer, listOf(timeBubble, pasteBubble, collapseBubble), bubbleSizePx, spacingPx)
        root.addView(expandedContainer)

        // 3. Time Signs Container: [Back] [+] [−]
        timeSignsContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        val anchorTime = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.BACK_ARROW, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                switchState(OverlayState.EXPANDED)
            }
        }
        val plusBubble = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.PLUS, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                isTimeModePlus = true
                updateTimeMinuteLabels()
                switchState(OverlayState.TIME_MINUTES)
            }
        }
        val minusBubble = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.MINUS, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                isTimeModePlus = false
                updateTimeMinuteLabels()
                switchState(OverlayState.TIME_MINUTES)
            }
        }
        populateHorizontalContainer(timeSignsContainer, listOf(anchorTime, plusBubble, minusBubble), bubbleSizePx, spacingPx)
        root.addView(timeSignsContainer)

        // 4. Time Minutes Container: [Back] [1m] [2m] [6m] [...]
        timeMinutesContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        val anchorBack = BubbleItemView(context).apply {
            setup(BubbleItemView.IconType.BACK_ARROW, currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                switchState(OverlayState.TIME_SIGNS)
            }
        }
        min1Bubble = BubbleItemView(context).apply {
            setupText("+1m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                animateTapPulse(this)
                TimeController.queueShiftMinutes(if (isTimeModePlus) 1 else -1)
            }
        }
        min2Bubble = BubbleItemView(context).apply {
            setupText("+2m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                animateTapPulse(this)
                TimeController.queueShiftMinutes(if (isTimeModePlus) 2 else -2)
            }
        }
        min6Bubble = BubbleItemView(context).apply {
            setupText("+6m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                animateTapPulse(this)
                TimeController.queueShiftMinutes(if (isTimeModePlus) 6 else -6)
            }
        }
        val customBtn = BubbleItemView(context).apply {
            setupText("...", currentSettings.themeStyle, currentSettings.bubbleOpacity)
            setOnClickListener {
                triggerHaptic()
                customMinutesInput = ""
                keypadView.configure(currentSettings.themeStyle, currentSettings.bubbleOpacity, isTimeModePlus)
                switchState(OverlayState.CUSTOM_KEYPAD)
            }
        }
        populateHorizontalContainer(timeMinutesContainer, listOf(anchorBack, min1Bubble, min2Bubble, min6Bubble, customBtn), bubbleSizePx, spacingPx)
        root.addView(timeMinutesContainer)

        // 5. Custom Keypad
        val keypadWidth = dpToPx(240f)
        val keypadHeight = dpToPx(310f)
        keypadView = CustomKeypadOverlayView(
            context = context,
            onDigit = { digit ->
                triggerHaptic()
                if (customMinutesInput.length < 4) customMinutesInput += digit
            },
            onBackspace = {
                triggerHaptic()
                if (customMinutesInput.isNotEmpty()) customMinutesInput = customMinutesInput.dropLast(1)
            },
            onClear = {
                triggerHaptic()
                customMinutesInput = ""
            },
            onConfirm = {
                triggerHaptic()
                val minutes = customMinutesInput.toLongOrNull() ?: 0L
                if (minutes > 0) {
                    TimeController.queueShiftMinutes(if (isTimeModePlus) minutes else -minutes)
                }
                switchState(OverlayState.TIME_MINUTES)
            },
            onBack = {
                triggerHaptic()
                switchState(OverlayState.TIME_MINUTES)
            },
            getInputText = { customMinutesInput }
        ).apply {
            visibility = View.GONE
        }
        root.addView(keypadView, FrameLayout.LayoutParams(keypadWidth, keypadHeight))
    }

    private fun populateHorizontalContainer(container: FrameLayout, views: List<View>, sizePx: Int, spacingPx: Int) {
        var startX = dpToPx(12f)
        val startY = dpToPx(12f)
        views.forEach { v ->
            val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = startX
                topMargin = startY
            }
            container.addView(v, lp)
            startX += sizePx + spacingPx
        }
    }

    private fun updateTimeMinuteLabels() {
        val sign = if (isTimeModePlus) "+" else "-"
        min1Bubble.setupText("${sign}1m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
        min2Bubble.setupText("${sign}2m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
        min6Bubble.setupText("${sign}6m", currentSettings.themeStyle, currentSettings.bubbleOpacity)
    }

    private fun applySettingsToViews() {
        rootLayout?.removeAllViews()
        buildReusableViews()
        switchState(currentState)
    }

    private fun switchState(newState: OverlayState) {
        currentState = newState
        val bubbleSizePx = dpToPx(currentSettings.bubbleSizeDp.toFloat())
        val spacingPx = dpToPx(16f)

        mainBubbleView.visibility = View.GONE
        expandedContainer.visibility = View.GONE
        timeSignsContainer.visibility = View.GONE
        timeMinutesContainer.visibility = View.GONE
        keypadView.visibility = View.GONE

        when (newState) {
            OverlayState.MAIN -> {
                // Restore exact original position when collapsed
                mainBubbleView.visibility = View.VISIBLE
                updateWindowSize(bubbleSizePx, bubbleSizePx, bubblePosX, bubblePosY)
            }

            OverlayState.EXPANDED -> {
                expandedContainer.visibility = View.VISIBLE
                val w = (bubbleSizePx * 3) + (spacingPx * 2) + dpToPx(24f)
                val h = bubbleSizePx + dpToPx(24f)
                openMenuInPlace(w, h)
            }

            OverlayState.TIME_SIGNS -> {
                timeSignsContainer.visibility = View.VISIBLE
                val w = (bubbleSizePx * 3) + (spacingPx * 2) + dpToPx(24f)
                val h = bubbleSizePx + dpToPx(24f)
                openMenuInPlace(w, h)
            }

            OverlayState.TIME_MINUTES -> {
                timeMinutesContainer.visibility = View.VISIBLE
                val w = (bubbleSizePx * 5) + (spacingPx * 4) + dpToPx(24f)
                val h = bubbleSizePx + dpToPx(24f)
                openMenuInPlace(w, h)
            }

            OverlayState.CUSTOM_KEYPAD -> {
                keypadView.visibility = View.VISIBLE
                val w = dpToPx(240f)
                val h = dpToPx(310f)
                openMenuInPlace(w, h)
            }
        }
    }

    private fun openMenuInPlace(menuWidth: Int, menuHeight: Int) {
        // Shift window only if it would go off-screen
        val safeX = bubblePosX.coerceIn(10, (screenWidth - menuWidth - 10).coerceAtLeast(10))
        val safeY = bubblePosY.coerceIn(50, (screenHeight - menuHeight - 50).coerceAtLeast(50))
        updateWindowSize(menuWidth, menuHeight, safeX, safeY)
    }

    private fun updateWindowSize(w: Int, h: Int, x: Int? = null, y: Int? = null) {
        windowParams?.width = w
        windowParams?.height = h
        if (x != null) windowParams?.x = x
        if (y != null) windowParams?.y = y
        try {
            windowManager.updateViewLayout(rootLayout, windowParams)
        } catch (_: Exception) {}
    }

    private fun setupDraggableMainBubble(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialWindowX = windowParams?.x ?: 0
                    initialWindowY = windowParams?.y ?: 0
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    view.isPressed = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val newX = (initialWindowX + dx).toInt().coerceIn(0, screenWidth - v.width)
                        val newY = (initialWindowY + dy).toInt().coerceIn(50, screenHeight - v.height - 100)
                        windowParams?.x = newX
                        windowParams?.y = newY
                        bubblePosX = newX
                        bubblePosY = newY
                        windowManager.updateViewLayout(rootLayout, windowParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && elapsed < 350) {
                        triggerHaptic()
                        switchState(OverlayState.EXPANDED)
                    } else if (isDragging) {
                        bubblePosX = windowParams?.x ?: bubblePosX
                        bubblePosY = windowParams?.y ?: bubblePosY

                        if (currentSettings.snapToEdge) {
                            snapBubbleToEdge()
                        } else {
                            coroutineScope.launch {
                                settingsRepository.saveBubblePosition(bubblePosX, bubblePosY)
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapBubbleToEdge() {
        val bubbleSizePx = dpToPx(currentSettings.bubbleSizeDp.toFloat())
        val currentX = windowParams?.x ?: 0
        val targetX = if (currentX + bubbleSizePx / 2 < screenWidth / 2) 20 else screenWidth - bubbleSizePx - 20

        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 180
        animator.interpolator = OvershootInterpolator(1.1f)
        animator.addUpdateListener { anim ->
            windowParams?.x = anim.animatedValue as Int
            windowManager.updateViewLayout(rootLayout, windowParams)
        }
        animator.start()
        bubblePosX = targetX
        coroutineScope.launch {
            settingsRepository.saveBubblePosition(targetX, bubblePosY)
        }
    }

    private fun animateTapPulse(view: View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(90)
                    .start()
            }
            .start()
    }

    private fun handlePasteAction() {
        coroutineScope.launch {
            val savedText = currentSettings.savedText
            val service = XenoAccessibilityService.instance
            if (service != null) {
                val pasted = service.pasteText(savedText)
                if (pasted) {
                    if (currentSettings.collapseAfterPaste) {
                        switchState(OverlayState.MAIN)
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_tap_input_first), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.toast_tap_input_first), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerHaptic() {
        if (!currentSettings.hapticFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            }
        } catch (_: Exception) {}
    }

    fun destroy() {
        coroutineScope.cancel()
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        rootLayout = null
    }

    companion object {
        private const val TAG = "BubbleOverlayManager"
    }
}
