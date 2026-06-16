package com.susking.ephone_s.aidata.testutil

import com.google.common.truth.Truth.assertThat
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import org.junit.Test
import java.io.File

class TtsTestFixturesTest {

    @Test
    fun createStreamingRequest_whenCalled_shouldPreserveTaggedText(): Unit {
        val actualRequest: TtsSynthesisRequest = TtsTestFixtures.createStreamingRequest()

        TtsTestFixtures.assertTaggedTextIsPreserved(actualRequest)
        assertThat(actualRequest.model).isEqualTo(TtsTestFixtures.MODEL_ID)
        assertThat(actualRequest.voiceId).isEqualTo(TtsTestFixtures.VOICE_ID)
        assertThat(actualRequest.isStreaming).isTrue()
        assertThat(actualRequest.description).isEqualTo(TtsTestFixtures.DESCRIPTION)
    }

    @Test
    fun createNonStreamingRequest_whenCalled_shouldDisableStreamingAndKeepTaggedText(): Unit {
        val actualRequest: TtsSynthesisRequest = TtsTestFixtures.createNonStreamingRequest()

        TtsTestFixtures.assertTaggedTextIsPreserved(actualRequest)
        assertThat(actualRequest.isStreaming).isFalse()
    }

    @Test
    fun createSuccessResult_whenCalled_shouldKeepRequestMetadata(): Unit {
        val inputRequest: TtsSynthesisRequest = TtsTestFixtures.createStreamingRequest()
        val inputAudioFile: File = File("voice_messages/tts_fixture.wav")

        val actualResult: TtsSynthesisResult = TtsTestFixtures.createSuccessResult(
            audioFile = inputAudioFile,
            request = inputRequest
        )

        assertThat(actualResult.audioFile).isEqualTo(inputAudioFile)
        assertThat(actualResult.durationMillis).isEqualTo(TtsTestFixtures.DURATION_MILLIS)
        assertThat(actualResult.model).isEqualTo(inputRequest.model)
        assertThat(actualResult.voiceId).isEqualTo(inputRequest.voiceId)
        assertThat(actualResult.isStreaming).isEqualTo(inputRequest.isStreaming)
        assertThat(actualResult.errorMessage).isNull()
    }

    @Test
    fun createFailureResult_whenCalled_shouldKeepErrorAndRequestMetadata(): Unit {
        val inputRequest: TtsSynthesisRequest = TtsTestFixtures.createNonStreamingRequest()

        val actualResult: TtsSynthesisResult = TtsTestFixtures.createFailureResult(
            request = inputRequest,
            errorMessage = ERROR_MESSAGE
        )

        assertThat(actualResult.audioFile).isNull()
        assertThat(actualResult.durationMillis).isNull()
        assertThat(actualResult.model).isEqualTo(inputRequest.model)
        assertThat(actualResult.voiceId).isEqualTo(inputRequest.voiceId)
        assertThat(actualResult.isStreaming).isFalse()
        assertThat(actualResult.errorMessage).isEqualTo(ERROR_MESSAGE)
    }

    @Test
    fun createMinimalWavBytes_whenCalled_shouldBuildPlayableHeader(): Unit {
        val actualBytes: ByteArray = TtsTestFixtures.createMinimalWavBytes()

        TtsTestFixtures.assertPlayableWavHeader(actualBytes)
    }

    private companion object {
        private const val ERROR_MESSAGE: String = "接口返回空音频"
    }
}
