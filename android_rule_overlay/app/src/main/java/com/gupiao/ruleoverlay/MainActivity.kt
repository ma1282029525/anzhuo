package com.gupiao.ruleoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var rulesEditor: EditText
    private lateinit var permissionStatus: TextView
    private lateinit var textSizeLabel: TextView
    private lateinit var textSizeSeekBar: SeekBar

    private var pendingStartAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsRepository = SettingsRepository(this)

        permissionStatus = findViewById(R.id.textPermissionStatus)
        rulesEditor = findViewById(R.id.editRules)
        textSizeLabel = findViewById(R.id.textTextSizeValue)
        textSizeSeekBar = findViewById(R.id.seekTextSize)

        val config = settingsRepository.load()
        rulesEditor.setText(config.content)

        val initialSize = config.textSizeSp.toInt().coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        textSizeSeekBar.max = MAX_TEXT_SIZE_SP - MIN_TEXT_SIZE_SP
        textSizeSeekBar.progress = initialSize - MIN_TEXT_SIZE_SP
        updateTextSizeLabel(initialSize)

        textSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateTextSizeLabel(progress + MIN_TEXT_SIZE_SP)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<Button>(R.id.buttonGrantPermission).setOnClickListener {
            pendingStartAfterPermission = false
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.buttonSave).setOnClickListener {
            saveCurrentConfig()
            if (OverlayService.isRunning) {
                sendOverlayCommand(OverlayService.ACTION_REFRESH)
            }
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonStart).setOnClickListener {
            saveCurrentConfig()
            if (hasOverlayPermission()) {
                sendOverlayCommand(OverlayService.ACTION_SHOW)
            } else {
                pendingStartAfterPermission = true
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.buttonStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()

        if (pendingStartAfterPermission && hasOverlayPermission()) {
            pendingStartAfterPermission = false
            sendOverlayCommand(OverlayService.ACTION_SHOW)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun updatePermissionStatus() {
        permissionStatus.text = if (hasOverlayPermission()) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_missing)
        }
    }

    private fun updateTextSizeLabel(textSizeSp: Int) {
        textSizeLabel.text = getString(R.string.text_size_value, textSizeSp)
    }

    private fun saveCurrentConfig() {
        settingsRepository.saveContent(rulesEditor.text.toString())
        settingsRepository.saveTextSize(currentTextSizeSp().toFloat())
    }

    private fun currentTextSizeSp(): Int {
        return textSizeSeekBar.progress + MIN_TEXT_SIZE_SP
    }

    private fun sendOverlayCommand(action: String) {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        if (action == OverlayService.ACTION_SHOW) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private const val MIN_TEXT_SIZE_SP = 12
        private const val MAX_TEXT_SIZE_SP = 28
    }
}
