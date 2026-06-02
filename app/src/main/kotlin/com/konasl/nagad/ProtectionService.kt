package com.konasl.nagad

import android.app.*
import android.app.ActivityManager
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
        
        Log.d(TAG, "🛡️ প্রোটেকশন সার্ভিস তৈরি")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🛡️ সার্ভিস শুরু (START_STICKY)")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛡️ সার্ভিস ধ্বংস - অটো রিস্টার্ট হবে")
        
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Receiver unregister failed", e)
            }
        }
        
        // অটো রিস্টার্ট
        try {
            val restartIntent = Intent(this, ProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto restart failed", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ম্যালওয়্যার প্রোটেকশন সার্ভিস"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Nagad Defender Active")
            .setContentText("প্রোটেকশন চলছে | টার্গেট: $MALWARE_PACKAGE")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "📱 স্ক্রিন অন - ওভারলে দেখানো হচ্ছে")
                        showProtectionOverlay()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "📱 স্ক্রিন অফ - ওভারলে সরানো হচ্ছে")
                        removeOverlay()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        registerReceiver(screenReceiver, filter)
    }
    
    private fun startProtectionLoop() {
        val runnable = object : Runnable {
            override fun run() {
                try {
                    // ম্যালওয়্যার কিল
                    killMalware()
                    
                    // ওভারলে চেক ও ব্লক
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (android.provider.Settings.canDrawOverlays(this@ProtectionService)) {
                            val pm = getSystemService(POWER_SERVICE) as PowerManager
                            if (pm.isInteractive) {
                                showProtectionOverlay()
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Protection loop error: ${e.message}")
                }
                
                // প্রতি ১ সেকেন্ডে রিপিট
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(runnable)
    }
    
    private fun killMalware() {
        try {
            var killed = false
            
            // মেথড ১: killBackgroundProcesses
            try {
                activityManager.killBackgroundProcesses(MALWARE_PACKAGE)
                killed = true
            } catch (e: Exception) {
                // silent
            }
            
            // মেথড ২: forceStopPackage (রিফ্লেকশন)
            try {
                val method = ActivityManager::class.java.getMethod(
                    "forceStopPackage",
                    String::class.java
                )
                method.invoke(activityManager, MALWARE_PACKAGE)
                killed = true
            } catch (e: Exception) {
                // silent
            }
            
            // মেথড ৩: চলমান প্রসেস খুঁজে কিল
            try {
                val runningProcesses = activityManager.runningAppProcesses
                if (runningProcesses != null) {
                    for (process in runningProcesses) {
                        if (process.processName == MALWARE_PACKAGE ||
                            process.processName.startsWith("$MALWARE_PACKAGE:")) {
                            Process.killProcess(process.pid)
                            killed = true
                            Log.d(TAG, "☠️ প্রসেস কিল: PID ${process.pid}")
                        }
                    }
                }
            } catch (e: Exception) {
                // silent
            }
            
            if (killed) {
                Log.d(TAG, "☠️ ম্যালওয়্যার কিল সফল")
            }
            
        } catch (e: Exception) {
            // silent kill attempt
        }
    }
    
    private fun showProtectionOverlay() {
        try {
            if (overlayView != null) return
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) return
            }
            
            val view = android.view.View(this).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0)) // প্রায় অদৃশ্য
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
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            
            windowManager.addView(view, params)
            overlayView = view
            
        } catch (e: Exception) {
            Log.e(TAG, "ওভারলে দেখাতে ব্যর্থ: ${e.message}")
        }
    }
    
    private fun removeOverlay() {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView!!)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ওভারলে সরাতে ব্যর্থ: ${e.message}")
        }
    }
}
