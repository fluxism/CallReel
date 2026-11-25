/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.data.recording

import android.os.Build
import com.kitsumed.shizucallrecorder.data.AppPreferences

/**
 * Tells whether video recording can be used.
 */
object VideoRecordingSupport {

    /**
     * Video needs Android 14+: that is where the shell can mirror the display through DisplayManager, and also where
     * the audio of VoIP calls can be captured.
     */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** True when the device supports video recording and the user enabled it. */
    fun isEnabled(preferences: AppPreferences): Boolean = isSupported && preferences.isVideoRecordingEnabled()
}
