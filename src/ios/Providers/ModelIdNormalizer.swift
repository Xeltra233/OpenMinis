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
