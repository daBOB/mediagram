package com.mediagram.controller

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** One feel per game-night moment, so a player knows what happened without looking. */
class Buzzer(
    context: Context,
) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun buzz(kind: String) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        // off, on, off, on … in milliseconds, starting with no delay
        val pattern =
            when (kind) {
                "accepted" -> longArrayOf(0, 30)
                "turn" -> longArrayOf(0, 60, 80, 60)
                "wrong" -> longArrayOf(0, 300)
                "won" -> longArrayOf(0, 80, 60, 80, 60, 250)
                else -> return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }
}
