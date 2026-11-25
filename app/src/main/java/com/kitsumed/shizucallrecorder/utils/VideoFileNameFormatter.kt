/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.utils

/**
 * Derives the name of the video file from the name of the audio file of the same call, so both sort together.
 */
object VideoFileNameFormatter {
    const val EXTENSION = ".mp4"
    const val MIME_TYPE = "video/mp4"

    /**
     * @param audioFileName The audio file name as produced by [RecordingFileNameFormatter], sub-folders included.
     * @param audioExtension The extension of the audio container, with its dot (e.g. ".ogg").
     * @return The same name with the audio extension replaced by [EXTENSION].
     */
    fun fromAudioFileName(audioFileName: String, audioExtension: String): String {
        val baseName = if (audioExtension.isNotEmpty() && audioFileName.endsWith(audioExtension)) {
            audioFileName.dropLast(audioExtension.length)
        } else {
            audioFileName
        }
        return baseName + EXTENSION
    }
}
