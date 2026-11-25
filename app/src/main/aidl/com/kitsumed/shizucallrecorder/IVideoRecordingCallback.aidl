package com.kitsumed.shizucallrecorder;

/**
 * Callback interface for receiving video recording events from the shell-side.
 *
 * Added by the CallReel fork.
 */
interface IVideoRecordingCallback {

    /**
     * Reports that the video recording failed after it was started successfully. The video recording is already
     * stopped and its file is closed when this is received. The audio recording is not affected.
     *
     * @param hasContent True if the video file contains video and is worth keeping.
     */
    oneway void onVideoRecordingFailure(boolean hasContent);
}
