package com.openminis.app.data

import com.openminis.app.data.model.ModelIdNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIdNormalizerTest {

    @Test
    fun stripChannelAffixes_removesBracketedPrefixes() {
        assertEquals(
            "gemini-3.8-flash-high",
            ModelIdNormalizer.stripChannelAffixes("[Antigravity渠道] gemini-3.8-flash-high"),
        )
        assertEquals(
            "gemini-pro-agent",
            ModelIdNormalizer.stripChannelAffixes("[Antigravity渠道] gemini-pro-agent"),
        )
        assertEquals(
            "gpt-4o",
            ModelIdNormalizer.stripChannelAffixes("【VIP高速】gpt-4o"),
        )
        assertEquals(
            "claude-3-7-sonnet",
            ModelIdNormalizer.stripChannelAffixes("(主力渠道) claude-3-7-sonnet"),
        )
        assertEquals(
            "o3-mini",
            ModelIdNormalizer.stripChannelAffixes("[渠道A] [高速] o3-mini"),
        )
    }

    @Test
    fun isVisionModel_matchesPrefixedGeminiAndGpt() {
        assertTrue(ModelIdNormalizer.isVisionModel("[Antigravity渠道] gemini-3.8-flash-high"))
        assertTrue(ModelIdNormalizer.isVisionModel("[Antigravity渠道] gemini-pro-agent"))
        assertTrue(ModelIdNormalizer.isVisionModel("【测试】gpt-4o-mini"))
        assertTrue(ModelIdNormalizer.isVisionModel("claude-3-5-sonnet-20241022"))
        assertTrue(ModelIdNormalizer.isVisionModel("qwen2.5-vl-72b"))
        // TTS should not be vision
        assertFalse(ModelIdNormalizer.isVisionModel("[Antigravity渠道] gemini-2.0-tts"))
    }

    @Test
    fun isReasoningModel_matchesPrefixedReasoningModels() {
        assertTrue(ModelIdNormalizer.isReasoningModel("[Antigravity渠道] gemini-3.8-flash-high"))
        assertTrue(ModelIdNormalizer.isReasoningModel("[Antigravity渠道] gemini-pro-agent"))
        assertTrue(ModelIdNormalizer.isReasoningModel("【中转】deepseek-r1"))
        assertTrue(ModelIdNormalizer.isReasoningModel("o3-mini-2025-01-31"))
        assertTrue(ModelIdNormalizer.isReasoningModel("claude-3-7-sonnet"))
    }
}
