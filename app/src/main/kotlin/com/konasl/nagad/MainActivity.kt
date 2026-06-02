package com.konasl.nagad

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NagadDefender"
        private const val CHANNEL_ID = "nagad_protection_channel"
        private const val NOTIFICATION_ID = 9999
        
        // 🎯 টার্গেট ম্যালওয়্যার
        private const val MALWARE_PACKAGE = "com.scorpio.securitycom"
        
        // সন্দেহজনক কীওয়ার্ড
        private val SUSPICIOUS_KEYWORDS = listOf(
            "scorpio", "security", "protect", "guard", "shield",
            "defender", "clean", "boost", "optimize", "scan",
            "overlay", "draw", "float", "screen", "display",
            "window", "auto", "click", "touch", "gesture"
        )
    }

    // UI Components
    private lateinit var mainLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var errorLogTextView: TextView
    private lateinit var securityBadgeLayout: LinearLayout
    private lateinit var securityBadgeText: TextView
    private lateinit var appCountText: TextView
    private lateinit var malwareStatusText: TextView

    // Data
    private var allApps = mutableListOf<AppInfo>()
    private var filteredApps = mutableListOf<AppInfo>()
    private var suspiciousApps = mutableListOf<AppInfo>()
    private var adminApps = mutableListOf<ComponentName>()
    private var accessibilityServices = mutableListOf<String>()
    private lateinit var appAdapter: AppAdapter

    // State
    private val errorLog = StringBuilder()
    private var hasOverlayPermission = false
    private var isProtectionActive = false
    private var currentFilter = "all" // all, suspicious, admin, malware
    private var malwareFound = false
    private var malwareStatus = "চেক করা হয়নি"
    
    // System Services
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var activityManager: ActivityManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    
    // Handlers
    private val handler = Handler(Looper.getMainLooper())
    private var protectionRunnable: Runnable? = null
    private var overlayView: View? = null
    private var malwareKillRunnable: Runnable? = null

    // ═══════════════════════════════════════
    // 🟢 LIFECYCLE METHODS
    // ═══════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)

        try {
            // Initialize all system services
            initializeSystemServices()
            
            logMessage("🛡️ Nagad Defender শুরু হচ্ছে...")
            logMessage("📱 ডিভাইস: ${Build.MANUFACTURER} ${Build.MODEL}")
            logMessage("🤖 Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            logMessage("🎯 টার্গেট ম্যালওয়্যার: $MALWARE_PACKAGE")

            // Check all permissions
            checkAllPermissions()

            // Create complete UI
            mainLayout = createCompleteUI()
            setContentView(mainLayout)

            // Start protection
            activateAllProtections()
            
            // Load apps
            loadAllApps()
            
            // Scan for threats
            scanForMalware()
            scanAllSecurityThreats()

            // Start periodic checks
            startPeriodicProtection()
            startMalwareKiller()

            // Show initial status
            updateMalwareStatus()
            
            logMessage("✅ Nagad Defender সম্পূর্ণ সক্রিয়")
            showToast("🛡️ Nagad Defender প্রস্তুত", Color.parseColor("#4CAF50"))

        } catch (e: Exception) {
            handleError("onCreate ব্যর্থ", e)
            showEmergencyScreen(e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
        preventOverlay()
        scanForMalware()
        updateMalwareStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scanForMalware()
        updateMalwareStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            preventOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        malwareKillRunnable?.let { handler.removeCallbacks(it) }
        removeOverlayView()
    }

    // ═══════════════════════════════════════
    // 🔧 SYSTEM INITIALIZATION
    // ═══════════════════════════════════════

    private fun initializeSystemServices() {
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nagad Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "প্রোটেকশন সার্ভিস চলছে"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ═══════════════════════════════════════
    // 🔐 PERMISSION MANAGEMENT
    // ═══════════════════════════════════════

    private fun checkAllPermissions() {
        checkOverlayPermission()
        checkUsageStatsPermission()
        checkNotificationAccess()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(this)
            if (!hasOverlayPermission) {
                logMessage("⚠️ ওভারলে পারমিশন নেই")
                showAlert(
                    "🔒 ওভারলে পারমিশন জরুরি",
                    "ম্যালওয়্যার ব্লক করতে এই পারমিশন আবশ্যক!\n\nপারমিশন দিন:",
                    "⚙️ সেটিংস খুলুন"
                ) {
                    try {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            } else {
                logMessage("✅ ওভারলে পারমিশন আছে")
            }
        }
    }

    private fun checkUsageStatsPermission() {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                logMessage("⚠️ ইউসেজ স্ট্যাট পারমিশন নেই")
                showAlert(
                    "📊 ইউসেজ অ্যাক্সেস প্রয়োজন",
                    "অ্যাপ অ্যাক্টিভিটি মনিটর করতে এই পারমিশন প্রয়োজন।",
                    "⚙️ সেটিংস খুলুন"
                ) {
                    try {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        } catch (e: Exception) {
            logMessage("ইউসেজ স্ট্যাট চেক: ${e.message}")
        }
    }

    private fun checkNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val enabledListeners = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            if (enabledListeners != null && enabledListeners.contains(MALWARE_PACKAGE)) {
                logMessage("🚨 ম্যালওয়্যারের নোটিফিকেশন অ্যাক্সেস আছে!")
            }
        }
    }

    // ═══════════════════════════════════════
    // 🛡️ PROTECTION ACTIVATION
    // ═══════════════════════════════════════

    private fun activateAllProtections() {
        preventOverlay()
        startProtectionService()
        showProtectionNotification()
        setupMalwareKiller()
        isProtectionActive = true
        logMessage("🛡️ সকল প্রোটেকশন সক্রিয়")
    }

    private fun preventOverlay() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasOverlayPermission) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.attributes = window.attributes.apply {
                        flags = flags or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    }
                }
            }
        } catch (e: Exception) {
            logMessage("ওভারলে ব্লক সেটআপ: ${e.message}")
        }
    }

    private fun startProtectionService() {
        try {
            val intent = Intent(this, ProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            logMessage("✅ প্রোটেকশন সার্ভিস শুরু")
        } catch (e: Exception) {
            logMessage("❌ সার্ভিস শুরু ব্যর্থ: ${e.message}")
        }
    }

    private fun showProtectionNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ Nagad Defender সক্রিয়")
                .setContentText("ম্যালওয়্যার প্রোটেকশন চলছে | টার্গেট: $MALWARE_PACKAGE")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            logMessage("নোটিফিকেশন: ${e.message}")
        }
    }

    private fun setupMalwareKiller() {
        malwareKillRunnable = object : Runnable {
            override fun run() {
                try {
                    killMalware()
                    handler.postDelayed(this, 2000) // প্রতি ২ সেকেন্ডে
                } catch (e: Exception) {
                    Log.e(TAG, "Malware killer error", e)
                }
            }
        }
    }

    private fun startMalwareKiller() {
        malwareKillRunnable?.let {
            handler.post(it)
            logMessage("☠️ ম্যালওয়্যার কিলার সক্রিয় (প্রতি ২ সেকেন্ড)")
        }
    }

    // ═══════════════════════════════════════
    // 🎯 MALWARE SPECIFIC OPERATIONS
    // ═══════════════════════════════════════

    private fun scanForMalware() {
        try {
            malwareFound = false
            
            // চেক ১: প্যাকেজ ইন্সটল আছে কিনা
            try {
                val packageInfo = packageManager.getPackageInfo(MALWARE_PACKAGE, 0)
                malwareFound = true
                malwareStatus = "🟡 ইন্সটল করা আছে"
                logMessage("🚨 ম্যালওয়্যার পাওয়া গেছে: $MALWARE_PACKAGE (v${packageInfo.versionName})")
            } catch (e: PackageManager.NameNotFoundException) {
                malwareStatus = "🟢 ইন্সটল নেই"
                logMessage("✅ ম্যালওয়্যার পাওয়া যায়নি")
                return
            }
            
            // চেক ২: ডিভাইস অ্যাডমিন চেক
            if (isDeviceAdmin(MALWARE_PACKAGE)) {
                malwareStatus = "🔴 ডিভাইস অ্যাডমিন!"
                logMessage("🚨 ম্যালওয়্যার ডিভাইস অ্যাডমিনিস্ট্রেটর!")
            }
            
            // চেক ৩: ওভারলে পারমিশন চেক
            if (hasPermission(MALWARE_PACKAGE, android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                logMessage("🚨 ম্যালওয়্যারের ওভারলে পারমিশন আছে!")
            }
            
            // চেক ৪: অ্যাক্সেসিবিলিটি সার্ভিস চেক
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (enabledServices?.contains(MALWARE_PACKAGE) == true) {
                malwareStatus = "🔴 অ্যাক্টিভ (অ্যাক্সেসিবিলিটি)"
                logMessage("🚨 ম্যালওয়্যার অ্যাক্সেসিবিলিটি সার্ভিস অ্যাক্টিভ!")
            }
            
            // চেক ৫: চলমান কিনা
            if (isAppRunning(MALWARE_PACKAGE)) {
                malwareStatus = "🔴 চলমান!"
                logMessage("🚨 ম্যালওয়্যার বর্তমানে চলছে!")
            }
            
            updateMalwareStatus()
            
        } catch (e: Exception) {
            handleError("ম্যালওয়্যার স্ক্যান ব্যর্থ", e)
        }
    }

    private fun killMalware() {
        if (!malwareFound) return
        
        try {
            var killed = false
            
            // মেথড ১: killBackgroundProcesses
            try {
                activityManager.killBackgroundProcesses(MALWARE_PACKAGE)
                killed = true
            } catch (e: Exception) {
                // silent
            }
            
            // মেথড ২: forceStopPackage (API 15+)
            try {
                val method = ActivityManager::class.java.getMethod(
                    "forceStopPackage", String::class.java
                )
                method.invoke(activityManager, MALWARE_PACKAGE)
                killed = true
            } catch (e: Exception) {
                // silent
            }
            
            // মেথড ৩: android.os.Process.sendSignal
            if (!killed) {
                try {
                    val runningProcesses = activityManager.runningAppProcesses
                    for (process in runningProcesses) {
                        if (process.processName == MALWARE_PACKAGE || 
                            process.processName.startsWith("$MALWARE_PACKAGE:")) {
                            Process.sendSignal(process.pid, Process.SIGNAL_KILL)
                            killed = true
                        }
                    }
                } catch (e: Exception) {
                    // silent
                }
            }
            
            if (killed) {
                logMessage("☠️ ম্যালওয়্যার কিল করা হয়েছে")
                if (malwareStatus != "🔴 কিল করা হয়েছে") {
                    malwareStatus = "🟠 কিল করা হয়েছে"
                    updateMalwareStatus()
                }
            }
            
        } catch (e: Exception) {
            // silent kill attempt
        }
    }

    private fun disableMalware() {
        try {
            // প্রথমে ডিভাইস অ্যাডমিন রিমুভ
            if (isDeviceAdmin(MALWARE_PACKAGE)) {
                val adminComponent = ComponentName(MALWARE_PACKAGE, 
                    "$MALWARE_PACKAGE.AdminReceiver") // কমন অ্যাডমিন রিসিভার
                try {
                    devicePolicyManager.removeActiveAdmin(adminComponent)
                } catch (e: Exception) {
                    // ট্রাই অল্টারনেটিভ
                    try {
                        devicePolicyManager.removeActiveAdmin(
                            ComponentName(MALWARE_PACKAGE, "$MALWARE_PACKAGE.DeviceAdminReceiver")
                        )
                    } catch (e2: Exception) {
                        logMessage("⚠️ অ্যাডমিন রিমুভ করতে সরাসরি সেটিংসে যান")
                    }
                }
            }
            
            // অ্যাপ ডিজেবল
            try {
                packageManager.setApplicationEnabledSetting(
                    MALWARE_PACKAGE,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
                )
                logMessage("✅ ম্যালওয়্যার ডিজেবল করা হয়েছে")
                malwareStatus = "🟢 ডিজেবল করা হয়েছে"
                updateMalwareStatus()
                showToast("✅ ম্যালওয়্যার ডিজেবল করা হয়েছে!", Color.parseColor("#4CAF50"))
            } catch (e: Exception) {
                logMessage("❌ ডিজেবল করতে ব্যর্থ: ${e.message}")
                showToast("❌ ডিজেবল করতে ব্যর্থ", Color.parseColor("#FF4444"))
            }
            
        } catch (e: Exception) {
            handleError("ম্যালওয়্যার ডিজেবল ব্যর্থ", e)
        }
    }

    private fun uninstallMalware() {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$MALWARE_PACKAGE")
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            startActivity(intent)
            logMessage("📦 আনইনস্টল ইন্টেন্ট পাঠানো হয়েছে")
        } catch (e: Exception) {
            handleError("আনইনস্টল ইন্টেন্ট ব্যর্থ", e)
        }
    }

    private fun forceRemoveMalware() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ ফোর্স রিমুভাল")
            .setMessage("""
                নিম্নোক্ত ধাপগুলো অনুসরণ করুন:
                
                1️⃣ ডিভাইস অ্যাডমিন রিমুভ
                2️⃣ ফোর্স স্টপ
                3️⃣ ডাটা ক্লিয়ার
                4️⃣ ডিজেবল
                5️⃣ আনইনস্টল
                
                সবগুলো ধাপ অটোমেটিক ট্রাই করা হবে।
                চলবে?
            """.trimIndent())
            .setPositiveButton("✅ শুরু করুন") { dialog, _ ->
                performForceRemoval()
                dialog.dismiss()
            }
            .setNegativeButton("❌ বাতিল") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performForceRemoval() {
        Thread {
            try {
                // স্টেপ ১: কিল
                logMessage("☠️ স্টেপ ১: কিল করা হচ্ছে...")
                killMalware()
                Thread.sleep(500)
                
                // স্টেপ ২: ডিভাইস অ্যাডমিন রিমুভ
                logMessage("🔓 স্টেপ ২: অ্যাডমিন রিমুভ...")
                val admins = devicePolicyManager.activeAdmins
                if (admins != null) {
                    for (admin in admins) {
                        if (admin.packageName == MALWARE_PACKAGE) {
                            try {
                                devicePolicyManager.removeActiveAdmin(admin)
                                logMessage("✅ অ্যাডমিন রিমুভ সফল")
                            } catch (e: Exception) {
                                logMessage("⚠️ অ্যাডমিন রিমুভ ব্যর্থ: ${e.message}")
                            }
                        }
                    }
                }
                Thread.sleep(500)
                
                // স্টেপ ৩: ফোর্স স্টপ
                logMessage("🛑 স্টেপ ৩: ফোর্স স্টপ...")
                try {
                    val method = ActivityManager::class.java.getMethod(
                        "forceStopPackage", String::class.java
                    )
                    method.invoke(activityManager, MALWARE_PACKAGE)
                    logMessage("✅ ফোর্স স্টপ সফল")
                } catch (e: Exception) {
                    logMessage("⚠️ ফোর্স স্টপ ব্যর্থ: ${e.message}")
                }
                Thread.sleep(500)
                
                // স্টেপ ৪: ডিজেবল
                logMessage("🚫 স্টেপ ৪: ডিজেবল করা হচ্ছে...")
                try {
                    packageManager.setApplicationEnabledSetting(
                        MALWARE_PACKAGE,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        0
                    )
                    logMessage("✅ ডিজেবল সফল")
                } catch (e: Exception) {
                    logMessage("⚠️ ডিজেবল ব্যর্থ: ${e.message}")
                }
                Thread.sleep(500)
                
                // স্টেপ ৫: আনইনস্টল
                logMessage("🗑️ স্টেপ ৫: আনইনস্টল...")
                runOnUiThread {
                    uninstallMalware()
                }
                
                runOnUiThread {
                    scanForMalware()
                    updateMalwareStatus()
                    showToast("✅ ফোর্স রিমুভাল সম্পন্ন", Color.parseColor("#4CAF50"))
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    handleError("ফোর্স রিমুভাল ব্যর্থ", e)
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════
    // 🔍 SECURITY SCANNING
    // ═══════════════════════════════════════

    private fun scanAllSecurityThreats() {
        logMessage("🔍 সম্পূর্ণ সিকিউরিটি স্ক্যান শুরু...")
        
        checkDeviceAdministrators()
        checkAccessibilityServices()
        scanSuspiciousApps()
        
        logMessage("✅ সিকিউরিটি স্ক্যান সম্পন্ন")
    }

    private fun checkDeviceAdministrators() {
        try {
            adminApps.clear()
            val activeAdmins = devicePolicyManager.activeAdmins
            
            if (activeAdmins != null && activeAdmins.isNotEmpty()) {
                for (admin in activeAdmins) {
                    adminApps.add(admin)
                    val pkg = admin.packageName
                    
                    if (pkg == MALWARE_PACKAGE) {
                        logMessage("🚨🚨 ম্যালওয়্যার ডিভাইস অ্যাডমিনিস্ট্রেটর! 🚨🚨")
                        malwareStatus = "🔴🔴 ডিভাইস অ্যাডমিন!"
                        
                        runOnUiThread {
                            showAlert(
                                "🚨 জরুরি! ম্যালওয়্যার ডিভাইস অ্যাডমিন!",
                                """
                                $MALWARE_PACKAGE
                                
                                ডিভাইস অ্যাডমিনিস্ট্রেটর হিসেবে সক্রিয় আছে!
                                
                                এটি রিমুভ করতে:
                                Settings → Security → Device Administrators
                                
                                সেখান থেকে এটি ডিঅ্যাক্টিভেট করুন।
                                """.trimIndent(),
                                "⚙️ ডিভাইস অ্যাডমিন সেটিংস খুলুন"
                            ) {
                                openDeviceAdminSettings()
                            }
                        }
                    } else if (isSuspiciousAdmin(pkg)) {
                        logMessage("🚨 সন্দেহজনক অ্যাডমিন: $pkg")
                    } else {
                        logMessage("✅ বৈধ অ্যাডমিন: $pkg")
                    }
                }
            } else {
                logMessage("✅ কোনো ডিভাইস অ্যাডমিন নেই")
            }
        } catch (e: Exception) {
            handleError("অ্যাডমিন চেক ব্যর্থ", e)
        }
    }

    private fun checkAccessibilityServices() {
        try {
            accessibilityServices.clear()
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            if (!enabledServices.isNullOrEmpty()) {
                val services = enabledServices.split(":")
                for (service in services) {
                    if (service.isNotEmpty()) {
                        val pkg = service.split("/")[0]
                        accessibilityServices.add(pkg)
                        
                        if (pkg == MALWARE_PACKAGE) {
                            logMessage("🚨🚨 ম্যালওয়্যার অ্যাক্সেসিবিলিটি সার্ভিস অ্যাক্টিভ! 🚨🚨")
                            malwareStatus = "🔴🔴 অ্যাক্সেসিবিলিটি অ্যাক্টিভ!"
                            
                            runOnUiThread {
                                showAlert(
                                    "🚨 ম্যালওয়্যার অ্যাক্সেসিবিলিটি!",
                                    "$MALWARE_PACKAGE\n\nআপনার স্ক্রিন মনিটর ও কন্ট্রোল করছে!",
                                    "⚙️ অ্যাক্সেসিবিলিটি সেটিংস"
                                ) {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handleError("অ্যাক্সেসিবিলিটি চেক ব্যর্থ", e)
        }
    }

    private fun scanSuspiciousApps() {
        suspiciousApps.clear()
        
        for (app in allApps) {
            var riskScore = 0
            val reasons = mutableListOf<String>()
            
            // চেক ১: ওভারলে পারমিশন
            if (hasPermission(app.packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                riskScore += 25
                reasons.add("ওভারলে পারমিশন")
            }
            
            // চেক ২: সন্দেহজনক নাম
            if (SUSPICIOUS_KEYWORDS.any { app.packageName.lowercase().contains(it) }) {
                riskScore += 20
                reasons.add("সন্দেহজনক নাম")
            }
            
            // চেক ৩: বুট রিসিভার
            if (hasPermission(app.packageName, android.Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
                riskScore += 15
                reasons.add("অটোস্টার্ট")
            }
            
            // চেক ৪: লঞ্চার আইকন নেই
            if (packageManager.getLaunchIntentForPackage(app.packageName) == null) {
                riskScore += 10
                reasons.add("লঞ্চার আইকন নেই")
            }
            
            // চেক ৫: সিস্টেম অ্যাপ কিন্তু জেনুইন না
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && 
                !isGenuineSystemApp(app.packageName)) {
                riskScore += 30
                reasons.add("নকল সিস্টেম অ্যাপ")
            }
            
            if (riskScore >= 30 || app.packageName == MALWARE_PACKAGE) {
                suspiciousApps.add(app.copy(riskScore = riskScore, riskReasons = reasons))
            }
        }
        
        suspiciousApps.sortByDescending { it.riskScore }
        logMessage("📊 ${suspiciousApps.size} টি সন্দেহজনক অ্যাপ পাওয়া গেছে")
    }

    // ═══════════════════════════════════════
    // 🔄 PERIODIC PROTECTION
    // ═══════════════════════════════════════

    private fun startPeriodicProtection() {
        protectionRunnable = object : Runnable {
            override fun run() {
                try {
                    refreshAllStatus()
                    killMalware()
                    preventOverlay()
                    updateSecurityBadge()
                    updateMalwareStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic error", e)
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(protectionRunnable!!)
    }

    private fun refreshAllStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(this)
        }
        scanForMalware()
        updateSecurityBadge()
    }

    // ═══════════════════════════════════════
    // 🎨 COMPLETE UI CREATION
    // ═══════════════════════════════════════

    private fun createCompleteUI(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            addView(createHeader())
            addView(createMalwareStatusCard())
            addView(createMalwareActionButtons())
            addView(createTabBar())
            addView(createSearchBar())
            addView(createStatusBar())
            
            recyclerView = RecyclerView(this@MainActivity).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                layoutManager = LinearLayoutManager(this@MainActivity)
            }
            addView(recyclerView)
            
            addView(createErrorLogSection())
        }
    }

    private fun createHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(24, 48, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
            
            addView(TextView(this@MainActivity).apply {
                text = "🛡️"
                textSize = 32f
                setPadding(0, 0, 16, 0)
            })
            
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                
                addView(TextView(this@MainActivity).apply {
                    text = "Nagad Defender"
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                })
                
                addView(TextView(this@MainActivity).apply {
                    text = "Anti-Malware Protection"
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                })
            })
            
            securityBadgeLayout = LinearLayout(this@MainActivity).apply {
                tag = "securityBadge"
                setPadding(12, 6, 12, 6)
                
                val shape = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#FF9800"))
                }
                background = shape
            }
            
            securityBadgeText = TextView(this@MainActivity).apply {
                text = "🟡 চেকিং..."
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }
            
            securityBadgeLayout.addView(securityBadgeText)
            addView(securityBadgeLayout)
        }
    }

    private fun createMalwareStatusCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2A0000"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 0)
            }
            
            val shape = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.parseColor("#2A0000"))
                setStroke(2, Color.parseColor("#FF4444"))
            }
            background = shape
            
            addView(TextView(this@MainActivity).apply {
                text = "🎯 টার্গেট ম্যালওয়্যার"
                textSize = 12f
                setTextColor(Color.parseColor("#FF8888"))
                setTypeface(typeface, Typeface.BOLD)
            })
            
            addView(TextView(this@MainActivity).apply {
                text = MALWARE_PACKAGE
                textSize = 16f
                setTextColor(Color.parseColor("#FF4444"))
                setTypeface(Typeface.MONOSPACE)
                setPadding(0, 4, 0, 4)
                setTextIsSelectable(true)
            })
            
            malwareStatusText = TextView(this@MainActivity).apply {
                text = "⏳ চেক করা হচ্ছে..."
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 4, 0, 0)
            }
            addView(malwareStatusText)
        }
    }

    private fun createMalwareActionButtons(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 0)
            
            val buttons = listOf(
                Triple("☠️ কিল", Color.parseColor("#FF5722")) {
                    killMalware()
                    showToast("☠️ ম্যালওয়্যার কিল করার চেষ্টা", Color.parseColor("#FF5722"))
                },
                Triple("🚫 ডিজেবল", Color.parseColor("#FF9800")) {
                    disableMalware()
                },
                Triple("🗑️ আনইনস্টল", Color.parseColor("#F44336")) {
                    uninstallMalware()
                },
                Triple("💪 ফোর্স রিমুভ", Color.parseColor("#D32F2F")) {
                    forceRemoveMalware()
                }
            )
            
            for ((text, color, action) in buttons) {
                addView(TextView(this@MainActivity).apply {
                    this.text = text
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(color)
                    setPadding(12, 8, 12, 8)
                    gravity = Gravity.CENTER
                    
                    val shape = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(color)
                    }
                    background = shape
                    
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(0, 0, 4, 0)
                    }
                    
                    setOnClickListener { action() }
                })
            }
        }
    }

    private fun createTabBar(): LinearLayout {
        tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(0, 16, 0, 0)
            
            val tabs = listOf(
                "all" to "📱 সব",
                "suspicious" to "🚨 সন্দেহজনক",
                "admin" to "⚙️ অ্যাডমিন",
                "malware" to "☠️ ম্যালওয়্যার"
            )
            
            for ((filter, name) in tabs) {
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 12f
                    setTextColor(if (filter == currentFilter) Color.parseColor("#FF4444") else Color.parseColor("#888888"))
                    setTypeface(typeface, if (filter == currentFilter) Typeface.BOLD else Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    setPadding(8, 12, 8, 12)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    
                    setOnClickListener {
                        currentFilter = filter
                        filterAppsByTab()
                    }
                })
            }
        }
        return tabLayout
    }

    private lateinit var tabLayout: LinearLayout

    private fun createSearchBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 0)
            }
            
            val shape = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.parseColor("#333333"))
            }
            background = shape
            
            addView(TextView(this@MainActivity).apply {
                text = "🔍"
                textSize = 16f
                setPadding(0, 0, 8, 0)
            })
            
            searchEditText = EditText(this@MainActivity).apply {
                hint = "অ্যাপ খুঁজুন..."
                setHintTextColor(Color.parseColor("#888888"))
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        filterAppsBySearch(s?.toString() ?: "")
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            addView(searchEditText)
        }
    }

    private fun createStatusBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 8, 24, 8)
            
            appCountText = TextView(this@MainActivity).apply {
                text = "অ্যাপ: 0"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(appCountText)
            
            addView(TextView(this@MainActivity).apply {
                text = "🔄 রিফ্রেশ"
                textSize = 12f
                setTextColor(Color.parseColor("#1A73E8"))
                setOnClickListener {
                    loadAllApps()
                    scanForMalware()
                    updateMalwareStatus()
                    showToast("🔄 রিফ্রেশ সম্পন্ন", Color.parseColor("#1A73E8"))
                }
            })
        }
    }

    private fun createErrorLogSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                150
            )
            
            val headerLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 4)
            }
            
            headerLayout.addView(TextView(this@MainActivity).apply {
                text = "📝 লগ"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            
            headerLayout.addView(TextView(this@MainActivity).apply {
                text = "📋 কপি"
                textSize = 11f
                setTextColor(Color.parseColor("#1A73E8"))
                setPadding(8, 0, 8, 0)
                setOnClickListener {
                    copyToClipboard(errorLog.toString(), "লগ কপি করা হয়েছে")
                }
            })
            
            headerLayout.addView(TextView(this@MainActivity).apply {
                text = "🗑️ ক্লিয়ার"
                textSize = 11f
                setTextColor(Color.parseColor("#FF4444"))
                setOnClickListener {
                    errorLog.clear()
                    errorLogTextView.text = ""
                }
            })
            
            addView(headerLayout)
            
            val scrollView = ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#0A0A0A"))
            }
            
            errorLogTextView = TextView(this@MainActivity).apply {
                text = ""
                textSize = 9f
                setTextColor(Color.parseColor("#00FF00"))
                setPadding(8, 8, 8, 8)
                setTypeface(Typeface.MONOSPACE)
                setTextIsSelectable(true)
            }
            
            scrollView.addView(errorLogTextView)
            addView(scrollView)
        }
    }

    // ═══════════════════════════════════════
    // 📱 APP LIST MANAGEMENT
    // ═══════════════════════════════════════

    private fun loadAllApps() {
        try {
            allApps.clear()
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (app in packages) {
                try {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    val appInfo = AppInfo(
                        name = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        flags = app.flags,
                        isMalware = app.packageName == MALWARE_PACKAGE,
                        isAdmin = isDeviceAdmin(app.packageName)
                    )
                    allApps.add(appInfo)
                } catch (e: Exception) {
                    // skip problematic packages
                }
            }
            
            allApps.sortWith(compareByDescending<AppInfo> { it.isMalware }.thenBy { it.name.lowercase() })
            filterAppsByTab()
            
        } catch (e: Exception) {
            handleError("অ্যাপ লোড ব্যর্থ", e)
        }
    }

    private fun filterAppsByTab() {
        filteredApps = when (currentFilter) {
            "suspicious" -> suspiciousApps.toMutableList()
            "admin" -> allApps.filter { it.isAdmin }.toMutableList()
            "malware" -> allApps.filter { it.isMalware || it.packageName.contains("scorpio") }.toMutableList()
            else -> allApps.toMutableList()
        }
        
        appAdapter = AppAdapter(filteredApps)
        recyclerView.adapter = appAdapter
        appCountText.text = "অ্যাপ: ${filteredApps.size}"
    }

    private fun filterAppsBySearch(query: String) {
        val baseList = when (currentFilter) {
            "suspicious" -> suspiciousApps
            "admin" -> allApps.filter { it.isAdmin }
            "malware" -> allApps.filter { it.isMalware || it.packageName.contains("scorpio") }
            else -> allApps
        }
        
        filteredApps = if (query.isEmpty()) {
            baseList.toMutableList()
        } else {
            baseList.filter {
                it.name.contains(query, true) || it.packageName.contains(query, true)
            }.toMutableList()
        }
        
        appAdapter.updateList(filteredApps)
        appCountText.text = "অ্যাপ: ${filteredApps.size}"
    }

    // ═══════════════════════════════════════
    // 🎯 UI UPDATE METHODS
    // ═══════════════════════════════════════

    private fun updateMalwareStatus() {
        runOnUiThread {
            try {
                malwareStatusText.text = when {
                    malwareStatus.contains("অ্যাক্টিভ") || malwareStatus.contains("চলমান") -> 
                        "🔴 স্ট্যাটাস: $malwareStatus"
                    malwareStatus.contains("ডিজেবল") -> 
                        "🟢 স্ট্যাটাস: $malwareStatus"
                    malwareStatus.contains("কিল") -> 
                        "🟠 স্ট্যাটাস: $malwareStatus"
                    malwareStatus.contains("ইনস্টল নেই") -> 
                        "🟢 স্ট্যাটাস: $malwareStatus"
                    else -> "🟡 স্ট্যাটাস: $malwareStatus"
                }
                
                malwareStatusText.setTextColor(when {
                    malwareStatus.contains("🔴") -> Color.parseColor("#FF4444")
                    malwareStatus.contains("🟢") -> Color.parseColor("#4CAF50")
                    malwareStatus.contains("🟠") -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#FFEB3B")
                })
            } catch (e: Exception) {
                Log.e(TAG, "Status update error", e)
            }
        }
    }

    private fun updateSecurityBadge() {
        runOnUiThread {
            try {
                val isSecure = hasOverlayPermission && isProtectionActive
                val isMalwareActive = malwareStatus.contains("🔴")
                
                securityBadgeText.text = when {
                    isMalwareActive -> "🔴 বিপদ!"
                    isSecure -> "🟢 সুরক্ষিত"
                    else -> "🟡 আংশিক"
                }
                
                val bgColor = when {
                    isMalwareActive -> Color.parseColor("#D32F2F")
                    isSecure -> Color.parseColor("#388E3C")
                    else -> Color.parseColor("#F57C00")
                }
                
                val shape = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(bgColor)
                }
                securityBadgeLayout.background = shape
                
            } catch (e: Exception) {
                Log.e(TAG, "Badge update error", e)
            }
        }
    }

    private fun showEmergencyScreen(exception: Exception) {
        try {
            val emergencyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                setPadding(32, 64, 32, 32)
                gravity = Gravity.CENTER
            }
            
            emergencyLayout.addView(TextView(this).apply {
                text = "🆘"
                textSize = 64f
                gravity = Gravity.CENTER
            })
            
            emergencyLayout.addView(TextView(this).apply {
                text = "জরুরি অবস্থা!"
                textSize = 24f
                setTextColor(Color.RED)
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 8)
            })
            
            emergencyLayout.addView(TextView(this).apply {
                text = "এরর: ${exception.message}\n\nক্লিপবোর্ডে কপি করা হয়েছে।"
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            })
            
            emergencyLayout.addView(TextView(this).apply {
                text = "⚙️ সেটিংস খুলুন"
                textSize = 16f
                setTextColor(Color.parseColor("#1A73E8"))
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            })
            
            setContentView(emergencyLayout)
        } catch (e: Exception) {
            Log.e(TAG, "Emergency UI failed", e)
        }
    }

    // ═══════════════════════════════════════
    // 🛠️ UTILITY METHODS
    // ═══════════════════════════════════════

    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        errorLog.append(logEntry).append("\n")
        Log.d(TAG, logEntry)
        
        try {
            if (::errorLogTextView.isInitialized) {
                runOnUiThread {
                    errorLogTextView.text = errorLog.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Log update failed", e)
        }
    }

    private fun handleError(context: String, exception: Exception) {
        val errorMessage = buildString {
            append("══════════════════════\n")
            append("🚨 এরর: $context\n")
            append("⏰ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            append("💬 ${exception.message}\n")
            append("📚 ${getStackTrace(exception)}\n")
            append("══════════════════════\n")
        }
        
        errorLog.append(errorMessage).append("\n")
        Log.e(TAG, errorMessage)
        
        copyToClipboard(errorMessage, "এরর কপি করা হয়েছে")
        showToast("❌ $context", Color.parseColor("#FF4444"))
        
        try {
            if (::errorLogTextView.isInitialized) {
                runOnUiThread { errorLogTextView.text = errorLog.toString() }
            }
        } catch (e: Exception) {}
    }

    private fun getStackTrace(exception: Exception): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            sw.toString().take(500)
        } catch (e: Exception) {
            "Stack trace unavailable"
        }
    }

    private fun copyToClipboard(text: String, message: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nagad Log", text))
            showToast("📋 $message", Color.parseColor("#1A73E8"))
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard failed", e)
        }
    }

    private fun showToast(message: String, bgColor: Int) {
        runOnUiThread {
            try {
                val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.CENTER, 0, 0)
                val view = toast.view
                if (view != null) {
                    view.setBackgroundColor(bgColor)
                    val textView = view.findViewById<TextView>(android.R.id.message)
                    if (textView != null) {
                        textView.setTextColor(Color.WHITE)
                        textView.setPadding(24, 12, 24, 12)
                    }
                }
                toast.show()
            } catch (e: Exception) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAlert(title: String, message: String, buttonText: String, action: () -> Unit) {
        try {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(buttonText) { dialog, _ ->
                    action()
                    dialog.dismiss()
                }
                .setNegativeButton("বন্ধ") { dialog, _ -> dialog.dismiss() }
                .setNeutralButton("📋 কপি") { dialog, _ ->
                    copyToClipboard("$title\n$message", "কপি করা হয়েছে")
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Alert failed", e)
        }
    }

    private fun isDeviceAdmin(packageName: String): Boolean {
        return try {
            val admins = devicePolicyManager.activeAdmins
            admins?.any { it.packageName == packageName } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isSuspiciousAdmin(packageName: String): Boolean {
        val genuine = listOf(
            "com.google.android.gms", "com.android.settings",
            "com.samsung.", "com.xiaomi.", "com.huawei.", "com.konasl.nagad"
        )
        return !genuine.any { packageName.startsWith(it) }
    }

    private fun isGenuineSystemApp(packageName: String): Boolean {
        val genuine = listOf(
            "com.android.", "com.google.android.", "com.samsung.",
            "com.xiaomi.", "com.huawei.", "com.oppo.", "com.vivo."
        )
        return genuine.any { packageName.startsWith(it) }
    }

    private fun hasPermission(packageName: String, permission: String): Boolean {
        return try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppRunning(packageName: String): Boolean {
        return try {
            val processes = activityManager.runningAppProcesses
            processes?.any { it.processName == packageName || it.processName.startsWith("$packageName:") } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun openDeviceAdminSettings() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.android.settings",
                "com.android.settings.DeviceAdminSettings"
            )
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun removeOverlayView() {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {}
    }

    // ═══════════════════════════════════════
    // 📦 DATA CLASSES
    // ═══════════════════════════════════════

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val flags: Int = 0,
        val riskScore: Int = 0,
        val riskReasons: MutableList<String>? = null,
        val isMalware: Boolean = false,
        val isAdmin: Boolean = false
    )

    // ═══════════════════════════════════════
    // 📋 ADAPTER
    // ═══════════════════════════════════════

    inner class AppAdapter(private var apps: List<AppInfo>) :
        RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(createAppItemView(parent))
        }

        private fun createAppItemView(parent: ViewGroup): LinearLayout {
            return LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 14, 20, 14)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 4, 12, 4) }
                
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                
                // Icon
                addView(ImageView(parent.context).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 16, 0) }
                })
                
                // Text container
                val textContainer = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                textContainer.addView(TextView(parent.context).apply {
                    id = View.generateViewId()
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                })
                
                textContainer.addView(TextView(parent.context).apply {
                    id = View.generateViewId()
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                    setTypeface(Typeface.MONOSPACE)
                })
                
                addView(textContainer)
                
                // Action buttons container
                addView(LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    
                    addView(TextView(parent.context).apply {
                        text = "☠️"
                        textSize = 18f
                        setPadding(8, 0, 8, 0)
                        setOnClickListener {
                            val pos = getPosition(this)
                            if (pos != -1) {
                                val app = apps[pos]
                                activityManager.killBackgroundProcesses(app.packageName)
                                showToast("☠️ কিল: ${app.name}", Color.parseColor("#FF5722"))
                            }
                        }
                    })
                    
                    addView(TextView(parent.context).apply {
                        text = "🗑️"
                        textSize = 18f
                        setPadding(8, 0, 0, 0)
                        setOnClickListener {
                            val pos = getPosition(this)
                            if (pos != -1) {
                                val app = apps[pos]
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE)
                                    intent.data = Uri.parse("package:${app.packageName}")
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    showToast("আনইনস্টল ব্যর্থ", Color.parseColor("#FF4444"))
                                }
                            }
                        }
                    })
                })
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val container = holder.container
            
            val iconView = container.getChildAt(0) as ImageView
            val textContainer = container.getChildAt(1) as LinearLayout
            val nameView = textContainer.getChildAt(0) as TextView
            val packageView = textContainer.getChildAt(1) as TextView
            
            iconView.setImageDrawable(app.icon)
            
            // ম্যালওয়্যার হাইলাইট
            if (app.isMalware) {
                nameView.text = "☠️ ${app.name}"
                nameView.setTextColor(Color.parseColor("#FF4444"))
                container.setBackgroundColor(Color.parseColor("#2A0000"))
            } else if (app.isAdmin) {
                nameView.text = "⚙️ ${app.name}"
                nameView.setTextColor(Color.parseColor("#FF9800"))
                container.setBackgroundColor(Color.parseColor("#1E1E1E"))
            } else {
                nameView.text = app.name
                nameView.setTextColor(Color.WHITE)
                container.setBackgroundColor(Color.parseColor("#1E1E1E"))
            }
            
            packageView.text = app.packageName
            
            if (app.riskScore > 0) {
                packageView.text = "${app.packageName} | ⚠️ রিস্ক: ${app.riskScore}%"
            }
            
            container.setOnClickListener {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        showToast("লঞ্চ করা যাবে না", Color.parseColor("#FF9800"))
                    }
                } catch (e: Exception) {
                    showToast("এরর: ${e.message}", Color.parseColor("#FF4444"))
                }
            }
        }

        override fun getItemCount() = apps.size

        fun updateList(newList: List<AppInfo>) {
            apps = newList
            notifyDataSetChanged()
        }

        private fun getPosition(view: View): Int {
            var parent = view.parent
            while (parent != null) {
                if (parent is RecyclerView) {
                    return parent.getChildAdapterPosition(view.parent as View)
                }
                parent = parent.parent
            }
            return -1
        }
    }
}
