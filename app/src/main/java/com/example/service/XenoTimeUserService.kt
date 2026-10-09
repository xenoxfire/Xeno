package com.example.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import com.example.IXenoTimeService
import kotlin.system.exitProcess

@Keep
class XenoTimeUserService : IXenoTimeService.Stub {

    constructor() : super()
    constructor(context: Context) : super()

    override fun destroy() {
        Log.i(TAG, "XenoTimeUserService destroy requested")
        exitProcess(0)
    }

    override fun shiftTime(deltaMs: Long): Boolean {
        val target = System.currentTimeMillis() + deltaMs
        return setTime(target)
    }

    override fun setTime(epochMs: Long): Boolean {
        try {
            val ok = SystemClock.setCurrentTimeMillis(epochMs)
            if (ok) return true
        } catch (t: Throwable) {
            Log.w(TAG, "SystemClock.setCurrentTimeMillis failed: ${t.message}")
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("cmd", "alarm", "set-time", epochMs.toString()))
            process.waitFor() == 0
        } catch (t: Throwable) {
            Log.e(TAG, "Fallback cmd alarm set-time failed: ${t.message}")
            false
        }
    }

    override fun setAutoTime(enabled: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "auto_time", enabled.toString()))
            process.waitFor() == 0
        } catch (t: Throwable) {
            Log.e(TAG, "settings put global auto_time failed: ${t.message}")
            false
        }
    }

    companion object {
        private const val TAG = "XenoTimeUserService"
    }
}
