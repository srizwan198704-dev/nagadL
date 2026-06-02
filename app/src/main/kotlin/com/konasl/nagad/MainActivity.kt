package com.konasl.nagad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "NagadApp"
        private const val ERROR_PREFIX = "ERROR: "
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var errorLogTextView: TextView
    private lateinit var mainLayout: LinearLayout
    
    private var allApps = mutableListOf<AppInfo>()
    private var filteredApps = mutableListOf<AppInfo>()
    private lateinit var appAdapter: AppAdapter
    
    private val errorLog = StringBuilder()
    private var hasOverlayPermission = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            logMessage("অ্যাপ শুরু হচ্ছে...")
            
            // ওভারলে পারমিশন চেক
            checkOverlayPermission()
            
            // পুরো UI প্রোগ্রামাটিকলি তৈরি
            mainLayout = createMainLayout()
            setContentView(mainLayout)
            
            // ওভারলে ব্লক করতে ফ্লাগ সেট
            preventOverlay()
            
            // অ্যাপ লোড করুন
            loadApps()
            
            logMessage("অ্যাপ সফলভাবে শুরু হয়েছে ✓")
            
        } catch (e: Exception) {
            handleError("অ্যাপ শুরু করতে ব্যর্থ", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            preventOverlay()
            // রিজিউম হলে ওভারলে স্ট্যাটাস চেক
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasOverlayPermission = Settings.canDrawOverlays(this)
                updateSecurityBadge()
            }
        } catch (e: Exception) {
            handleError("onResume এ সমস্যা", e)
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                preventOverlay()
            } catch (e: Exception) {
                handleError("WindowFocus এ সমস্যা", e)
            }
        }
    }
    
    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        errorLog.append(logEntry).append("\n")
        Log.d(TAG, logEntry)
        
        // UI আপডেট যদি errorLogTextView তৈরি হয়ে থাকে
        try {
            if (::errorLogTextView.isInitialized) {
                runOnUiThread {
                    errorLogTextView.text = errorLog.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "লগ আপডেট করতে ব্যর্থ", e)
        }
    }
    
    private fun handleError(context: String, exception: Exception) {
        val errorMessage = buildString {
            append("=== এরর ডিটেইলস ===\n")
            append("সময়: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())).append("\n")
            append("জায়গা: ").append(context).append("\n")
            append("এরর টাইপ: ").append(exception.javaClass.simpleName).append("\n")
            append("মেসেজ: ").append(exception.message ?: "কোনো মেসেজ নেই").append("\n")
            append("\nস্ট্যাক ট্রেস:\n")
            append(getStackTrace(exception))
            append("\n=== এরর শেষ ===\n")
        }
        
        // লগ করুন
        errorLog.append(errorMessage).append("\n")
        Log.e(TAG, errorMessage)
        
        // ক্লিপবোর্ডে কপি করুন
        copyToClipboard(errorMessage, "এরর ডিটেইলস কপি করা হয়েছে")
        
        // টোস্ট দেখান
        showErrorToast(context, exception)
        
        // UI আপডেট
        try {
            if (::errorLogTextView.isInitialized) {
                runOnUiThread {
                    errorLogTextView.text = errorLog.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ত্রুটি UI আপডেট করতে ব্যর্থ", e)
        }
    }
    
    private fun getStackTrace(exception: Exception): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "স্ট্যাক ট্রেস পেতে ব্যর্থ: ${e.message}"
        }
    }
    
    private fun copyToClipboard(text: String, successMessage: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Nagad Error Log", text)
            clipboard.setPrimaryClip(clip)
            
            runOnUiThread {
                Toast.makeText(this, "📋 $successMessage", Toast.LENGTH_LONG).show()
            }
            
            logMessage("ক্লিপবোর্ডে কপি করা হয়েছে: $successMessage")
            
        } catch (e: Exception) {
            Log.e(TAG, "ক্লিপবোর্ডে কপি করতে ব্যর্থ", e)
            runOnUiThread {
                Toast.makeText(this, "❌ ক্লিপবোর্ডে কপি করতে ব্যর্থ", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showErrorToast(context: String, exception: Exception) {
        val shortMessage = try {
            "${exception.javaClass.simpleName}: ${exception.message?.take(50) ?: "Unknown error"}"
        } catch (e: Exception) {
            "Unknown error occurred"
        }
        
        runOnUiThread {
            try {
                val toast = Toast.makeText(
                    this,
                    "❌ $context\n$shortMessage",
                    Toast.LENGTH_LONG
                )
                toast.setGravity(Gravity.CENTER, 0, 0)
                
                // টোস্ট ভিউ কাস্টমাইজ
                val toastView = toast.view
                if (toastView != null) {
                    toastView.setBackgroundColor(Color.parseColor("#FF4444"))
                    val textView = toastView.findViewById<TextView>(android.R.id.message)
                    if (textView != null) {
                        textView.setTextColor(Color.WHITE)
                        textView.textSize = 14f
                    }
                }
                
                toast.show()
            } catch (e: Exception) {
                Log.e(TAG, "টোস্ট দেখাতে ব্যর্থ", e)
            }
        }
    }
    
    private fun preventOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    )
                    logMessage("ওভারলে ব্লক সক্রিয় ✓")
                } else {
                    logMessage("⚠️ ওভারলে পারমিশন নেই")
                }
            } else {
                logMessage("ওভারলে ব্লক: SDK ভার্সন ${Build.VERSION.SDK_INT}, প্রযোজ্য নয়")
            }
        } catch (e: Exception) {
            handleError("ওভারলে ব্লক করতে ব্যর্থ", e)
        }
    }
    
    private fun checkOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasOverlayPermission = Settings.canDrawOverlays(this)
                
                if (!hasOverlayPermission) {
                    logMessage("ওভারলে পারমিশন নেই, ডায়ালগ দেখানো হচ্ছে")
                    
                    AlertDialog.Builder(this)
                        .setTitle("🔒 ওভারলে পারমিশন প্রয়োজন")
                        .setMessage("অন্যান্য অ্যাপকে আপনার স্ক্রিনের উপর প্রদর্শন থেকে বিরত রাখতে এই পারমিশনটি প্রয়োজন।\n\nপারমিশন দিলে আপনার নিরাপত্তা নিশ্চিত হবে।")
                        .setPositiveButton("✅ পারমিশন দিন") { dialog, _ ->
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                                logMessage("পারমিশন পেজ খোলা হয়েছে")
                            } catch (e: Exception) {
                                handleError("পারমিশন পেজ খুলতে ব্যর্থ", e)
                                dialog.dismiss()
                            }
                        }
                        .setNegativeButton("❌ বাদ দিন") { dialog, _ ->
                            logMessage("ব্যবহারকারী পারমিশন দিতে অস্বীকার করেছেন")
                            Toast.makeText(
                                this,
                                "⚠️ পারমিশন ছাড়া ওভারলে ব্লক কাজ করবে না",
                                Toast.LENGTH_LONG
                            ).show()
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    logMessage("ওভারলে পারমিশন আছে ✓")
                }
            } else {
                logMessage("ওভারলে পারমিশন: SDK ${Build.VERSION.SDK_INT}, প্রযোজ্য নয়")
            }
        } catch (e: Exception) {
            handleError("পারমিশন চেক করতে ব্যর্থ", e)
        }
    }
    
    private fun updateSecurityBadge() {
        try {
            val securityBadge = mainLayout.findViewWithTag<LinearLayout>("securityBadge")
            if (securityBadge != null) {
                val badgeText = securityBadge.getChildAt(0) as? TextView
                if (badgeText != null) {
                    if (hasOverlayPermission) {
                        badgeText.text = "🔒 সুরক্ষিত"
                        securityBadge.setBackgroundColor(Color.parseColor("#34A853"))
                    } else {
                        badgeText.text = "⚠️ অরক্ষিত"
                        securityBadge.setBackgroundColor(Color.parseColor("#FF4444"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "সিকিউরিটি ব্যাজ আপডেট করতে ব্যর্থ", e)
        }
    }
    
    private fun createMainLayout(): LinearLayout {
        return try {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F0F2F5"))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // হেডার
                addView(createHeader())
                
                // সার্চ বার
                addView(createSearchBar())
                
                // স্ট্যাটাস বার
                addView(createStatusBar())
                
                // RecyclerView
                recyclerView = RecyclerView(this@MainActivity).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutManager = LinearLayoutManager(this@MainActivity)
                }
                addView(recyclerView)
                
                // এরর লগ সেকশন
                addView(createErrorLogSection())
            }
        } catch (e: Exception) {
            handleError("মেইন লেআউট তৈরি করতে ব্যর্থ", e)
            // ফ্যালব্যাক লেআউট
            LinearLayout(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = "লেআউট তৈরি করতে ব্যর্থ হয়েছে। দয়া করে এরর লগ দেখুন।"
                    setTextColor(Color.RED)
                    textSize = 16f
                    setPadding(16, 16, 16, 16)
                })
            }
        }
    }
    
    private fun createHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A73E8"))
            setPadding(24, 48, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // অ্যাপ আইকন
            addView(TextView(this@MainActivity).apply {
                text = "📱"
                textSize = 28f
                setPadding(0, 0, 16, 0)
            })
            
            // টাইটেল
            addView(TextView(this@MainActivity).apply {
                text = "Nagad Launcher"
                textSize = 20f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })
            
            // সিকিউরিটি ব্যাজ
            addView(createSecurityBadge())
            
            // কপি লগ বাটন
            addView(TextView(this@MainActivity).apply {
                text = "📋"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(16, 0, 0, 0)
                
                setOnClickListener {
                    try {
                        copyToClipboard(errorLog.toString(), "সম্পূর্ণ লগ কপি করা হয়েছে")
                    } catch (e: Exception) {
                        handleError("লগ কপি করতে ব্যর্থ", e)
                    }
                }
            })
        }
    }
    
    private fun createSecurityBadge(): LinearLayout {
        return LinearLayout(this).apply {
            tag = "securityBadge"
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(
                if (hasOverlayPermission) Color.parseColor("#34A853")
                else Color.parseColor("#FF4444")
            )
            setPadding(12, 6, 12, 6)
            
            val shape = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(
                    if (hasOverlayPermission) Color.parseColor("#34A853")
                    else Color.parseColor("#FF4444")
                )
            }
            background = shape
            
            addView(TextView(this@MainActivity).apply {
                text = if (hasOverlayPermission) "🔒 সুরক্ষিত" else "⚠️ অরক্ষিত"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }
    
    private fun createSearchBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 24, 24, 0)
            }
            
            val shape = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.WHITE)
                setStroke(1, Color.parseColor("#DDDDDD"))
            }
            background = shape
            elevation = 4f
            
            // সার্চ আইকন
            addView(TextView(this@MainActivity).apply {
                text = "🔍"
                textSize = 18f
                setPadding(0, 0, 12, 0)
            })
            
            // সার্চ ইনপুট
            searchEditText = EditText(this@MainActivity).apply {
                hint = "অ্যাপ খুঁজুন..."
                textSize = 16f
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.BLACK)
                setHintTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        try {
                            filterApps(s?.toString() ?: "")
                        } catch (e: Exception) {
                            handleError("অ্যাপ ফিল্টার করতে ব্যর্থ", e)
                        }
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            addView(searchEditText)
            
            // ক্লিয়ার বাটন
            val clearButton = TextView(this@MainActivity).apply {
                text = "✕"
                textSize = 18f
                setTextColor(Color.GRAY)
                setPadding(12, 0, 0, 0)
                visibility = View.GONE
                
                setOnClickListener {
                    searchEditText.text.clear()
                    visibility = View.GONE
                }
            }
            addView(clearButton)
            
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }
    
    private fun createStatusBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 8, 32, 8)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            addView(TextView(this@MainActivity).apply {
                id = View.generateViewId()
                text = "ইনস্টল করা অ্যাপ: 0"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                tag = "appCountText"
            })
            
            // রিফ্রেশ বাটন
            addView(TextView(this@MainActivity).apply {
                text = "🔄"
                textSize = 20f
                setPadding(16, 0, 0, 0)
                
                setOnClickListener {
                    try {
                        loadApps()
                        Toast.makeText(this@MainActivity, "অ্যাপ লিস্ট রিফ্রেশ হয়েছে", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        handleError("রিফ্রেশ করতে ব্যর্থ", e)
                    }
                }
            })
        }
    }
    
    private fun createErrorLogSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                150
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            
            // এরর লগ হেডার
            val headerLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 4)
            }
            
            headerLayout.addView(TextView(this@MainActivity).apply {
                text = "📝 এরর লগ"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })
            
            headerLayout.addView(TextView(this@MainActivity).apply {
                text = "ক্লিয়ার"
                textSize = 12f
                setTextColor(Color.parseColor("#1A73E8"))
                setPadding(8, 4, 8, 4)
                
                setOnClickListener {
                    errorLog.clear()
                    errorLogTextView.text = ""
                    Toast.makeText(this@MainActivity, "লগ ক্লিয়ার করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            })
            
            addView(headerLayout)
            
            // স্ক্রলেবল এরর লগ
            val scrollView = ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.WHITE)
            }
            
            errorLogTextView = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                text = errorLog.toString()
                textSize = 10f
                setTextColor(Color.parseColor("#333333"))
                setPadding(8, 8, 8, 8)
                setTypeface(Typeface.MONOSPACE)
                setTextIsSelectable(true)
            }
            
            scrollView.addView(errorLogTextView)
            addView(scrollView)
        }
    }
    
    private fun loadApps() {
        try {
            logMessage("অ্যাপ লোড করা শুরু...")
            allApps.clear()
            val pm = packageManager
            
            try {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                var loadCount = 0
                
                for (app in packages) {
                    try {
                        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null && app.packageName != packageName) {
                            val appInfo = AppInfo(
                                name = app.loadLabel(pm).toString(),
                                packageName = app.packageName,
                                icon = app.loadIcon(pm)
                            )
                            allApps.add(appInfo)
                            loadCount++
                        }
                    } catch (e: Exception) {
                        logMessage("প্যাকেজ লোড করতে ব্যর্থ: ${app.packageName} - ${e.message}")
                    }
                }
                
                logMessage("সফলভাবে $loadCount টি অ্যাপ লোড হয়েছে")
                
            } catch (e: Exception) {
                handleError("প্যাকেজ ম্যানেজার থেকে অ্যাপ পেতে ব্যর্থ", e)
            }
            
            allApps.sortBy { it.name.lowercase() }
            filteredApps = ArrayList(allApps)
            
            appAdapter = AppAdapter(filteredApps)
            recyclerView.adapter = appAdapter
            
            updateAppCount()
            logMessage("অ্যাপ লোড সম্পন্ন ✓")
            
        } catch (e: Exception) {
            handleError("অ্যাপ লোড করতে ব্যর্থ", e)
            Toast.makeText(this, "অ্যাপ লোড করতে ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun filterApps(query: String) {
        try {
            filteredApps = if (query.isEmpty()) {
                ArrayList(allApps)
            } else {
                allApps.filter {
                    it.name.contains(query, true) || 
                    it.packageName.contains(query, true)
                }.toMutableList()
            }
            
            appAdapter.updateList(filteredApps)
            updateAppCount()
            
        } catch (e: Exception) {
            handleError("অ্যাপ ফিল্টার করতে ব্যর্থ", e)
        }
    }
    
    private fun updateAppCount() {
        try {
            val countText = mainLayout.findViewWithTag<TextView>("appCountText")
            countText?.text = "ইনস্টল করা অ্যাপ: ${filteredApps.size}"
        } catch (e: Exception) {
            Log.e(TAG, "অ্যাপ কাউন্ট আপডেট করতে ব্যর্থ", e)
        }
    }
    
    // ডেটা ক্লাস
    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )
    
    // অ্যাডাপ্টার ক্লাস
    inner class AppAdapter(private var apps: List<AppInfo>) : 
        RecyclerView.Adapter<AppAdapter.ViewHolder>() {
        
        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return try {
                ViewHolder(createAppItemView(parent))
            } catch (e: Exception) {
                handleError("ViewHolder তৈরি করতে ব্যর্থ", e)
                // ফ্যালব্যাক ভিউ
                ViewHolder(LinearLayout(parent.context).apply {
                    addView(TextView(parent.context).apply {
                        text = "ভিউ তৈরি করতে ব্যর্থ"
                        setTextColor(Color.RED)
                    })
                })
            }
        }
        
        private fun createAppItemView(parent: ViewGroup): LinearLayout {
            return LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 16, 24, 16)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                setBackgroundColor(Color.WHITE)
                (layoutParams as RecyclerView.LayoutParams).setMargins(16, 4, 16, 4)
                
                // আইকন
                val iconView = ImageView(parent.context).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                        setMargins(0, 0, 16, 0)
                    }
                }
                addView(iconView)
                
                // টেক্সট কন্টেইনার
                val textContainer = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                
                // অ্যাপ নাম
                val nameView = TextView(parent.context).apply {
                    id = View.generateViewId()
                    textSize = 16f
                    setTextColor(Color.parseColor("#1A1A1A"))
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 0, 0, 4)
                }
                textContainer.addView(nameView)
                
                // প্যাকেজ নাম
                val packageView = TextView(parent.context).apply {
                    id = View.generateViewId()
                    textSize = 12f
                    setTextColor(Color.parseColor("#666666"))
                }
                textContainer.addView(packageView)
                
                addView(textContainer)
                
                // লঞ্চ বাটন
                val launchButton = TextView(parent.context).apply {
                    text = "▶"
                    textSize = 20f
                    setTextColor(Color.parseColor("#1A73E8"))
                    setPadding(16, 0, 0, 0)
                    gravity = Gravity.CENTER
                    
                    setOnClickListener {
                        try {
                            val position = getPositionForView(this)
                            if (position != -1 && position < apps.size) {
                                launchApp(apps[position])
                            }
                        } catch (e: Exception) {
                            handleError("লঞ্চ বাটন কাজ করেনি", e)
                        }
                    }
                }
                addView(launchButton)
            }
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                if (position < apps.size) {
                    val app = apps[position]
                    val container = holder.container
                    
                    val iconView = container.getChildAt(0) as ImageView
                    val nameView = (container.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
                    val packageView = (container.getChildAt(1) as LinearLayout).getChildAt(1) as TextView
                    
                    iconView.setImageDrawable(app.icon)
                    nameView.text = app.name
                    packageView.text = app.packageName
                    
                    container.setOnClickListener {
                        launchApp(app)
                    }
                }
            } catch (e: Exception) {
                handleError("ViewHolder বাইন্ড করতে ব্যর্থ পজিশন $position", e)
            }
        }
        
        override fun getItemCount() = apps.size
        
        fun updateList(newList: List<AppInfo>) {
            apps = newList
            notifyDataSetChanged()
        }
        
        private fun getPositionForView(view: View): Int {
            var parent = view.parent
            while (parent != null) {
                if (parent is RecyclerView) {
                    return parent.getChildAdapterPosition(view.parent as View)
                }
                parent = parent.parent
            }
            return -1
        }
        
        private fun launchApp(app: AppInfo) {
            try {
                logMessage("লঞ্চ করার চেষ্টা: ${app.name} (${app.packageName})")
                
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    
                    val successMsg = "${app.name} সফলভাবে চালু হয়েছে ✓"
                    logMessage(successMsg)
                    Toast.makeText(this@MainActivity, successMsg, Toast.LENGTH_SHORT).show()
                    
                } else {
                    val failMsg = "${app.name} চালু করা যাচ্ছে না - লঞ্চ ইন্টেন্ট নেই"
                    logMessage(ERROR_PREFIX + failMsg)
                    
                    val errorDetail = buildString {
                        append("অ্যাপ লঞ্চ করতে ব্যর্থ\n")
                        append("অ্যাপ: ${app.name}\n")
                        append("প্যাকেজ: ${app.packageName}\n")
                        append("কারণ: লঞ্চ ইন্টেন্ট পাওয়া যায়নি\n")
                        append("সময়: ").append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date()))
                    }
                    
                    copyToClipboard(errorDetail, "এরর ডিটেইলস কপি করা হয়েছে")
                    
                    showErrorDialog(app.name, "লঞ্চ ইন্টেন্ট পাওয়া যায়নি")
                }
                
            } catch (e: Exception) {
                val errorMsg = "${app.name} চালু করতে এরর: ${e.message}"
                handleError(errorMsg, e)
                
                val errorDetail = buildString {
                    append("=== অ্যাপ লঞ্চ এরর ===\n")
                    append("অ্যাপ: ${app.name}\n")
                    append("প্যাকেজ: ${app.packageName}\n")
                    append("এরর: ${e.message}\n")
                    append("স্ট্যাক ট্রেস:\n")
                    append(getStackTrace(e))
                }
                
                copyToClipboard(errorDetail, "এরর ডিটেইলস কপি করা হয়েছে")
                showErrorDialog(app.name, e.message ?: "অজানা এরর")
            }
        }
        
        private fun showErrorDialog(appName: String, errorMessage: String) {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("❌ অ্যাপ চালু করতে ব্যর্থ")
                    .setMessage("$appName চালু করা যায়নি।\n\nকারণ: $errorMessage\n\nএরর ডিটেইলস ক্লিপবোর্ডে কপি করা হয়েছে।")
                    .setPositiveButton("ঠিক আছে") { dialog, _ -> dialog.dismiss() }
                    .setNeutralButton("📋 কপি করুন") { dialog, _ ->
                        copyToClipboard(
                            "অ্যাপ: $appName\nএরর: $errorMessage",
                            "এরর মেসেজ কপি করা হয়েছে"
                        )
                        dialog.dismiss()
                    }
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "এরর ডায়ালগ দেখাতে ব্যর্থ", e)
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}
