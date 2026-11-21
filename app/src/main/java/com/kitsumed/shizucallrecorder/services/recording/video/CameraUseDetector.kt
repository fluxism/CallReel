/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the ShizuVideoCallRecorder fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.services.recording.video

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * Reports when any app starts using a camera. Used as a hint that the call being recorded is a video call, for apps
 * that do not report their video state to Android Telecom.
 *
 * This needs no permission: it only tells that a camera is in use, not by whom, and gives no access to it.
 *
 * @param onCameraInUse Called on the main thread each time a camera becomes unavailable. Also called right after
 *                      [start] when a camera is already in use at that moment.
 */
class CameraUseDetector(context: Context, private val onCameraInUse: () -> Unit) {

    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
    private var isRegistered = false

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            AppLogger.d("Camera '$cameraId' is now in use.")
            onCameraInUse()
        }
    }

    fun start() {
        if (isRegistered) return
        val manager = cameraManager ?: return
        runCatching {
            manager.registerAvailabilityCallback(availabilityCallback, Handler(Looper.getMainLooper()))
            isRegistered = true
        }.onFailure { AppLogger.w("Failed to watch camera availability. Video will not start from the camera.", it) }
    }

    fun stop() {
        if (!isRegistered) return
        isRegistered = false
        runCatching { cameraManager?.unregisterAvailabilityCallback(availabilityCallback) }
    }
}
