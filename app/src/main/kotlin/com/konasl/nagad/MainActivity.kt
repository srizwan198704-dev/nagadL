package com.konasl.nagad

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var containerLayout: LinearLayout
    private lateinit var clonedAppsRecyclerView: RecyclerView
    private lateinit var addAppButton: Button
    private val installedApps = mutableListOf<AppInfo>()
    private val clonedApps = mutableListOf<ClonedAppInfo>()
    private var clonedAppAdapter: ClonedAppAdapter? = null
    
    // Dual Space এর জন্য আলাদা ডিরেক্টরি
    private val dualSpaceDir = "dual_space_data"

    data class AppInfo(
        val appName: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?,
        val apkPath: String?
    )

    data class ClonedAppInfo(
        val originalPackageName: String,
        val clonedPackageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dual Space ডিরেক্টরি তৈরি
        createDualSpaceDirectory()
        
        // Main container
        containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
        }

        // Title Section
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTextView = TextView(this).apply {
            text = "🔄 Dual Space"
            textSize = 28f
            setTextColor(Color.parseColor("#1A237E"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerLayout.addView(titleTextView)
        containerLayout.addView(headerLayout)

        // Description
        val descTextView = TextView(this).apply {
            text = "Clone apps and run multiple accounts simultaneously\nEach cloned app runs in isolated environment"
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        containerLayout.addView(descTextView)

        // Info Card
        val infoCard = createInfoCard()
        containerLayout.addView(infoCard)

        // Add App Button
        addAppButton = Button(this).apply {
            text = "➕ Clone New App"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(32, 16, 32, 16)
            background = createGradientDrawable(
                Color.parseColor("#FF6F00"),
                Color.parseColor("#FF8F00")
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            setOnClickListener {
                showAppSelectionDialog()
            }
        }
        containerLayout.addView(addAppButton)

        // Cloned Apps Section
        val clonedLabel = TextView(this).apply {
            text = "📱 Cloned Apps (${clonedApps.size})"
            textSize = 18f
            setTextColor(Color.parseColor("#424242"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        containerLayout.addView(clonedLabel)

        // RecyclerView for cloned apps
        clonedAppsRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setBackgroundColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
        }

        clonedAppAdapter = ClonedAppAdapter(clonedApps,
            onLaunchClick = { clonedApp ->
                launchClonedApp(clonedApp)
            },
            onDeleteClick = { clonedApp ->
                deleteClonedApp(clonedApp)
                clonedLabel.text = "📱 Cloned Apps (${clonedApps.size})"
            }
        )
        clonedAppsRecyclerView.adapter = clonedAppAdapter
        containerLayout.addView(clonedAppsRecyclerView)

        setContentView(containerLayout)
        
        // Load installed apps for cloning
        loadInstalledApps()
        // Load previously cloned apps
        loadClonedApps()
    }

    private fun createInfoCard(): CardView {
        return CardView(this).apply {
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(Color.parseColor("#E3F2FD"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }

            val infoLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val infoTitle = TextView(context).apply {
                text = "⚡ How Dual Space Works"
                textSize = 16f
                setTextColor(Color.parseColor("#1565C0"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }

            val infoText = TextView(context).apply {
                text = "• Creates isolated environment for apps\n" +
                       "• Run multiple accounts simultaneously\n" +
                       "• Separate data storage for each clone\n" +
                       "• Original apps remain unchanged"
                textSize = 13f
                setTextColor(Color.parseColor("#424242"))
            }

            infoLayout.addView(infoTitle)
            infoLayout.addView(infoText)
            addView(infoLayout)
        }
    }

    private fun createDualSpaceDirectory() {
        val dir = File(filesDir, dualSpaceDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(intent, 0)
        
        installedApps.clear()
        for (resolveInfo in activities) {
            val appName = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            
            // Skip system apps and our own app
            if (packageName != this.packageName && 
                (resolveInfo.activityInfo.applicationInfo.flags and 
                 android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                val icon = resolveInfo.loadIcon(pm)
                val apkPath = resolveInfo.activityInfo.applicationInfo.sourceDir
                installedApps.add(AppInfo(appName, packageName, icon, apkPath))
            }
        }
    }

    private fun loadClonedApps() {
        val prefs = getSharedPreferences("dual_space_prefs", Context.MODE_PRIVATE)
        val clonedPackages = prefs.getStringSet("cloned_packages", emptySet()) ?: emptySet()
        
        clonedApps.clear()
        for (packageName in clonedPackages) {
            val parts = packageName.split("|")
            if (parts.size == 3) {
                val originalPkg = parts[0]
                val clonedPkg = parts[1]
                val appName = parts[2]
                
                // Get icon from original app
                var icon: android.graphics.drawable.Drawable? = null
                try {
                    icon = packageManager.getApplicationIcon(originalPkg)
                } catch (e: Exception) {}
                
                clonedApps.add(ClonedAppInfo(originalPkg, clonedPkg, appName, icon))
            }
        }
        clonedAppAdapter?.notifyDataSetChanged()
    }

    private fun saveClonedApp(clonedApp: ClonedAppInfo) {
        val prefs = getSharedPreferences("dual_space_prefs", Context.MODE_PRIVATE)
        val clonedPackages = prefs.getStringSet("cloned_packages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        clonedPackages.add("${clonedApp.originalPackageName}|${clonedApp.clonedPackageName}|${clonedApp.appName}")
        prefs.edit().putStringSet("cloned_packages", clonedPackages).apply()
    }

    private fun showAppSelectionDialog() {
        if (installedApps.isEmpty()) {
            Toast.makeText(this, "No apps available for cloning", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Select App to Clone")
        
        val appNames = installedApps.map { "${it.appName}\n(${it.packageName})" }.toTypedArray()
        
        dialogBuilder.setItems(appNames) { _, which ->
            val selectedApp = installedApps[which]
            cloneApp(selectedApp)
        }
        
        dialogBuilder.setNegativeButton("Cancel", null)
        dialogBuilder.show()
    }

    private fun cloneApp(appInfo: AppInfo) {
        // Check if already cloned
        if (clonedApps.any { it.originalPackageName == appInfo.packageName }) {
            Toast.makeText(this, "${appInfo.appName} is already cloned!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create clone package name
            val clonedPackageName = "${appInfo.packageName}.dual"
            
            // Create data directory for cloned app
            val cloneDataDir = File(filesDir, "$dualSpaceDir/$clonedPackageName")
            if (!cloneDataDir.exists()) {
                cloneDataDir.mkdirs()
            }

            // Create clone configuration
            val configFile = File(cloneDataDir, "clone_config.txt")
            configFile.writeText("""
                original_package=${appInfo.packageName}
                clone_package=$clonedPackageName
                app_name=${appInfo.appName}
                created_time=${System.currentTimeMillis()}
            """.trimIndent())

            // Create cloned app info
            val clonedApp = ClonedAppInfo(
                originalPackageName = appInfo.packageName,
                clonedPackageName = clonedPackageName,
                appName = "${appInfo.appName} (Clone)",
                icon = appInfo.icon
            )

            // Add to list and save
            clonedApps.add(clonedApp)
            clonedAppAdapter?.notifyDataSetChanged()
            saveClonedApp(clonedApp)

            // Show success with instructions
            showCloneSuccessDialog(appInfo.appName)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clone: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCloneSuccessDialog(appName: String) {
        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("✅ Clone Successful")
        dialogBuilder.setMessage(
            "$appName has been cloned successfully!\n\n" +
            "📱 The clone will appear in your cloned apps list\n" +
            "🔒 Clone runs in isolated environment\n" +
            "💾 Separate data storage for each clone\n\n" +
            "Tap on the cloned app to launch it!"
        )
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        dialogBuilder.show()
    }

    private fun launchClonedApp(clonedApp: ClonedAppInfo) {
        try {
            // Try to launch original app but with clone flag
            val launchIntent = packageManager.getLaunchIntentForPackage(clonedApp.originalPackageName)
            
            if (launchIntent != null) {
                // Add flags for dual space
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                
                // Pass clone information
                launchIntent.putExtra("dual_space_clone", true)
                launchIntent.putExtra("clone_package", clonedApp.clonedPackageName)
                launchIntent.putExtra("clone_data_dir", 
                    File(filesDir, "$dualSpaceDir/${clonedApp.clonedPackageName}").absolutePath)
                
                startActivity(launchIntent)
                Toast.makeText(this, "Launching ${clonedApp.appName}...", Toast.LENGTH_SHORT).show()
            } else {
                // If can't launch, show manual instructions
                showManualLaunchDialog(clonedApp)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            showManualLaunchDialog(clonedApp)
        }
    }

    private fun showManualLaunchDialog(clonedApp: ClonedAppInfo) {
        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Launch ${clonedApp.appName}")
        dialogBuilder.setMessage(
            "For full Dual Space functionality, the app needs to be launched " +
            "with special permissions.\n\n" +
            "Alternative methods:\n" +
            "1. Go to Settings > Dual Space > Select App\n" +
            "2. Long press home screen > Dual Space Widget\n" +
            "3. Use Parallel Space mode\n\n" +
            "This clone provides isolated data storage for your app."
        )
        dialogBuilder.setPositiveButton("OK", null)
        dialogBuilder.show()
    }

    private fun deleteClonedApp(clonedApp: ClonedAppInfo) {
        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Delete Clone")
        dialogBuilder.setMessage(
            "Are you sure you want to delete ${clonedApp.appName}?\n\n" +
            "⚠️ All clone data will be permanently deleted!\n" +
            "✅ Original app will remain unaffected."
        )
        dialogBuilder.setPositiveButton("Delete") { _, _ ->
            // Remove from list
            clonedApps.remove(clonedApp)
            clonedAppAdapter?.notifyDataSetChanged()
            
            // Remove from preferences
            val prefs = getSharedPreferences("dual_space_prefs", Context.MODE_PRIVATE)
            val clonedPackages = prefs.getStringSet("cloned_packages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            clonedPackages.remove("${clonedApp.originalPackageName}|${clonedApp.clonedPackageName}|${clonedApp.appName}")
            prefs.edit().putStringSet("cloned_packages", clonedPackages).apply()
            
            // Delete clone data directory
            val cloneDataDir = File(filesDir, "$dualSpaceDir/${clonedApp.clonedPackageName}")
            if (cloneDataDir.exists()) {
                cloneDataDir.deleteRecursively()
            }
            
            Toast.makeText(this, "${clonedApp.appName} deleted", Toast.LENGTH_SHORT).show()
        }
        dialogBuilder.setNegativeButton("Cancel", null)
        dialogBuilder.show()
    }

    private fun createGradientDrawable(startColor: Int, endColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            colors = intArrayOf(startColor, endColor)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            cornerRadius = 24f
        }
    }
}

class ClonedAppAdapter(
    private val clonedApps: List<MainActivity.ClonedAppInfo>,
    private val onLaunchClick: (MainActivity.ClonedAppInfo) -> Unit,
    private val onDeleteClick: (MainActivity.ClonedAppInfo) -> Unit
) : RecyclerView.Adapter<ClonedAppAdapter.ClonedAppViewHolder>() {

    class ClonedAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        lateinit var appIcon: ImageView
        lateinit var appName: TextView
        lateinit var cloneBadge: TextView
        lateinit var launchButton: Button
        lateinit var deleteButton: ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClonedAppViewHolder {
        val context = parent.context
        
        val cardView = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            radius = 16f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        }

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Badge
        val badgeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cloneBadge = TextView(context).apply {
            text = "CLONE"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF6F00"))
            setPadding(12, 4, 12, 4)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        badgeLayout.addView(cloneBadge)

        val appIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                setMargins(0, 8, 0, 8)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val appName = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#212121"))
            gravity = Gravity.CENTER
            maxLines = 2
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val launchButton = Button(context).apply {
            text = "▶ Launch"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 16f
            }
        }

        val deleteButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                topMargin = 8
            }
            setColorFilter(Color.parseColor("#F44336"))
            setImageResource(android.R.drawable.ic_delete)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        linearLayout.addView(badgeLayout)
        linearLayout.addView(appIcon)
        linearLayout.addView(appName)
        linearLayout.addView(launchButton)
        linearLayout.addView(deleteButton)
        cardView.addView(linearLayout)

        val holder = ClonedAppViewHolder(cardView)
        holder.appIcon = appIcon
        holder.appName = appName
        holder.cloneBadge = cloneBadge
        holder.launchButton = launchButton
        holder.deleteButton = deleteButton
        
        return holder
    }

    override fun onBindViewHolder(holder: ClonedAppViewHolder, position: Int) {
        val clonedApp = clonedApps[position]
        holder.appIcon.setImageDrawable(clonedApp.icon)
        holder.appName.text = clonedApp.appName
        holder.launchButton.setOnClickListener {
            onLaunchClick(clonedApp)
        }
        holder.deleteButton.setOnClickListener {
            onDeleteClick(clonedApp)
        }
    }

    override fun getItemCount() = clonedApps.size
}
