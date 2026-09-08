import Foundation

/**
 * Model ID normalizer and capability detector.
 *
 * Strips channel prefixes/suffixes (e.g. `[Antigravity渠道] gemini-3.8-flash-high` -> `gemini-3.8-flash-high`)
 * and matches core model identifiers across prefixes, brackets, and tags to accurately determine
 * vision (multimodal) and reasoning (deep thinking) capabilities.
 */
enum ModelIdNormalizer {

    private static let leadingBracketRegex = try! NSRegularExpression(
        pattern: #"^(\s*(\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|<[^>]*>|\{[^}]*\}))\s*"#
    )
    private static let trailingBracketRegex = try! NSRegularExpression(
        pattern: #"\s*(\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|<[^>]*>|\{[^}]*\})\s*$"#
    )
    private static let leadingLabelRegex = try! NSRegularExpression(
        pattern: #"^([^:/]{1,10}[:：])\s*"#
    )

    static func stripChannelAffixes(_ rawId: String) -> String {
        var s = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
        var changed = true
        while changed {
            let before = s
            let fullRange = NSRange(s.startIndex..<s.endIndex, in: s)
            s = leadingBracketRegex.stringByReplacingMatches(in: s, options: [], range: fullRange, withTemplate: "")
            let r2 = NSRange(s.startIndex..<s.endIndex, in: s)
            s = trailingBracketRegex.stringByReplacingMatches(in: s, options: [], range: r2, withTemplate: "")
            let r3 = NSRange(s.startIndex..<s.endIndex, in: s)
            s = leadingLabelRegex.stringByReplacingMatches(in: s, options: [], range: r3, withTemplate: "")
            s = s.trimmingCharacters(in: .whitespacesAndNewlines)
            changed = (s != before)
        }
        return s.isEmpty ? rawId.trimmingCharacters(in: .whitespacesAndNewlines) : s
    }

    static func containsNormalized(_ modelId: String, target: String) -> Bool {
        let needle = target.lowercased().replacingOccurrences(of: ".", with: "-")
        let raw = modelId.lowercased().replacingOccurrences(of: ".", with: "-")
        if raw.contains(needle) { return true }
        let stripped = stripChannelAffixes(modelId).lowercased().replacingOccurrences(of: ".", with: "-")
        return stripped.contains(needle)
    }

    static func isVisionModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        let disqualifiers = ["-tts", "tts-", "_tts", "speech-to-text", "-embedding", "embedding-", "embed-", "text-to-speech"]
        if disqualifiers.contains(where: { combined.contains($0) }) { return false }

        if combined.contains("gemini") { return true }
        if combined.contains("gpt-4o") || combined.contains("gpt-4-turbo") || combined.contains("gpt-4-vision") { return true }
        if combined.contains("gpt-5") { return true }
        if combined.range(of: #"\b(o1|o3|o4)(-[a-z0-9]+)?\b"#, options: .regularExpression) != nil { return true }

        if combined.contains("claude-3") || combined.contains("claude-4") ||
            combined.contains("sonnet") || combined.contains("opus") { return true }

        if combined.contains("vision") || combined.contains("multimodal") { return true }
        if combined.contains("-vl") || combined.contains("_vl") || combined.contains("vl-") || combined.contains("vl_") { return true }
        if combined.contains("llava") || combined.contains("internvl") || combined.contains("minicpm-v") { return true }
        if combined.contains("qwen") && (combined.contains("vl") || combined.contains("omni")) { return true }
        if combined.contains("ocr") || combined.contains("fuyu") || combined.contains("neva") || combined.contains("vila") { return true }

        return false
    }

    static func isAudioInputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        let disqualifiers = ["-tts", "tts-", "_tts", "text-to-speech", "-embedding", "image-only"]
        if disqualifiers.contains(where: { combined.contains($0) }) { return false }

        let asrPatterns = ["asr", "whisper", "transcrib", "speech-to-text", "speech2text", "stt", "sensevoice"]
        if asrPatterns.contains(where: { combined.contains($0) }) { return true }

        if combined.contains("gemini") && !combined.contains("image") && !combined.contains("imagen") { return true }
        if combined.contains("omni") { return true }
        if combined.contains("gpt-4o") || combined.contains("chatgpt-4o") { return true }

        return false
    }

    static func isVideoInputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        let disqualifiers = ["-tts", "tts-", "_tts", "-embedding", "image-only"]
        if disqualifiers.contains(where: { combined.contains($0) }) { return false }

        if combined.contains("gemini") && !combined.contains("image") && !combined.contains("imagen") { return true }
        if combined.contains("qwen") && (combined.contains("vl") || combined.contains("omni")) { return true }
        if combined.contains("video-llm") || combined.contains("videollama") || combined.contains("video-chat") { return true }

        return false
    }

    static func isPdfInputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        let disqualifiers = ["-tts", "tts-", "_tts", "-embedding"]
        if disqualifiers.contains(where: { combined.contains($0) }) { return false }

        if combined.contains("gemini") { return true }
        if combined.contains("claude") || combined.contains("sonnet") || combined.contains("opus") { return true }
        if combined.contains("gpt-4o") || combined.contains("gpt-5") { return true }
        if combined.range(of: #"\b(o1|o3|o4)(-[a-z0-9]+)?\b"#, options: .regularExpression) != nil { return true }
        if combined.contains("qwen") && combined.contains("vl") { return true }
        if combined.contains("ocr") || combined.contains("document") { return true }

        return false
    }

    static func isImageOutputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        if combined.contains("gemini") && combined.contains("image") { return true }
        if combined.contains("nano banana") { return true }
        if combined.contains("dall-e") || combined.contains("gpt-image") { return true }
        if combined.contains("flux") || combined.contains("midjourney") || combined.contains("stable-diffusion") || combined.contains("sdxl") { return true }
        if combined.contains("kolors") || combined.contains("qwen-image") || combined.contains("z-image") || combined.contains("ernie-image") { return true }
        if combined.contains("bailu-image") { return true }
        if combined.contains("image-gen") || combined.contains("text2image") || combined.contains("t2i") { return true }

        return false
    }

    static func isAudioOutputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        if combined.contains("tts") || combined.contains("cosyvoice") || combined.contains("seed-tts") { return true }
        if combined.contains("text-to-speech") || combined.contains("text2speech") || combined.contains("audio-gen") { return true }
        if combined.contains("moss-ttsd") || combined.contains("voice-output") { return true }
        if combined.contains("omni") { return true }

        return false
    }

    static func isVideoOutputModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        if combined.contains("wan-ai") || combined.contains("wan2") || combined.contains("wan-2") { return true }
        if combined.contains("sora") || combined.contains("kling") || combined.contains("runway") || combined.contains("gen-2") || combined.contains("gen-3") { return true }
        if combined.contains("luma") || combined.contains("pika") || combined.contains("t2v") || combined.contains("i2v") || combined.contains("bailu-video") { return true }
        if combined.contains("cogvideox") { return true }

        return false
    }

    static func isReasoningModel(_ modelId: String, displayName: String? = nil) -> Bool {
        let stripped = stripChannelAffixes(modelId).lowercased()
        let combined = "\(stripped) \(displayName?.lowercased() ?? "")"

        let disqualifiers = ["-tts", "-embedding", "-moderation"]
        if disqualifiers.contains(where: { combined.contains($0) }) { return false }

        if combined.contains("reasoner") || combined.contains("reasoning") || combined.contains("thinking") || combined.contains("thought") { return true }
        if combined.contains("r1") || combined.contains("qwq") { return true }

        if combined.contains("gemini-3") || combined.contains("gemini-2.5") ||
            (combined.contains("gemini") && (combined.contains("pro") || combined.contains("flash") || combined.contains("agent") || combined.contains("high"))) {
            return true
        }

        if combined.contains("gpt-5") || combined.contains("codex") { return true }
        if combined.range(of: #"\b(o1|o3|o4)(-[a-z0-9]+)?\b"#, options: .regularExpression) != nil { return true }

        if combined.contains("deepseek-r1") || combined.contains("deepseek-v4") { return true }

        if combined.contains("claude-3-7") || combined.contains("claude-3.7") ||
            combined.contains("claude-4") || combined.contains("opus-4") { return true }

        return false
    }
}
