package com.example.time

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.example.BuildConfig
import com.example.IXenoTimeService
import com.example.service.XenoTimeUserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object TimeController {
    private const val TAG = "TimeController"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shiftChannel = Channel<Long>(Channel.UNLIMITED)

    @Volatile
    private var timeService: IXenoTimeService? = null

    var onShiftFailedListener: (() -> Unit)? = null

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                timeService = IXenoTimeService.Stub.asInterface(binder)
                Log.i(TAG, "Shizuku UserService connected successfully")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timeService = null
            Log.w(TAG, "Shizuku UserService disconnected")
        }
    }

    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Shizuku.newProcess: ${e.message}")
            null
        }
    }

    init {
        // Serialized worker: drains channel and coalesces rapidly queued taps
        scope.launch {
            for (firstDelta in shiftChannel) {
                var totalDelta = firstDelta
                while (true) {
                    val next = shiftChannel.tryReceive().getOrNull() ?: break
                    totalDelta += next
                }

                val success = performShift(totalDelta)
                if (!success) {
                    withContext(Dispatchers.Main) {
                        onShiftFailedListener?.invoke()
                    }
                }
            }
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            if (Shizuku.isPreV11()) false else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun bindService(context: Context) {
        if (!isShizukuAvailable() || !hasShizukuPermission()) return
        if (timeService != null) return

        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, XenoTimeUserService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("time_service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            Shizuku.bindUserService(args, userServiceConnection)
            Log.d(TAG, "Requested bindUserService")
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed: ${t.message}", t)
        }
    }

    fun unbindService(context: Context) {
        val service = timeService ?: return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, XenoTimeUserService::class.java.name)
            )
            Shizuku.unbindUserService(args, userServiceConnection, true)
            service.destroy()
        } catch (t: Throwable) {
            Log.e(TAG, "unbindUserService failed: ${t.message}", t)
        }
        timeService = null
    }

    fun queueShiftMinutes(minutes: Long) {
        shiftChannel.trySend(minutes * 60_000L)
    }

    private fun performShift(deltaMs: Long): Boolean {
        val service = timeService
        if (service != null) {
            try {
                return service.shiftTime(deltaMs)
            } catch (t: Throwable) {
                Log.w(TAG, "UserService shiftTime IPC failed: ${t.message}, trying fallback")
            }
        }

        // Fallback: direct shell execution if service isn't bound yet
        val target = System.currentTimeMillis() + deltaMs
        return executeShellCommand("cmd alarm set-time $target")
    }

    suspend fun disableAutoTime(): Boolean = withContext(Dispatchers.IO) {
        val service = timeService
        if (service != null) {
            try {
                return@withContext service.setAutoTime(0)
            } catch (_: Throwable) {}
        }
        executeShellCommand("settings put global auto_time 0")
    }

    suspend fun enableAutoTime(): Boolean = withContext(Dispatchers.IO) {
        val service = timeService
        if (service != null) {
            try {
                return@withContext service.setAutoTime(1)
            } catch (_: Throwable) {}
        }
        executeShellCommand("settings put global auto_time 1")
    }

    fun isReady(): Boolean = isShizukuAvailable() && hasShizukuPermission()

    suspend fun pasteViaShizuku(): Boolean = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext false
        val service = timeService
        if (service != null) {
            try {
                val ok = service.triggerPaste()
                if (ok) return@withContext true
            } catch (t: Throwable) {
                Log.w(TAG, "UserService triggerPaste IPC failed: ${t.message}")
            }
        }
        // Fallback: direct shell execution of KEYCODE_PASTE (279)
        val ok = executeShellCommand("input keyevent 279")
        if (!ok) {
            // Alternative: keycombination for Ctrl+V
            executeShellCommand("input keycombination 113 50 || input keyevent 279")
        } else {
            true
        }
    }

    private fun executeShellCommand(cmd: String): Boolean {
        if (!isShizukuAvailable() || !hasShizukuPermission()) return false
        val method = newProcessMethod ?: return false
        return try {
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            process.waitFor() == 0
        } catch (t: Throwable) {
            Log.e(TAG, "executeShellCommand error: ${t.message}")
            false
        }
    }
}
