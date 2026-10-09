package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.time.TimeController
import kotlinx.coroutines.runBlocking

enum class PasteResult {
    PASTED_DIRECTLY,
    COPIED_TO_CLIPBOARD,
    FAILED
}

class XenoAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastFocusedNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "XenoAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val source = event.source ?: return
                try {
                    if (source.isFocused || source.isEditable || isInputClass(source) || hasAction(source, AccessibilityNodeInfo.ACTION_PASTE)) {
                        synchronized(this) {
                            try {
                                lastFocusedNode?.recycle()
                            } catch (_: Exception) {}
                            lastFocusedNode = AccessibilityNodeInfo.obtain(source)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error caching focused node: ${e.message}")
                }
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "XenoAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(this) {
            try {
                lastFocusedNode?.recycle()
            } catch (_: Exception) {}
            lastFocusedNode = null
        }
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "XenoAccessibilityService destroyed")
    }

    /**
     * Pastes [text] into the currently focused or active input field in any app.
     * Uses a multi-tiered strategy:
     * 1. Clipboard copy
     * 2. Cached focused node injection (ACTION_PASTE / ACTION_SET_TEXT)
     * 3. System-wide active input search across all windows
     * 4. Shizuku hardware paste fallback (input keyevent 279)
     */
    fun pasteText(text: String): PasteResult {
        // Step 1: Ensure text is in the system clipboard
        val clipboardSuccess = copyToClipboard(text)

        // Step 2: Try injecting into cached last focused node
        synchronized(this) {
            val cached = lastFocusedNode
            if (cached != null) {
                try {
                    if (cached.refresh() && performPasteOnNode(cached, text)) {
                        Log.d(TAG, "Pasted successfully via cached lastFocusedNode")
                        return PasteResult.PASTED_DIRECTLY
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed pasting on cached node: ${e.message}")
                }
            }
        }

        // Step 3: Locate active target node across windows
        val targetNode = findBestTargetNode()
        if (targetNode != null) {
            try {
                if (performPasteOnNode(targetNode, text)) {
                    Log.d(TAG, "Pasted successfully via discovered targetNode")
                    return PasteResult.PASTED_DIRECTLY
                }
            } finally {
                try {
                    targetNode.recycle()
                } catch (_: Exception) {}
            }
        }

        // Step 4: Shizuku hardware paste fallback
        // If Shizuku is active, trigger system keyevent 279 (KEYCODE_PASTE) which works in 100% of apps
        if (TimeController.isReady()) {
            val shizukuOk = runBlocking {
                TimeController.pasteViaShizuku()
            }
            if (shizukuOk) {
                Log.d(TAG, "Pasted successfully via Shizuku KEYCODE_PASTE")
                return PasteResult.PASTED_DIRECTLY
            }
        }

        // Step 5: Fallback to clipboard if direct paste failed
        return if (clipboardSuccess) PasteResult.COPIED_TO_CLIPBOARD else PasteResult.FAILED
    }

    private fun performPasteOnNode(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            // Request focus if needed
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Method A: ACTION_PASTE (best for WebViews, Chrome, Telegram, Rich Editors)
            if (hasAction(node, AccessibilityNodeInfo.ACTION_PASTE)) {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (ok) return true
            }

            // Method B: ACTION_SET_TEXT with cursor preservation
            val currentText = node.text?.toString() ?: ""
            val selStart = node.textSelectionStart
            val selEnd = node.textSelectionEnd

            val newText: String
            val newCursorPos: Int

            if (selStart in 0..currentText.length && selEnd in 0..currentText.length && selStart != -1) {
                val start = minOf(selStart, selEnd)
                val end = maxOf(selStart, selEnd)
                val sb = StringBuilder(currentText)
                sb.replace(start, end, text)
                newText = sb.toString()
                newCursorPos = start + text.length
            } else {
                newText = if (currentText.isEmpty()) text else "$currentText $text"
                newCursorPos = newText.length
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            val setTextOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setTextOk) {
                try {
                    val selArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                } catch (_: Exception) {}
                return true
            }

            // Method C: Check parent hierarchy for ACTION_PASTE
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (hasAction(parent, AccessibilityNodeInfo.ACTION_PASTE)) {
                    val parentOk = parent.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (parentOk) return true
                }
                parent = parent.parent
                depth++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error performing paste on node: ${e.message}")
        }
        return false
    }

    private fun findBestTargetNode(): AccessibilityNodeInfo? {
        // 1. System-wide focused input
        try {
            findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        } catch (_: Exception) {}

        // 2. Accessibility focus
        try {
            findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { return it }
        } catch (_: Exception) {}

        // 3. Search through interactive windows (prioritizing focused and application windows)
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                val sorted = windowList.sortedByDescending {
                    (if (it.isFocused) 20 else 0) +
                            (if (it.type == AccessibilityWindowInfo.TYPE_APPLICATION) 10 else 0) +
                            it.layer
                }
                for (w in sorted) {
                    val root = w.root ?: continue
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
                    val candidate = searchTreeForInput(root, depth = 0)
                    if (candidate != null) return candidate
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing windows: ${e.message}")
        }

        // 4. Fallback: search rootInActiveWindow
        try {
            rootInActiveWindow?.let { root ->
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
                val candidate = searchTreeForInput(root, depth = 0)
                if (candidate != null) return candidate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing rootInActiveWindow: ${e.message}")
        }

        return null
    }

    private fun searchTreeForInput(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 14) return null

        // If explicitly focused and is an input or editable
        if (node.isFocused && (node.isEditable || hasAction(node, AccessibilityNodeInfo.ACTION_PASTE) || isInputClass(node))) {
            return node
        }

        // Search children first to find the most specific input view
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = searchTreeForInput(child, depth + 1)
            if (res != null) return res
        }

        // If node itself is editable or has paste
        if (node.isEditable || hasAction(node, AccessibilityNodeInfo.ACTION_PASTE) || isInputClass(node)) {
            return node
        }

        return null
    }

    private fun isInputClass(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: return false
        return cls.contains("EditText", ignoreCase = true) ||
                cls.contains("AutoCompleteTextView", ignoreCase = true) ||
                cls.contains("Search", ignoreCase = true) ||
                cls.contains("WebView", ignoreCase = true) ||
                cls.contains("TextInput", ignoreCase = true)
    }

    private fun hasAction(node: AccessibilityNodeInfo, actionId: Int): Boolean {
        return node.actionList.any { it.id == actionId }
    }

    private fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Xeno Text", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "XenoAccessibility"

        @Volatile
        var instance: XenoAccessibilityService? = null
            private set
    }
}
