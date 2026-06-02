package com.konasl.nagad

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NagadBoot"
        private const val MALWARE_PACKAGE = "com.scorpio.securitycom"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 বুট রিসিভার ট্রিগার: ${intent.action}")
        
        try {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    
                    Log.d(TAG, "🚀 বুট কমপ্লিট, প্রোটেকশন শুরু...")
                    
                    // ১. প্রথমে ম্যালওয়্যার কিল
                    killMalware(context)
                    
                    // ২. প্রোটেকশন সার্ভিস শুরু
                    startProtectionService(context)
                    
                    // ৩. কিছুক্ষণ পর মেইন অ্যাপ চালু
                    android.os.Handler(context.mainLooper).postDelayed({
                        startMainApp(context)
                    }, 1500)
                    
                    Log.d(TAG, "✅ বুট প্রোটেকশন সম্পন্ন")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "বুট রিসিভার এরর: ${e.message}", e)
        }
    }
    
    private fun killMalware(context: Context) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // মেথড ১: killBackgroundProcesses
            activityManager.killBackgroundProcesses(MALWARE_PACKAGE)
            Log.d(TAG, "☠️ ম্যালওয়্যার কিল (মেথড ১): $MALWARE_PACKAGE")
            
            // মেথড ২: forceStopPackage (রিফ্লেকশন)
            try {
                val method = ActivityManager::class.java.getMethod(
                    "forceStopPackage", 
                    String::class.java
                )
                method.invoke(activityManager, MALWARE_PACKAGE)
                Log.d(TAG, "☠️ ম্যালওয়্যার ফোর্স স্টপ (মেথড ২): $MALWARE_PACKAGE")
            } catch (e: Exception) {
                Log.e(TAG, "ফোর্স স্টপ ব্যর্থ: ${e.message}")
            }
            
            // মেথড ৩: চলমান প্রসেস খুঁজে কিল
            try {
                val runningProcesses = activityManager.runningAppProcesses
                if (runningProcesses != null) {
                    for (process in runningProcesses) {
                        if (process.processName == MALWARE_PACKAGE || 
                            process.processName.startsWith("$MALWARE_PACKAGE:")) {
                            android.os.Process.killProcess(process.pid)
                            Log.d(TAG, "☠️ প্রসেস কিল (মেথড ৩): PID ${process.pid}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "প্রসেস কিল ব্যর্থ: ${e.message}")
            }
            
            // মেথড ৪: প্যাকেজ ডিজেবল করার চেষ্টা
            try {
                val pm = context.packageManager
                pm.setApplicationEnabledSetting(
                    MALWARE_PACKAGE,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
                )
                Log.d(TAG, "🚫 ম্যালওয়্যার ডিজেবল (মেথড ৪)")
            } catch (e: Exception) {
                Log.e(TAG, "ডিজেবল ব্যর্থ: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "ম্যালওয়্যার কিল করতে ব্যর্থ: ${e.message}", e)
        }
    }
    
    private fun startProtectionService(context: Context) {
        try {
            val serviceIntent = Intent(context, ProtectionService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "🛡️ প্রোটেকশন সার্ভিস শুরু")
        } catch (e: Exception) {
            Log.e(TAG, "সার্ভিস শুরু করতে ব্যর্থ: ${e.message}", e)
        }
    }
    
    private fun startMainApp(context: Context) {
        try {
            val mainIntent = Intent(context, MainActivity::class.java)
            mainIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(mainIntent)
            Log.d(TAG, "📱 মেইন অ্যাপ চালু")
        } catch (e: Exception) {
            Log.e(TAG, "মেইন অ্যাপ চালু করতে ব্যর্থ: ${e.message}", e)
        }
    }
}
