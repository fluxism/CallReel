/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.recording

import android.app.Service
import android.net.Uri
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.IRecordingCallback
import com.kitsumed.shizucallrecorder.IShellService
import com.kitsumed.shizucallrecorder.IVideoRecordingCallback
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.data.recording.RecordingFailure
import com.kitsumed.shizucallrecorder.data.recording.RecordingPlan
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.kitsumed.shizucallrecorder.utils.RecordingFileNameFormatter
import com.kitsumed.shizucallrecorder.utils.VideoFileNameFormatter

/**
 * Application-side owner of a single recording session.
 *
 * **Creating an instance of this class will immediately start a recording session (capturing audio) with the remote recording engine.**
 *
 * This class owns:
 *
 * - SAF destination creation
 * - The output ParcelFileDescriptor for the remote recording engine
 * - The resolved recording plan and metadata
 * - The communication with the remote recording engine through IShellService
 *
 * @throws InitializationException when failure happens during the initialization **on the application side** (SAF folder invalid, file creation failed, etc).
 */
class RecordingSession(
    val context: Service,
    remoteService: IShellService,
    val initializationMetadata: EnrichedCallData,
    val plan: RecordingPlan,
    private val listener: SessionListener
) {

    /**
     * Callback interface for receiving recording session events from the recording engine, such as failures.
     */
    interface SessionListener {
        /**
         * Called when the recording session receives a failure from the remote recording engine.
         *
         * **The session should be considered terminated after this callback.**
         */
        fun onSessionFailed(failure: RecordingFailure)
    }

    /**
     * Exception thrown when the recording session cannot be initialized.
     *
     * @property userFriendlyMessage A message suitable for displaying to the user.
     * @property technicalLogMessage A detailed technical message for logging purposes.
     * @property cause The underlying cause of the exception, if any.
     */
    class InitializationException(
        val userFriendlyMessage: String,
        technicalLogMessage: String,
        cause: Throwable? = null
    ) : Exception(technicalLogMessage, cause)


    /**
     * The URI of the current recording file, or null if no recording is active.
     */
    var currentRecordingUri: Uri? = null
        private set // Prevent modification from outside the class.

    // ---- FORK: video recording state

    /** The name of the audio file, sub-folders included. The video file is named after it. */
    private val audioFileName: String

    /** The URI of the current (or last) video file of this session, or null if video was never started. */
    var currentVideoUri: Uri? = null
        private set

    /** True while the remote recording engine is recording video for this session. */
    @Volatile
    var isVideoActive: Boolean = false
        private set

    /** Called (from a binder thread) when the video recording fails after it started. */
    @Volatile
    private var videoFailureListener: ((hasContent: Boolean) -> Unit)? = null

    private val videoCallback = object : IVideoRecordingCallback.Stub() {
        override fun onVideoRecordingFailure(hasContent: Boolean) {
            AppLogger.e("Remote recording engine reported a video recording failure. hasContent=$hasContent")
            if (!isVideoActive) return // Already stopped on our side.
            isVideoActive = false
            if (!hasContent) deleteVideoFile()
            videoFailureListener?.invoke(hasContent)
        }
    }

    /**
     * The ParcelFileDescriptor for the current recording file, or null if no recording is active.
     *
     * This is the file descriptor passed to the remote recording engine.
     */
    private var outputPfd: ParcelFileDescriptor? = null

    /**
     * The remote shell service, or null if the recording session of this specific instance has been released or canceled.
     */
    private var shellService: IShellService? = remoteService

    /**
     * The callback implementation for receiving recording events from the remote recording engine.
     */
    private val recordingCallback = object : IRecordingCallback.Stub() {
        override fun onRecordingFailure(error: RecordingFailure) {
            AppLogger.e("Remote recording engine reported failure with error: $error")
            // Release resources and notify listener about failure
            when (error) {
                RecordingFailure.CAPTURE_IS_SILENT -> {
                    // Delete silent capture
                    cancel(context)
                }
                else -> {
                    // Keep file since it may still contain data
                    release()
                }
            }
            listener.onSessionFailed(error)
        }
    }

    init {
        val folderUri = AppPreferences(context).getRecordingFolderUri()

        if (!SafHelper.isFolderValid(context, folderUri)) {
            throw InitializationException(
                userFriendlyMessage = context.getString(R.string.recording_error_folder_missing),
                technicalLogMessage = "Cannot start recording: selected output folder is missing, invalid, or inaccessible."
            )
        }

        val fileName = RecordingFileNameFormatter.formatFileName(
            context,
            initializationMetadata,
            plan.codec
        )

        audioFileName = fileName

        val safResult = SafHelper.createAudioFile(
            context,
            folderUri,
            fileName,
            plan.codec.mimeType
        ) ?: throw InitializationException(
            userFriendlyMessage = context.getString(R.string.recording_error_file_creation),
            technicalLogMessage = "Failed to create audio file in SAF storage."
        )

        currentRecordingUri = safResult.uri
        outputPfd = safResult.descriptor

        try {
            remoteService.startRecording(outputPfd!!, plan, recordingCallback)
            AppLogger.i("Recording session initialized the remote recording engine successfully.")
        } catch (e: Exception) {
            cancel(context)
            when (e) {
                is DeadObjectException -> throw InitializationException(
                    userFriendlyMessage = context.getString(R.string.recording_error_shell_service_dead ),
                    technicalLogMessage = "Remote recording engine process died (already dead or hard native crash during startRecording execution).",
                    cause = e
                )
                else -> throw InitializationException(
                    userFriendlyMessage = context.getString(R.string.recording_error_binder_call_failed ),
                    technicalLogMessage = "Binder IPC failure (${e.javaClass.simpleName}) while calling startRecording().",
                    cause = e
                )
            }
        }
    }

    /**
     * Pauses the recording session.
     */
    fun pause() {
        shellService?.pauseRecording()
    }

    /**
     * Resumes the recording session.
     */
    fun resume() {
        shellService?.resumeRecording()
    }

    /**
     * Releases the recording session, stopping the recording and closing resources.
     *
     * **This method should be called when the recording session is no longer needed.**
     */
    fun release() {
        // FORK: stop the video first, so we learn whether its file is worth keeping.
        stopVideo()

        runCatching {
            shellService?.stopRecording()
        }

        /*
         * The ShellRecordingEngine closes the FD after finalization.
         * Closing again here is safe as a fallback.
         */
        runCatching {
            outputPfd?.close()
        }

        shellService = null
        outputPfd = null
    }

    /**
     * FORK: Starts recording the screen into an MP4 file next to the audio file of this session.
     *
     * The video never affects the audio recording: when it cannot start, this returns false and nothing else changes.
     *
     * @param onFailure Called from a binder thread if the video recording fails later. The argument tells whether the file was kept.
     * @return true if the video recording is running (also when it already was).
     */
    fun startVideo(onFailure: (hasContent: Boolean) -> Unit): Boolean {
        val service = shellService ?: return false
        if (isVideoActive) return true

        val folderUri = AppPreferences(context).getRecordingFolderUri()
        if (!SafHelper.isFolderValid(context, folderUri)) {
            AppLogger.e("Cannot start video recording: the output folder is missing, invalid, or inaccessible.")
            return false
        }

        val videoFileName = VideoFileNameFormatter.fromAudioFileName(audioFileName, plan.codec.containerExtension)
        val safResult = SafHelper.createAudioFile(context, folderUri, videoFileName, VideoFileNameFormatter.MIME_TYPE)
        if (safResult == null) {
            AppLogger.e("Cannot start video recording: failed to create the video file in SAF storage.")
            return false
        }
        currentVideoUri = safResult.uri
        videoFailureListener = onFailure

        val started = try {
            service.startVideoRecording(safResult.descriptor, videoCallback)
        } catch (e: Exception) {
            AppLogger.e("Binder IPC failure (${e.javaClass.simpleName}) while calling startVideoRecording().", e)
            false
        } finally {
            // The shell process received its own duplicate of the descriptor, ours is no longer needed.
            runCatching { safResult.descriptor.close() }
        }

        if (!started) {
            AppLogger.e("The remote recording engine could not start the video recording.")
            deleteVideoFile()
            return false
        }

        isVideoActive = true
        AppLogger.i("Video recording started for this session.")
        return true
    }

    /**
     * FORK: Stops the video recording of this session, if any. The audio recording continues.
     * A video file without any video in it is deleted.
     */
    fun stopVideo() {
        if (!isVideoActive) return
        isVideoActive = false

        val hasContent = try {
            shellService?.stopVideoRecording() == true
        } catch (e: Exception) {
            // We cannot know, so keep the file rather than risk deleting a good one.
            AppLogger.w("Binder IPC failure (${e.javaClass.simpleName}) while calling stopVideoRecording(). Keeping the video file.", e)
            true
        }

        AppLogger.i("Video recording stopped for this session. hasContent=$hasContent")
        if (!hasContent) deleteVideoFile()
    }

    private fun deleteVideoFile() {
        val uri = currentVideoUri ?: return
        currentVideoUri = null
        AppLogger.i("Deleting the empty video file.")
        runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() }.onFailure {
            AppLogger.w("Failed to delete the empty video file.", it)
        }
    }

    /**
     * Releases the recording session, stopping the recording and DELETING the output file.
     *
     * **This method should be called when the output file should not be kept. When no data are expected.**
     */
    fun cancel(context: Service) {
        release()
        AppLogger.i("Recording session canceled. Deleting incomplete recording file.")
        currentRecordingUri?.let { uri ->
            try {
                DocumentFile.fromSingleUri(context, uri)?.delete()
            } catch (e: Exception) {
                AppLogger.w("Failed to delete incomplete recording file.", e)
            }
        }
    }
}