package com.openminis.app.data.model

/**
 * Model ID normalizer and capability detector.
 *
 * Strips channel prefixes/suffixes (e.g. `[Antigravity渠道] gemini-3.8-flash-high` -> `gemini-3.8-flash-high`)
 * and matches core model identifiers across prefixes, brackets, and tags to accurately determine
 * vision (multimodal) and reasoning (deep thinking) capabilities.
 */
object ModelIdNormalizer {

    // Matches leading bracket groups: [...], 【...】, (...), （...）, <...>, {...}
    private val LEADING_BRACKET_REGEX = Regex("""^(\s*(\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|<[^>]*>|\{[^}]*\}))\s*""")
    // Matches trailing bracket groups
    private val TRAILING_BRACKET_REGEX = Regex("""\s*(\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|<[^>]*>|\{[^}]*\})\s*$""")
    // Matches leading channel/route labels like "渠道: ", "channel: ", "转发: "
    private val LEADING_LABEL_REGEX = Regex("""^([^:/]{1,10}[:：])\s*""")

    /**
     * Strips channel affixes (e.g. `[Antigravity渠道] gemini-3.8-flash-high` -> `gemini-3.8-flash-high`).
     */
    fun stripChannelAffixes(rawId: String): String {
        var s = rawId.trim()
        var changed = true
        while (changed) {
            val before = s
            s = LEADING_BRACKET_REGEX.replace(s, "")
            s = TRAILING_BRACKET_REGEX.replace(s, "")
            s = LEADING_LABEL_REGEX.replace(s, "")
            s = s.trim()
            changed = s != before
        }
        return s.ifEmpty { rawId.trim() }
    }

    /**
     * Checks if the model ID (ignoring channel names and brackets) contains [target],
     * normalizing dots and hyphens.
     */
    fun containsNormalized(modelId: String, target: String): Boolean {
        val needle = target.lowercase().replace('.', '-')
        val raw = modelId.lowercase().replace('.', '-')
        if (raw.contains(needle)) return true
        val stripped = stripChannelAffixes(modelId).lowercase().replace('.', '-')
        return stripped.contains(needle)
    }

    /**
     * Determines whether this model is known to natively consume images (vision / multimodal).
     *
     * Matches keywords regardless of channel prefixes (e.g. `[Antigravity渠道] gemini-3.8-flash-high`,
     * `[Antigravity渠道] gemini-pro-agent`, `【中转】gpt-4o`, `qwen2.5-vl-72b`).
     */
    fun isVisionModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        // Disqualifiers: audio/TTS/embedding dedicated models
        val disqualifiers = listOf("-tts", "tts-", "_tts", "speech-to-text", "-embedding", "embedding-", "embed-", "text-to-speech")
        if (disqualifiers.any { combined.contains(it) }) return false

        // Gemini: all modern Gemini models are multimodal vision models
        if (combined.contains("gemini")) return true

        // OpenAI: GPT-4o, GPT-4 Turbo, GPT-5, o1, o3, o4
        if (combined.contains("gpt-4o") || combined.contains("gpt-4-turbo") || combined.contains("gpt-4-vision")) return true
        if (combined.contains("gpt-5")) return true
        if (Regex("""\b(o1|o3|o4)(-[a-z0-9]+)?\b""").containsMatchIn(combined)) return true

        // Claude: 3, 3.5, 3.7, 4, Sonnet, Opus
        if (combined.contains("claude-3") || combined.contains("claude-4") ||
            combined.contains("sonnet") || combined.contains("opus")) return true

        // General vision/multimodal keywords
        if (combined.contains("vision") || combined.contains("multimodal")) return true
        if (combined.contains("-vl") || combined.contains("_vl") || combined.contains("vl-") || combined.contains("vl_")) return true
        if (combined.contains("llava") || combined.contains("internvl") || combined.contains("minicpm-v")) return true
        if (combined.contains("qwen") && (combined.contains("vl") || combined.contains("omni"))) return true
        if (combined.contains("ocr") || combined.contains("fuyu") || combined.contains("neva") || combined.contains("vila")) return true

        return false
    }

    /**
     * Determines whether this model natively supports audio input (ASR or speech/omni model).
     */
    fun isAudioInputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        val disqualifiers = listOf("-tts", "tts-", "_tts", "text-to-speech", "-embedding", "image-only")
        if (disqualifiers.any { combined.contains(it) }) return false

        // Dedicated ASR
        val asrPatterns = listOf("asr", "whisper", "transcrib", "speech-to-text", "speech2text", "stt", "sensevoice")
        if (asrPatterns.any { combined.contains(it) }) return true

        // Gemini: all modern Gemini models natively accept audio inputs (except pure image generation)
        if (combined.contains("gemini") && !combined.contains("image") && !combined.contains("imagen")) return true

        // Omni models (Qwen-Omni, GPT-4o, Nemotron-Omni)
        if (combined.contains("omni")) return true
        if (combined.contains("gpt-4o") || combined.contains("chatgpt-4o")) return true

        return false
    }

    /**
     * Determines whether this model natively supports video input.
     */
    fun isVideoInputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        val disqualifiers = listOf("-tts", "tts-", "_tts", "-embedding", "image-only")
        if (disqualifiers.any { combined.contains(it) }) return false

        // Gemini: all modern Gemini models natively accept video inputs
        if (combined.contains("gemini") && !combined.contains("image") && !combined.contains("imagen")) return true

        // Qwen-VL & Qwen-Omni support native video frames
        if (combined.contains("qwen") && (combined.contains("vl") || combined.contains("omni"))) return true

        // Dedicated video understanding
        if (combined.contains("video-llm") || combined.contains("videollama") || combined.contains("video-chat")) return true

        return false
    }

    /**
     * Determines whether this model natively supports PDF document input.
     */
    fun isPdfInputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        val disqualifiers = listOf("-tts", "tts-", "_tts", "-embedding")
        if (disqualifiers.any { combined.contains(it) }) return false

        // Gemini: full PDF parsing and document understanding
        if (combined.contains("gemini")) return true

        // Claude: multi-page PDF processing with visual tables/charts
        if (combined.contains("claude") || combined.contains("sonnet") || combined.contains("opus")) return true

        // OpenAI: GPT-4o, GPT-5, o1, o3, o4
        if (combined.contains("gpt-4o") || combined.contains("gpt-5")) return true
        if (Regex("""\b(o1|o3|o4)(-[a-z0-9]+)?\b""").containsMatchIn(combined)) return true

        // Qwen-VL, OCR models
        if (combined.contains("qwen") && combined.contains("vl")) return true
        if (combined.contains("ocr") || combined.contains("document")) return true

        return false
    }

    /**
     * Determines whether this model outputs generated images.
     */
    fun isImageOutputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        if (combined.contains("gemini") && combined.contains("image")) return true
        if (combined.contains("nano banana")) return true
        if (combined.contains("dall-e") || combined.contains("gpt-image")) return true
        if (combined.contains("flux") || combined.contains("midjourney") || combined.contains("stable-diffusion") || combined.contains("sdxl")) return true
        if (combined.contains("kolors") || combined.contains("qwen-image") || combined.contains("z-image") || combined.contains("ernie-image")) return true
        if (combined.contains("bailu-image")) return true
        if (combined.contains("image-gen") || combined.contains("text2image") || combined.contains("t2i")) return true

        return false
    }

    /**
     * Determines whether this model outputs generated audio (TTS / speech generation).
     */
    fun isAudioOutputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        if (combined.contains("tts") || combined.contains("cosyvoice") || combined.contains("seed-tts")) return true
        if (combined.contains("text-to-speech") || combined.contains("text2speech") || combined.contains("audio-gen")) return true
        if (combined.contains("moss-ttsd") || combined.contains("voice-output")) return true
        if (combined.contains("omni")) return true

        return false
    }

    /**
     * Determines whether this model outputs generated video.
     */
    fun isVideoOutputModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        if (combined.contains("wan-ai") || combined.contains("wan2") || combined.contains("wan-2")) return true
        if (combined.contains("sora") || combined.contains("kling") || combined.contains("runway") || combined.contains("gen-2") || combined.contains("gen-3")) return true
        if (combined.contains("luma") || combined.contains("pika") || combined.contains("t2v") || combined.contains("i2v") || combined.contains("bailu-video")) return true
        if (combined.contains("cogvideox")) return true

        return false
    }

    /**
     * Determines whether this model is known to support reasoning / deep thinking.
     *
     * Matches keywords regardless of channel prefixes (e.g. `[Antigravity渠道] gemini-3.8-flash-high`,
     * `[Antigravity渠道] gemini-pro-agent`, `deepseek-r1`, `o3-mini`).
     */
    fun isReasoningModel(modelId: String, displayName: String? = null): Boolean {
        val stripped = stripChannelAffixes(modelId).lowercase()
        val combined = "$stripped ${displayName?.lowercase().orEmpty()}"

        // Disqualifiers: non-reasoning dedicated models
        val disqualifiers = listOf("-tts", "-embedding", "-moderation")
        if (disqualifiers.any { combined.contains(it) }) return false

        // Explicit reasoning indicators
        if (combined.contains("reasoner") || combined.contains("reasoning") || combined.contains("thinking") || combined.contains("thought")) return true
        if (combined.contains("r1") || combined.contains("qwq")) return true

        // Gemini: Gemini 3.x, Gemini 2.5 Pro/Flash, and models ending with -high, -agent, etc.
        if (combined.contains("gemini-3") || combined.contains("gemini-2.5") ||
            (combined.contains("gemini") && (combined.contains("pro") || combined.contains("flash") || combined.contains("agent") || combined.contains("high")))) {
            return true
        }

        // OpenAI: o1, o3, o4, gpt-5, codex
        if (combined.contains("gpt-5") || combined.contains("codex")) return true
        if (Regex("""\b(o1|o3|o4)(-[a-z0-9]+)?\b""").containsMatchIn(combined)) return true

        // DeepSeek: V4, R1
        if (combined.contains("deepseek-r1") || combined.contains("deepseek-v4")) return true

        // Claude: Claude 3.7, Claude 4, Opus 4
        if (combined.contains("claude-3-7") || combined.contains("claude-3.7") ||
            combined.contains("claude-4") || combined.contains("opus-4")) return true

        return false
    }
}
