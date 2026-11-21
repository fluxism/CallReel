package com.kitsumed.shizucallrecorder.services.recording.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTriggerPolicyTest {

    private fun decide(
        trigger: VideoTrigger,
        isFeatureEnabled: Boolean = true,
        isVideoRecording: Boolean = false,
        wasStoppedByUser: Boolean = false,
        isAppOrVoipCall: Boolean = true,
    ) = VideoTriggerPolicy.shouldStartVideo(trigger, isFeatureEnabled, isVideoRecording, wasStoppedByUser, isAppOrVoipCall)

    @Test
    fun nothingStartsVideoWhenTheFeatureIsOff() {
        VideoTrigger.entries.forEach { assertFalse(it.name, decide(it, isFeatureEnabled = false)) }
    }

    @Test
    fun nothingStartsVideoWhenItIsAlreadyRecording() {
        VideoTrigger.entries.forEach { assertFalse(it.name, decide(it, isVideoRecording = true)) }
    }

    @Test
    fun everyTriggerStartsVideoOnAnAppCall() {
        VideoTrigger.entries.forEach { assertTrue(it.name, decide(it)) }
    }

    @Test
    fun manualTriggerWorksAfterTheUserStoppedVideo() {
        assertTrue(decide(VideoTrigger.MANUAL, wasStoppedByUser = true))
    }

    @Test
    fun automaticTriggersRespectAManualStop() {
        assertFalse(decide(VideoTrigger.TELECOM, wasStoppedByUser = true))
        assertFalse(decide(VideoTrigger.CAMERA, wasStoppedByUser = true))
    }

    @Test
    fun cameraOnlyCountsForAppOrVoipCalls() {
        assertFalse(decide(VideoTrigger.CAMERA, isAppOrVoipCall = false))
        assertTrue(decide(VideoTrigger.TELECOM, isAppOrVoipCall = false))
        assertTrue(decide(VideoTrigger.MANUAL, isAppOrVoipCall = false))
    }

    @Test
    fun voipAudioMakesAnAppOrVoipCall() {
        assertTrue(VideoTriggerPolicy.isAppOrVoipCall(true, null, emptyList()))
        assertTrue(VideoTriggerPolicy.isAppOrVoipCall(true, "com.android.phone", listOf("com.google.android.dialer")))
    }

    @Test
    fun thirdPartyPackageMakesAnAppOrVoipCall() {
        assertTrue(VideoTriggerPolicy.isAppOrVoipCall(false, "com.whatsapp", listOf("com.google.android.dialer", null)))
    }

    @Test
    fun carrierCallsAreNotAppOrVoipCalls() {
        val dialers = listOf("com.google.android.dialer", "com.example.dialer")
        assertFalse(VideoTriggerPolicy.isAppOrVoipCall(false, "com.android.phone", dialers))
        assertFalse(VideoTriggerPolicy.isAppOrVoipCall(false, "com.google.android.dialer", dialers))
        assertFalse(VideoTriggerPolicy.isAppOrVoipCall(false, "com.example.dialer", dialers))
        assertFalse(VideoTriggerPolicy.isAppOrVoipCall(false, null, dialers))
        assertFalse(VideoTriggerPolicy.isAppOrVoipCall(false, "", dialers))
    }
}
