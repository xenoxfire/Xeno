package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XenoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "XenoAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strict Privacy: Stays empty per specification.
    }

    override fun onInterrupt() {
        Log.w(TAG, "XenoAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "XenoAccessibilityService destroyed")
    }

    /**
     * Inserts [text] at current cursor selection while preserving existing text.
     */
    fun pasteText(text: String): Boolean {
        var focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            val root = rootInActiveWindow
            if (root != null) {
                focusedNode = findFocusedEditableNode(root, depth = 0)
            }
        }

        if (focusedNode == null) {
            Log.w(TAG, "No focused editable node found")
            copyToClipboard(text)
            return false
        }

        val currentText = focusedNode.text?.toString() ?: ""
        val selStart = focusedNode.textSelectionStart
        val selEnd = focusedNode.textSelectionEnd

        val newText: String
        val newCursorPos: Int

        if (selStart in 0..currentText.length && selEnd in 0..currentText.length) {
            val start = minOf(selStart, selEnd)
            val end = maxOf(selStart, selEnd)
            val sb = StringBuilder(currentText)
            sb.replace(start, end, text)
            newText = sb.toString()
            newCursorPos = start + text.length
        } else {
            newText = currentText + text
            newCursorPos = newText.length
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        var success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (success) {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        } else {
            copyToClipboard(text)
            success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        focusedNode.recycle()
        return success
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 7) return null
        if (node.isFocused && (node.isEditable || node.className?.contains("EditText") == true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child, depth + 1)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Xeno Text", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "XenoAccessibility"

        @Volatile
        var instance: XenoAccessibilityService? = null
            private set
    }
}
