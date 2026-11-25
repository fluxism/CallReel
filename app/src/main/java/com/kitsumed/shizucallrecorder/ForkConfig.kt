/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork.
 */

package com.kitsumed.shizucallrecorder

/**
 * Switches that make CallReel behave differently from the original project where a fork has to.
 */
object ForkConfig {
    /**
     * The original app asks its users, once a year, to sponsor its author, with a screen written in the author's own voice.
     * A fork must not present itself as the original author, so these prompts are off. The original project is still
     * credited and linked in the About section, as its licence requires.
     */
    const val SHOW_UPSTREAM_SPONSOR_PROMPTS = false
}
