package com.a_rx.a1tourch

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews

/**
 * TorchWidgetProvider — Renders a custom horizontal glassmorphic App Widget
 * allowing users to toggle the flashlight and adjust its brightness levels
 * directly from their Home Screen.
 */
class TorchWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_TORCH = "com.a_rx.a1tourch.ACTION_TOGGLE_TORCH"
        const val ACTION_DECREASE_BRIGHTNESS = "com.a_rx.a1tourch.ACTION_DECREASE_BRIGHTNESS"
        const val ACTION_INCREASE_BRIGHTNESS = "com.a_rx.a1tourch.ACTION_INCREASE_BRIGHTNESS"
        const val ACTION_UPDATE_STATE = "com.a_rx.a1tourch.ACTION_UPDATE_STATE"
        
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_LEVEL = "extra_level"

        private var isTorchOn = false
        private var brightnessLevel = 1 // 1..5 scale
        private const val SLIDER_MAX_STEPS = 5
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_TORCH -> {
                toggleTorch(context)
            }
            ACTION_DECREASE_BRIGHTNESS -> {
                if (isTorchOn) adjustBrightness(context, -1)
            }
            ACTION_INCREASE_BRIGHTNESS -> {
                if (isTorchOn) adjustBrightness(context, 1)
            }
            ACTION_UPDATE_STATE -> {
                val state = intent.getBooleanExtra(EXTRA_STATE, isTorchOn)
                val level = intent.getIntExtra(EXTRA_LEVEL, brightnessLevel)
                isTorchOn = state
                brightnessLevel = level.coerceIn(1, SLIDER_MAX_STEPS)
                updateAllWidgets(context)
            }
        }
    }

    private fun toggleTorch(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = findCameraWithFlash(cameraManager) ?: return
            isTorchOn = !isTorchOn

            if (isTorchOn) {
                // Determine maximum brightness capability
                val maxLevel = queryMaxTorchLevel(cameraManager, cameraId)
                if (maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hardwareLevel = mapProgressToHardwareLevel(brightnessLevel, maxLevel)
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, hardwareLevel)
                } else {
                    cameraManager.setTorchMode(cameraId, true)
                }
            } else {
                cameraManager.setTorchMode(cameraId, false)
            }

            // Sync widget displays and broadcast updates to other elements
            updateAllWidgets(context)
            notifyStateChange(context)

        } catch (e: CameraAccessException) {
            Log.e("TorchWidget", "CameraAccessException toggling torch", e)
        }
    }

    private fun adjustBrightness(context: Context, direction: Int) {
        val newLevel = (brightnessLevel + direction).coerceIn(1, SLIDER_MAX_STEPS)
        if (newLevel == brightnessLevel) return // No change

        brightnessLevel = newLevel
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = findCameraWithFlash(cameraManager) ?: return
            val maxLevel = queryMaxTorchLevel(cameraManager, cameraId)

            if (maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hardwareLevel = mapProgressToHardwareLevel(brightnessLevel, maxLevel)
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, hardwareLevel)
            }

            updateAllWidgets(context)
            notifyStateChange(context)

        } catch (e: CameraAccessException) {
            Log.e("TorchWidget", "CameraAccessException adjusting brightness", e)
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TorchWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (widgetId in allWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_torch)

        // ── Render Power Toggle Button Animation ─────────────────────────────
        if (isTorchOn) {
            // ON state: warm gold background, black icon
            views.setInt(R.id.widgetBtnToggle, "setBackgroundResource", R.drawable.bg_torch_button_on)
            views.setInt(R.id.widgetIcon, "setColorFilter", Color.BLACK)
            
            // Enable control panel visually
            views.setFloat(R.id.widgetControlsLayout, "setAlpha", 1.0f)
        } else {
            // OFF state: frosted glass outline, white translucent icon
            views.setInt(R.id.widgetBtnToggle, "setBackgroundResource", R.drawable.bg_torch_button_off)
            views.setInt(R.id.widgetIcon, "setColorFilter", Color.parseColor("#66FFFFFF"))
            
            // Disable/dim control panel visually
            views.setFloat(R.id.widgetControlsLayout, "setAlpha", 0.3f)
        }

        // ── Render Brightness Level Details ──────────────────────────────────
        views.setTextViewText(R.id.widgetTextLevel, "Level $brightnessLevel")

        // ── Bind Click Listeners ─────────────────────────────────────────────
        // 1. Toggle Button
        views.setOnClickPendingIntent(
            R.id.widgetBtnToggle,
            createPendingBroadcast(context, ACTION_TOGGLE_TORCH)
        )

        // 2. Minus Button (Only active when torch is ON)
        views.setOnClickPendingIntent(
            R.id.widgetBtnMinus,
            if (isTorchOn) createPendingBroadcast(context, ACTION_DECREASE_BRIGHTNESS) else null
        )

        // 3. Plus Button (Only active when torch is ON)
        views.setOnClickPendingIntent(
            R.id.widgetBtnPlus,
            if (isTorchOn) createPendingBroadcast(context, ACTION_INCREASE_BRIGHTNESS) else null
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createPendingBroadcast(context: Context, actionString: String): PendingIntent {
        val intent = Intent(context, TorchWidgetProvider::class.java).apply {
            action = actionString
        }
        return PendingIntent.getBroadcast(
            context,
            actionString.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyStateChange(context: Context) {
        val updateIntent = Intent(ACTION_UPDATE_STATE).apply {
            putExtra(EXTRA_STATE, isTorchOn)
            putExtra(EXTRA_LEVEL, brightnessLevel)
            `package` = context.packageName
        }
        context.sendBroadcast(updateIntent)
    }

    // ── Helper camera queries ────────────────────────────────────────────────
    private fun findCameraWithFlash(cameraManager: CameraManager): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) return id
        }
        return null
    }

    private fun queryMaxTorchLevel(cameraManager: CameraManager, cameraId: String): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                return characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } catch (e: Exception) {
                // Fallback
            }
        }
        return 1
    }

    private fun mapProgressToHardwareLevel(progress: Int, maxLevel: Int): Int {
        if (maxLevel <= 1 || progress <= 1) return 1
        if (progress >= SLIDER_MAX_STEPS) return maxLevel
        val fraction = (progress - 1).toFloat() / (SLIDER_MAX_STEPS - 1).toFloat()
        return 1 + Math.round((maxLevel - 1) * fraction)
    }
}
