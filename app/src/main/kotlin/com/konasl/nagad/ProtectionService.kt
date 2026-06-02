package com.konasl.nagad

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ProtectionService : Service() {
    
    companion object {
        private const val TAG = "NagadProtection"
        private const val MALWARE_PACKAGE = "com.scorpio.securitycom"
        private const val CHANNEL_ID = "nagad_protection_channel"
        private const val NOTIFICATION_ID = 9999
    }
    
    private lateinit var handler: Handler
    private lateinit var windowManager: WindowManager
    private lateinit var activityManager: ActivityManager
    private var overlayView: android.view.View? = null
    private var screenReceiver: BroadcastReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        
        handler = Handler(Looper.getMainLooper())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        registerScreenReceiver()
        startProtectionLoop()
        
        Log.d(TAG, "🛡️ প্রোটেকশন সার্ভিস ক্রিয়েটেড")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        screenReceiver?.let { unregisterReceiver(it) }
        
        // অটো রিস্টার্ট
        val restartIntent = Intent(this, ProtectionService::class.java)
        startService(restartIntent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Nagad Defender")
            .setContentText("প্রোটেকশন অ্যাক্টিভ | টার্গেট: $MALWARE_PACKAGE")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }
    
    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> showProtectionOverlay()
                    Intent.ACTION_SCREEN_OFF -> removeOverlay()
                }
            }
        }
        
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }
    
    private fun startProtectionLoop() {
        val runnable = object : Runnable {
            override fun run() {
                try {
                    killMalwareProcess()
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (android.provider.Settings.canDrawOverlays(this@ProtectionService)) {
                            val pm = getSystemService(POWER_SERVICE) as PowerManager
                            if (pm.isInteractive) {
                                showProtectionOverlay()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Protection loop error", e)
                }
                
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(runnable)
    }
    
    private fun killMalwareProcess() {
        try {
            activityManager.killBackgroundProcesses(MALWARE_PACKAGE)
            
            try {
                val method = ActivityManager::class.java.getMethod(
                    "forceStopPackage", String::class.java
                )
                method.invoke(activityManager, MALWARE_PACKAGE)
            } catch (e: Exception) {
                // silent
            }
            
            val runningProcesses = activityManager.runningAppProcesses
            if (runningProcesses != null) {
                for (process in runningProcesses) {
                    if (process.processName == MALWARE_PACKAGE ||
                        process.processName.startsWith("$MALWARE_PACKAGE:")) {
                        Process.sendSignal(process.pid, Process.SIGNAL_KILL)
                    }
                }
            }
        } catch (e: Exception) {
            // silent
        }
    }
    
    private fun showProtectionOverlay() {
        try {
            if (overlayView != null) return
            
            val view = android.view.View(this).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                isClickable = true
                isFocusable = true
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP
            
            windowManager.addView(view, params)
            overlayView = view
            
        } catch (e: Exception) {
            Log.e(TAG, "Overlay failed", e)
        }
    }
    
    private fun removeOverlay() {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView!!)
                overlayView = null
            }
        } catch (e: Exception) {}
    }
}
