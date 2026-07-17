package com.a_rx.a1tourch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.os.Handler
import android.os.Looper

/**
 * TorchTileService — Custom Quick Settings Tile Service (Pull-down panel button)
 * to toggle the flashlight state with sync callbacks and long-press preference shortcuts.
 */
class TorchTileService : TileService() {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isReceiverRegistered = false
    private var maxTorchLevel = 1

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == cameraId) {
                Handler(Looper.getMainLooper()).post {
                    updateTileState(enabled)
                    // Sync state to widgets
                    notifyWidgetState(enabled, 1)
                }
            }
        }

        override fun onTorchStrengthLevelChanged(camId: String, newStrengthLevel: Int) {
            if (camId == cameraId) {
                Handler(Looper.getMainLooper()).post {
                    // Map back from hardware level to UI level (1..5)
                    val uiLevel = mapHardwareLevelToProgress(newStrengthLevel, maxTorchLevel)
                    notifyWidgetState(enabled = true, level = uiLevel)
                }
            }
        }
    }

    // Broadcast receiver to sync with widget toggles
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TorchWidgetProvider.ACTION_UPDATE_STATE) {
                val state = intent.getBooleanExtra(TorchWidgetProvider.EXTRA_STATE, false)
                updateTileState(state)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = findCameraWithFlash()
        
        // Query maximum strength support
        cameraId?.let { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    maxTorchLevel = characteristics.get(
                        CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
                    ) ?: 1
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        cameraManager.registerTorchCallback(callback, null)

        // Listen for state change broadcasts from widgets
        val filter = IntentFilter(TorchWidgetProvider.ACTION_UPDATE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        isReceiverRegistered = true
    }

    override fun onStopListening() {
        super.onStopListening()
        cameraManager.unregisterTorchCallback(callback)
        if (isReceiverRegistered) {
            unregisterReceiver(stateReceiver)
            isReceiverRegistered = false
        }
    }

    override fun onClick() {
        super.onClick()
        val id = cameraId ?: return
        val tile = qsTile ?: return
        val currentEnabled = tile.state == Tile.STATE_ACTIVE
        try {
            cameraManager.setTorchMode(id, !currentEnabled)
            updateTileState(!currentEnabled)
            notifyWidgetState(!currentEnabled, 1)
        } catch (e: CameraAccessException) {
            Log.e("TorchTile", "CameraAccessException toggling torch", e)
        }
    }

    private fun updateTileState(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "ON" else "OFF"
        }
        
        tile.updateTile()
    }

    private fun notifyWidgetState(enabled: Boolean, level: Int) {
        val intent = Intent(TorchWidgetProvider.ACTION_UPDATE_STATE).apply {
            putExtra(TorchWidgetProvider.EXTRA_STATE, enabled)
            putExtra(TorchWidgetProvider.EXTRA_LEVEL, level)
            `package` = packageName
        }
        sendBroadcast(intent)
    }

    private fun mapHardwareLevelToProgress(hardwareLevel: Int, maxLevel: Int): Int {
        if (maxLevel <= 1 || hardwareLevel <= 1) return 1
        if (hardwareLevel >= maxLevel) return 5 // Max UI snap level is 5
        val fraction = (hardwareLevel - 1).toFloat() / (maxLevel - 1).toFloat()
        return 1 + Math.round(fraction * 4) // Solve: 1 + round(fraction * (5 - 1))
    }

    private fun findCameraWithFlash(): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                return id
            }
        }
        return null
    }
}
