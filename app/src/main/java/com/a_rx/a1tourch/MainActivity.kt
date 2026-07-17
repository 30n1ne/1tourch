package com.a_rx.a1tourch

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import android.content.Intent
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * MainActivity — Controls the device's hardware flashlight (torch) with variable brightness
 * wrapped in an Apple Vision Pro / iOS 18 glassmorphism fluid theme.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TorchControl"
        private const val SLIDER_MAX_STEPS = 5
    }

    // ── Camera / Torch State ──────────────────────────────────────────────────
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxTorchLevel: Int = 1
    private var isTorchOn: Boolean = false
    private var supportsMultiLevel: Boolean = false

    // ── UI References ─────────────────────────────────────────────────────────
    private lateinit var tvHeader: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPowerCircle: ImageButton
    private lateinit var tvLevelReadout: TextView
    private lateinit var tvBrightnessLabel: TextView
    private lateinit var tvInfoBanner: TextView
    private lateinit var viewGlowRing: View
    private lateinit var viewAmbientGlow: View
    private lateinit var switchFlashlight: MaterialSwitch
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var cardTorch: View
    private lateinit var layoutTicks: View
    private lateinit var sliderSection: View

    // ── Switch Color Tints (iOS Style) ────────────────────────────────────────
    private val switchThumbTint = ColorStateList.valueOf(Color.WHITE)
    private val switchTrackTint = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_checked)
        ),
        intArrayOf(
            Color.parseColor("#1AFFFFFF"), // Frosted glass OFF state
            Color.parseColor("#FFF5A623")  // iOS Warm Amber ON state
        )
    )

    // ── Torch Callback ────────────────────────────────────────────────────────
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == cameraId) {
                runOnUiThread {
                    isTorchOn = enabled
                    updateUiState(enabled)

                    // Keep switch checked state in sync silently (avoiding infinite callback)
                    switchFlashlight.setOnCheckedChangeListener(null)
                    switchFlashlight.isChecked = enabled
                    switchFlashlight.setOnCheckedChangeListener(switchListener)
                }
            }
        }

        override fun onTorchStrengthLevelChanged(camId: String, newStrengthLevel: Int) {
            if (camId == cameraId && supportsMultiLevel) {
                runOnUiThread {
                    // Map back from hardware level (1..maxTorchLevel) to UI level (1..5)
                    val uiProgress = mapHardwareLevelToProgress(newStrengthLevel, maxTorchLevel)
                    seekBarBrightness.progress = uiProgress
                    updateLevelReadout(uiProgress)
                }
            }
        }
    }

    private val switchListener = { _: android.widget.CompoundButton, isChecked: Boolean ->
        triggerHapticFeedback()
        if (isChecked) {
            turnOnTorch()
        } else {
            turnOffTorch()
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        initCamera()
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.unregisterTorchCallback(torchCallback)
        if (isTorchOn) {
            turnOffTorch()
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Initialization
    // ════════════════════════════════════════════════════════════════════════════

    private fun bindViews() {
        tvHeader = findViewById(R.id.tvHeader)
        tvStatus = findViewById(R.id.tvStatus)
        btnPowerCircle = findViewById(R.id.btnPowerCircle)
        tvLevelReadout = findViewById(R.id.tvLevelReadout)
        tvBrightnessLabel = findViewById(R.id.tvBrightnessLabel)
        tvInfoBanner = findViewById(R.id.tvInfoBanner)
        viewGlowRing = findViewById(R.id.viewGlowRing)
        viewAmbientGlow = findViewById(R.id.viewAmbientGlow)
        switchFlashlight = findViewById(R.id.switchFlashlight)
        seekBarBrightness = findViewById(R.id.seekBarBrightness)
        cardTorch = findViewById(R.id.cardTorch)
        layoutTicks = findViewById(R.id.layoutTicks)
        sliderSection = findViewById(R.id.sliderSection)

        // Apply custom iOS track/thumb styling to the MaterialSwitch programmatically
        switchFlashlight.thumbTintList = switchThumbTint
        switchFlashlight.trackTintList = switchTrackTint
        switchFlashlight.trackDecorationDrawable = null
    }

    private fun initCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = findCameraWithFlash()

        if (cameraId == null) {
            showFatalError(getString(R.string.label_no_flash))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                maxTorchLevel = characteristics.get(
                    CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
                ) ?: 1
                supportsMultiLevel = maxTorchLevel > 1

                Log.i(TAG, "Hardware max level: $maxTorchLevel. Multi-level support: $supportsMultiLevel")
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to query camera characteristics", e)
                maxTorchLevel = 1
                supportsMultiLevel = false
            }
        } else {
            maxTorchLevel = 1
            supportsMultiLevel = false
        }

        if (supportsMultiLevel) {
            // Slider range is 1..5 for the snap points requested
            seekBarBrightness.max = SLIDER_MAX_STEPS
            seekBarBrightness.progress = 1
            updateLevelReadout(1)
        } else {
            // Hide brightness controls if variable brightness is not supported
            sliderSection.visibility = View.GONE
            tvInfoBanner.text = getString(R.string.label_multi_level_unsupported)
            tvInfoBanner.visibility = View.VISIBLE
        }

        cameraManager.registerTorchCallback(torchCallback, null)
    }

    private fun findCameraWithFlash(): String? {
        return try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (hasFlash == true) return id
            }
            null
        } catch (e: CameraAccessException) {
            null
        }
    }

    private fun setupListeners() {
        switchFlashlight.setOnCheckedChangeListener(switchListener)

        // ── Power Button Touch Physics ───────────────────────────────────────
        btnPowerCircle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Apple touch spring start: scale down to 0.94
                    animateButtonScale(v, 0.94f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Apple touch spring release: scale back and bounce
                    animateButtonScale(v, 1.0f)
                    triggerHapticFeedback()
                    toggleTorch()
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animateButtonScale(v, 1.0f)
                    true
                }
                else -> false
            }
        }

        // ── Custom Discrete Snap Slider ──────────────────────────────────────
        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var lastProgress = 1

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Ensure the progress stays within 1..5 bounds
                val coercedProgress = progress.coerceIn(1, SLIDER_MAX_STEPS)

                if (coercedProgress != lastProgress) {
                    lastProgress = coercedProgress
                    updateLevelReadout(coercedProgress)

                    // Micro haptic click on each snap point
                    triggerHapticFeedback()

                    // Pulse/Bounce visual feedback on the slider track/thumb
                    animateSliderThumbSnap()

                    if (isTorchOn && supportsMultiLevel) {
                        val hardwareLevel = mapProgressToHardwareLevel(coercedProgress, maxTorchLevel)
                        setTorchStrength(hardwareLevel)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun toggleTorch() {
        if (isTorchOn) {
            turnOffTorch()
        } else {
            turnOnTorch()
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Torch Core API Control
    // ════════════════════════════════════════════════════════════════════════════

    private fun turnOnTorch() {
        val id = cameraId ?: return
        try {
            if (supportsMultiLevel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val progress = seekBarBrightness.progress.coerceIn(1, SLIDER_MAX_STEPS)
                val hardwareLevel = mapProgressToHardwareLevel(progress, maxTorchLevel)
                cameraManager.turnOnTorchWithStrengthLevel(id, hardwareLevel)
                Log.i(TAG, "Torch ON at level $hardwareLevel")
            } else {
                cameraManager.setTorchMode(id, true)
                Log.i(TAG, "Torch ON (binary fallback)")
            }
            isTorchOn = true
            updateUiState(true)
        } catch (e: CameraAccessException) {
            handleCameraError(e)
        }
    }

    private fun turnOffTorch() {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, false)
            isTorchOn = false
            updateUiState(false)
            Log.i(TAG, "Torch OFF")
        } catch (e: CameraAccessException) {
            handleCameraError(e)
        }
    }

    private fun setTorchStrength(hardwareLevel: Int) {
        val id = cameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                cameraManager.turnOnTorchWithStrengthLevel(id, hardwareLevel)
            } catch (e: CameraAccessException) {
                handleCameraError(e)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Dynamic Mapping Logic (1..5 Snap points <-> 1..Max Hardware levels)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Maps the slider progress (1..5) to actual hardware strength level (1..maxLevel).
     * Distributes intermediate levels linearly across the 5 points.
     */
    private fun mapProgressToHardwareLevel(progress: Int, maxLevel: Int): Int {
        if (maxLevel <= 1) return 1
        if (progress <= 1) return 1
        if (progress >= SLIDER_MAX_STEPS) return maxLevel

        // Linear interpolation formula: 1 + (maxLevel - 1) * (progress - 1) / (SLIDER_MAX_STEPS - 1)
        val fraction = (progress - 1).toFloat() / (SLIDER_MAX_STEPS - 1).toFloat()
        return 1 + Math.round((maxLevel - 1) * fraction)
    }

    /**
     * Maps the hardware strength level (1..maxLevel) back to the slider progress (1..5).
     * Finds the nearest snap level for visual sync from external torch callbacks.
     */
    private fun mapHardwareLevelToProgress(hardwareLevel: Int, maxLevel: Int): Int {
        if (maxLevel <= 1 || hardwareLevel <= 1) return 1
        if (hardwareLevel >= maxLevel) return SLIDER_MAX_STEPS

        // Solve: progress = 1 + round( fraction * (SLIDER_MAX_STEPS - 1) )
        val fraction = (hardwareLevel - 1).toFloat() / (maxLevel - 1).toFloat()
        return 1 + Math.round(fraction * (SLIDER_MAX_STEPS - 1))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Animations & Haptics (Apple HIG)
    // ════════════════════════════════════════════════════════════════════════════

    private fun triggerHapticFeedback() {
        // Triggers standard clean iOS/Apple-style haptic tap (VIRTUAL_KEY / CONFIRM)
        window.decorView.performHapticFeedback(
            HapticFeedbackConstants.CONFIRM,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun animateButtonScale(view: View, scale: Float) {
        // Apple-style overshoot spring interpolator
        val interpolator = if (scale == 1.0f) OvershootInterpolator(1.8f) else AccelerateDecelerateInterpolator()
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(180)
            .setInterpolator(interpolator)
            .start()
    }

    private fun animateSliderThumbSnap() {
        // Tiny scale bounce feedback on the slider container to feel tactile on tick snaps
        seekBarBrightness.animate()
            .scaleY(1.08f)
            .scaleX(1.01f)
            .setDuration(80)
            .withEndAction {
                seekBarBrightness.animate()
                    .scaleY(1.0f)
                    .scaleX(1.0f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }.start()
    }

    private fun updateUiState(isOn: Boolean) {
        if (isOn) {
            // ── ON State ─────────────────────────────────────────────────────
            tvStatus.text = getString(R.string.label_status_on)
            tvStatus.setTextColor(Color.parseColor("#30D158")) // iOS Active Green

            btnPowerCircle.setBackgroundResource(R.drawable.bg_torch_button_on)
            btnPowerCircle.setColorFilter(Color.BLACK) // Black bolt on white active background

            animateGlowElements(show = true)
            if (supportsMultiLevel) {
                seekBarBrightness.isEnabled = true
            }
        } else {
            // ── OFF State ────────────────────────────────────────────────────
            tvStatus.text = getString(R.string.label_status_off)
            tvStatus.setTextColor(Color.parseColor("#FF453A")) // iOS Warning Red

            btnPowerCircle.setBackgroundResource(R.drawable.bg_torch_button_off)
            btnPowerCircle.setColorFilter(Color.parseColor("#66FFFFFF")) // Muted bolt on glass

            animateGlowElements(show = false)
            seekBarBrightness.isEnabled = false
        }
        notifyWidgetState(isOn)
    }

    private fun animateGlowElements(show: Boolean) {
        val targetAlpha = if (show) 1.0f else 0.0f
        val targetScale = if (show) 1.15f else 0.8f
        val ambientTargetAlpha = if (show) 0.5f else 0.0f // subtle background ambient glow intensity

        // Visibility toggling
        if (show) {
            viewGlowRing.visibility = View.VISIBLE
            viewAmbientGlow.visibility = View.VISIBLE
        }

        val ringAlpha = ObjectAnimator.ofFloat(viewGlowRing, View.ALPHA, targetAlpha)
        val ringScaleX = ObjectAnimator.ofFloat(viewGlowRing, View.SCALE_X, targetScale)
        val ringScaleY = ObjectAnimator.ofFloat(viewGlowRing, View.SCALE_Y, targetScale)
        
        val ambientAlpha = ObjectAnimator.ofFloat(viewAmbientGlow, View.ALPHA, ambientTargetAlpha)

        // Animate card background color to represent active glass warmth
        val cardAlphaAnim = ObjectAnimator.ofFloat(
            cardTorch, 
            "alpha", 
            if (show) 1.0f else 0.95f
        )

        AnimatorSet().apply {
            playTogether(ringAlpha, ringScaleX, ringScaleY, ambientAlpha, cardAlphaAnim)
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!show) {
                        viewGlowRing.visibility = View.INVISIBLE
                        viewAmbientGlow.visibility = View.INVISIBLE
                    }
                }
            })
            start()
        }
    }

    private fun updateLevelReadout(progress: Int) {
        tvLevelReadout.text = "Level $progress / $SLIDER_MAX_STEPS"
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Error Handling
    // ════════════════════════════════════════════════════════════════════════════

    private fun showFatalError(message: String) {
        switchFlashlight.isEnabled = false
        seekBarBrightness.isEnabled = false
        sliderSection.visibility = View.GONE

        tvInfoBanner.text = message
        tvInfoBanner.visibility = View.VISIBLE

        tvStatus.text = getString(R.string.label_status_off)
        tvStatus.setTextColor(Color.parseColor("#FF453A"))
    }

    private fun handleCameraError(exception: CameraAccessException) {
        val message = when (exception.reason) {
            CameraAccessException.CAMERA_IN_USE -> "Camera is in use by another application."
            CameraAccessException.CAMERA_DISCONNECTED -> "Camera has been disconnected."
            CameraAccessException.CAMERA_ERROR -> "A camera hardware error occurred."
            CameraAccessException.MAX_CAMERAS_IN_USE -> "Too many cameras are currently in use."
            else -> getString(R.string.label_error)
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        isTorchOn = false
        switchFlashlight.setOnCheckedChangeListener(null)
        switchFlashlight.isChecked = false
        switchFlashlight.setOnCheckedChangeListener(switchListener)
        updateUiState(false)
    }

    private fun notifyWidgetState(enabled: Boolean) {
        val intent = Intent(TorchWidgetProvider.ACTION_UPDATE_STATE).apply {
            putExtra(TorchWidgetProvider.EXTRA_STATE, enabled)
            putExtra(TorchWidgetProvider.EXTRA_LEVEL, seekBarBrightness.progress)
            `package` = packageName
        }
        sendBroadcast(intent)
    }
}
