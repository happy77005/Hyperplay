package com.example.first

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.os.Build
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.first.engine.DacHelper
import com.google.android.material.switchmaterial.SwitchMaterial
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.graphics.Color

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnChangeFolder).setOnClickListener {
            val songCache = SongCache(this)
            songCache.clearCache()
            
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Restart the app from LandingActivity and clear the backstack
            val intent = Intent(this, LandingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.btnRefreshScan).setOnClickListener {
            val songCache = SongCache(this)
            songCache.clearCache()
            
            // Go back to MainActivity with FORCE_RESCAN flag
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("FORCE_RESCAN", true)
            // Retrieve current settings to ensure scan type is preserved
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            intent.putExtra("SCAN_TYPE", prefs.getString("SCAN_TYPE", "FULL"))
            intent.putExtra("FOLDER_URI", prefs.getString("FOLDER_URI", null))
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        val switchPause = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchPauseOnDisconnect)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        // Default is TRUE per user request
        switchPause.isChecked = prefs.getBoolean("pause_on_disconnect", true)
        
        switchPause.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pause_on_disconnect", isChecked).apply()
        }

        val switchGapless = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGapless)
        // Default is TRUE
        switchGapless.isChecked = prefs.getBoolean("gapless_playback", true)
        
        switchGapless.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gapless_playback", isChecked).apply()
        }

        // ── Audio Engine Selection (3-way mutual exclusion) ──────────────────
        val rbNormal    = findViewById<RadioButton>(R.id.rbEngineNormal)
        val rbHiRes     = findViewById<RadioButton>(R.id.rbEngineHiRes)
        val rbUsbDirect = findViewById<RadioButton>(R.id.rbEngineUsbDirect)
        val btnNormal    = findViewById<CardView>(R.id.btnEngineNormal)
        val btnHiRes     = findViewById<CardView>(R.id.btnEngineHiRes)
        val btnUsbDirect = findViewById<CardView>(R.id.btnEngineUsbDirect)

        val currentEngine = prefs.getString("pref_audio_engine", "HI_RES")
        rbNormal.isChecked    = currentEngine == "NORMAL"
        rbHiRes.isChecked     = currentEngine == "HI_RES"
        rbUsbDirect.isChecked = currentEngine == "USB_DAC"

        val switchResonance = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchResonance)
        switchResonance.isChecked = prefs.getBoolean("pref_resonance_enabled", false)
        switchResonance.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_resonance_enabled", isChecked).apply()
            
            // Send broadcast for immediate update
            val intent = Intent("com.example.first.ACTION_SET_RESONANCE").apply { setPackage(packageName) }
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)

            val status = if (isChecked) "Enabled" else "Disabled"
            android.widget.Toast.makeText(this, "3D Resonance Audio: $status", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Bit-Perfect Logic
        val switchBitPerfect = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBitPerfect)
        switchBitPerfect.isChecked = prefs.getBoolean("pref_bit_perfect", false)
        switchBitPerfect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_bit_perfect", isChecked).apply()
        }

        // Logic to update Native Tuning UI + Bit-Perfect
        fun updateNativeTuningUI(isHiRes: Boolean) {
            val container = findViewById<android.widget.LinearLayout>(R.id.layoutNativeTuning)
            val sb = findViewById<android.widget.SeekBar>(R.id.sbPreAmp)
            val cardPre = findViewById<androidx.cardview.widget.CardView>(R.id.cardPreAmp)
            val cardRes = findViewById<androidx.cardview.widget.CardView>(R.id.cardResonance)
            val cardBP = findViewById<androidx.cardview.widget.CardView>(R.id.cardBitPerfect)
            
            container.alpha = if (isHiRes) 1.0f else 0.4f
            cardBP.alpha = if (isHiRes) 1.0f else 0.4f
            
            sb.isEnabled = isHiRes
            cardPre.isEnabled = isHiRes
            cardRes.isEnabled = isHiRes
            switchResonance.isEnabled = isHiRes
            switchBitPerfect.isEnabled = isHiRes
            cardBP.isEnabled = isHiRes
            
            val clickListener = if (!isHiRes) View.OnClickListener { 
                android.widget.Toast.makeText(this, "Requires Hi-Res Engine", android.widget.Toast.LENGTH_SHORT).show()
            } else null
            
            if (!isHiRes) {
                 container.setOnClickListener(clickListener)
                 cardPre.setOnClickListener(clickListener)
                 cardRes.setOnClickListener(clickListener)
                 cardBP.setOnClickListener(clickListener)
            } else {
                 container.setOnClickListener(null)
                 cardPre.setOnClickListener(null)
                 cardRes.setOnClickListener(null)
                 cardBP.setOnClickListener(null)
            }
        }

        // Initial State
        val sbPreAmp = findViewById<android.widget.SeekBar>(R.id.sbPreAmp)

        updateNativeTuningUI(currentEngine == "HI_RES")

        fun selectEngine(key: String) {
            rbNormal.isChecked    = key == "NORMAL"
            rbHiRes.isChecked     = key == "HI_RES"
            rbUsbDirect.isChecked = key == "USB_DAC"
            prefs.edit().putString("pref_audio_engine", key).apply()
            updateNativeTuningUI(key == "HI_RES")
        }

        btnNormal.setOnClickListener    { selectEngine("NORMAL")  }
        btnHiRes.setOnClickListener     { selectEngine("HI_RES")  }
        btnUsbDirect.setOnClickListener { selectEngine("USB_DAC") }



        // ── USB DAC Bypass switch (default ON) ──────────────────────────────
        val switchBypass = findViewById<SwitchMaterial>(R.id.switchUsbDacBypass)
        switchBypass.isChecked = prefs.getBoolean("pref_usb_dac_bypass", true)
        switchBypass.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_usb_dac_bypass", isChecked).apply()
            val msg = if (isChecked)
                "DAC bypass ON — USB Direct auto-activates on plug"
            else
                "DAC bypass OFF — plugging DAC won't change engine"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Hook up PreAmp to Service immediately
        val tvPreAmpLabel = findViewById<TextView>(R.id.tvPreAmpLabel)
        
        // Initial setup
        val savedProgress = prefs.getInt("pref_preamp_progress", 220) // Default +2.0dB (200=0dB, 220=+2.0dB)
        sbPreAmp.progress = savedProgress
        val initialDb = (savedProgress - 200) / 10f
        tvPreAmpLabel.text = "Pre-Amp Boost (${if (initialDb > 0) "+" else ""}${String.format("%.1f", initialDb)} dB)"

        sbPreAmp.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                // Calculate dB (-20.0 to +20.0)
                val db = (progress - 200) / 10f
                
                // Update Label
                tvPreAmpLabel.text = "Pre-Amp Boost (${if (db > 0) "+" else ""}${String.format("%.1f", db)} dB)"
                
                if (fromUser) {
                     // Haptic Feedback for each increment
                     if (Build.VERSION.SDK_INT >= 27) {
                         seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                     } else {
                         seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                     }

                     prefs.edit().putInt("pref_preamp_progress", progress).apply()
                     
                     // Send broadcast for immediate update if service is running
                     val intent = Intent("com.example.first.ACTION_SET_PREAMP").apply { setPackage(packageName) }
                     intent.putExtra("db", db)
                     sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(p0: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(p0: android.widget.SeekBar?) {}
        })




        // ── Battery Optimization ──────────────────────────────────────────
        findViewById<View>(R.id.btnBatteryOptimization).setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }

        findViewById<View>(R.id.btnSystemAppInfo).setOnClickListener {
            openAppInfo()
        }

        updateBatteryOptUI()
    }



    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Could not open app info", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptUI()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun updateBatteryOptUI() {
        val isIgnoring = isIgnoringBatteryOptimizations()
        val tvStatus = findViewById<TextView>(R.id.tvBatteryOptStatus)
        val tvDesc = findViewById<TextView>(R.id.tvBatteryOptDesc)
        val btn = findViewById<View>(R.id.btnBatteryOptimization)

        if (isIgnoring) {
            tvStatus.text = "Optimized (OFF)"
            tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            tvDesc.text = "Background playback is stable"
            btn.alpha = 0.6f
            btn.isEnabled = false
        } else {
            tvStatus.text = "Active"
            tvStatus.setTextColor(findViewById<TextView>(R.id.tvPreAmpLabel).textColors)
            tvDesc.text = "Tap to fix background playback stopping"
            btn.alpha = 1.0f
            btn.isEnabled = true
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery settings if specific request fails
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    android.widget.Toast.makeText(this, "Could not open battery settings", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
