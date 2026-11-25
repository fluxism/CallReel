/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.services.shell.recording.video

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display
import android.view.Surface
import java.lang.reflect.InvocationTargetException

/**
 * Mirrors the main display into a [Surface], using hidden framework APIs available to the shell user.
 *
 * Inspired by the scrcpy DisplayManager wrapper:
 * https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayManager.java
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
object DisplayMirror {

    data class DisplaySize(val width: Int, val height: Int)

    /**
     * Returns the current logical size of the main display (it follows the rotation).
     */
    fun getMainDisplaySize(): DisplaySize = unwrap {
        val globalClass = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val global = globalClass.getDeclaredMethod("getInstance").invoke(null)
        val info = globalClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(global, Display.DEFAULT_DISPLAY)
            ?: throw IllegalStateException("The main display information is unavailable.")

        val infoClass = info.javaClass
        DisplaySize(
            width = infoClass.getField("logicalWidth").getInt(info),
            height = infoClass.getField("logicalHeight").getInt(info),
        )
    }

    /**
     * Creates a virtual display that mirrors the main display into [surface].
     *
     * Uses the hidden static `DisplayManager.createVirtualDisplay(String, int, int, int displayIdToMirror, Surface)`,
     * which exists since Android 14. It was added for the shell and does not need a MediaProjection.
     * Android 14: https://cs.android.com/android/platform/superproject/+/android14-release:frameworks/base/core/java/android/hardware/display/DisplayManager.java
     *
     * @throws NoSuchMethodException on Android versions without that method.
     */
    fun create(name: String, width: Int, height: Int, surface: Surface): VirtualDisplay = unwrap {
        val method = DisplayManager::class.java.getMethod(
            "createVirtualDisplay",
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Surface::class.java,
        )
        method.invoke(null, name, width, height, Display.DEFAULT_DISPLAY, surface) as VirtualDisplay
    }

    /** Runs a reflective call and rethrows the real cause instead of the reflection wrapper. */
    private inline fun <T> unwrap(block: () -> T): T {
        try {
            return block()
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
