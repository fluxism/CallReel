/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.services.recording.video

/**
 * What asked for the video recording to start.
 */
enum class VideoTrigger {
    /** The user tapped "Record video". */
    MANUAL,
    /** Android Telecom reported the call as a video call. */
    TELECOM,
    /** A camera was opened while the call was being recorded. */
    CAMERA,
}

/**
 * Decides whether a [VideoTrigger] starts the video recording. Pure logic, no Android dependencies.
 */
object VideoTriggerPolicy {

    /**
     * @param trigger What asked for video.
     * @param isFeatureEnabled Whether video recording is enabled in the settings and supported on this device.
     * @param isVideoRecording Whether a video recording is already running for this call.
     * @param wasStoppedByUser Whether the user stopped the video by hand earlier in this call. Automatic triggers respect that.
     * @param isAppOrVoipCall Whether the call comes from a third-party app or uses the VoIP audio mode.
     */
    fun shouldStartVideo(
        trigger: VideoTrigger,
        isFeatureEnabled: Boolean,
        isVideoRecording: Boolean,
        wasStoppedByUser: Boolean,
        isAppOrVoipCall: Boolean,
    ): Boolean {
        if (!isFeatureEnabled || isVideoRecording) return false

        return when (trigger) {
            VideoTrigger.MANUAL -> true
            VideoTrigger.TELECOM -> !wasStoppedByUser
            // A camera can be opened for other reasons during a carrier call (for example face unlock), and carrier
            // video calls are reported by Telecom anyway. So the camera only counts for app and VoIP calls.
            VideoTrigger.CAMERA -> !wasStoppedByUser && isAppOrVoipCall
        }
    }

    /**
     * Tells whether a call comes from a third-party app or is routed as VoIP audio.
     *
     * @param isCallAudioVoIpBased Whether the call audio uses the VoIP audio mode.
     * @param callPackageName The package that owns the call, or null when the detection method cannot tell.
     * @param dialerPackageNames The packages that handle carrier calls on this device (system and default dialers). Nulls are ignored.
     */
    fun isAppOrVoipCall(isCallAudioVoIpBased: Boolean, callPackageName: String?, dialerPackageNames: List<String?>): Boolean {
        if (isCallAudioVoIpBased) return true
        if (callPackageName.isNullOrBlank()) return false
        return callPackageName != TELEPHONY_PACKAGE && callPackageName !in dialerPackageNames.filterNotNull()
    }

    /** The package of the Android telephony stack, which owns carrier calls. */
    private const val TELEPHONY_PACKAGE = "com.android.phone"
}
