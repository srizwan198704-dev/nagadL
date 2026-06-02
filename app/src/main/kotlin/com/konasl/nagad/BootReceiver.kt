package com.konasl.nagad

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
        Log.d(TAG, "🔔 বুট রিসিভার: ${intent.action}")
        
        try {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    
                    // ম্যালওয়্যার কিল
                    killMalware(context)
                    
                    // প্রোটেকশন সার্ভিস শুরু
                    val serviceIntent = Intent(context, ProtectionService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    // ১ সেকেন্ড পর মেইন অ্যাপ
                    android.os.Handler(context.mainLooper).postDelayed({
                        val mainIntent = Intent(context, MainActivity::class.java)
                        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(mainIntent)
                    }, 1000)
                    
                    Log.d(TAG, "✅ বুট প্রোটেকশন অ্যাক্টিভেটেড")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "BootReceiver error", e)
        }
    }
    
    private fun killMalware(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(MALWARE_PACKAGE)
            
            // ফোর্স স্টপ
            try {
                val method = ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
                method.invoke(am, MALWARE_PACKAGE)
            } catch (e: Exception) {
                // silent
            }
            
            Log.d(TAG, "☠️ ম্যালওয়্যার কিল করা হয়েছে বুটে")
        } catch (e: Exception) {
            Log.e(TAG, "Boot kill failed", e)
        }
    }
}
