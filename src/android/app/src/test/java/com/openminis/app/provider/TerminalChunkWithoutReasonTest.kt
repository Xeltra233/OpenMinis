package com.openminis.app.provider

import com.openminis.app.data.model.LLMMessage
import com.openminis.app.data.model.LLMModel
import com.openminis.app.data.model.LLMStreamChunk
import com.openminis.app.data.model.ThinkingLevel
import com.openminis.app.provider.anthropic.AnthropicProvider
import com.openminis.app.provider.openai.OpenAIProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 回归：AI 正常回复完毕后，界面却弹出"连接中断，此回复可能不完整。点击重试以继续。"
 *
 * ChatViewModel.runAgentLoop 把 `turnFinishReason == null && 有可见内容`
 * 当作断流（[T-android-silent-stream-drop]）。因此任何"干净结束、但终止信号
 * 为 null"的路径，都会把一条完整回复误报成中断。两类确定性误报：
 *
 *  1. Chat Completions 以 `data: [DONE]` 收尾，但中继没有转发携带
 *     finish_reason 的空 delta 收尾块 → 旧代码 emit Finished(null)；
 *  2. Anthropic 的 usage-only message_delta（没有 stop_reason）→ 旧代码
 *     emit Finished(null)；而协议真正的终止事件 message_stop 被完全忽略。
 *
 * `[DONE]` 与 `message_stop` 都是"干净结束"的哨兵，必须落到具体
 * stopReason；既没有哨兵、也没有 stop_reason 的流仍是截断
 * （见 StreamDropNoFinishTest / ResponsesApiFinishedTest，那些 null 是
 * 断流检测所依赖的信号，不能被这次修复吞掉）。
 */
class TerminalChunkWithoutReasonTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun openAiChunks(body: String, useResponsesAPI: Boolean = false): List<LLMStreamChunk> {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
        val provider = OpenAIProvider(
            apiKey = "test-key",
            model = LLMModel.gpt4oMini,
            basePath = server.url("/v1").toString().trimEnd('/'),
            useResponsesAPI = useResponsesAPI,
        )
        return runBlocking {
            provider.streamMessage(
                messages = listOf(LLMMessage(LLMMessage.Role.USER, "hi")),
                systemPrompt = null,
                maxTokens = 256,
                temperature = null,
                imageParts = emptyList(),
                tools = emptyList(),
                thinkingLevel = ThinkingLevel.OFF,
            ).toList()
        }
    }

    private fun anthropicChunks(body: String): List<LLMStreamChunk> {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
        val provider = AnthropicProvider(
            apiKey = "test-key",
            model = LLMModel.claudeHaiku45,
            basePath = server.url("/").toString().trimEnd('/'),
        )
        return runBlocking {
            provider.streamMessage(
                messages = listOf(LLMMessage(LLMMessage.Role.USER, "hi")),
                systemPrompt = null,
                maxTokens = 1024,
            ).toList()
        }
    }

    /** Joins SSE event lines the way a real stream delimits them. */
    private fun sse(vararg lines: String): String = lines.joinToString("\n\n") + "\n"

    // ── Chat Completions: [DONE] 没有前置 finish_reason ───────────────────

    @Test
    fun `Chat Completions DONE without finish_reason reports a clean stop`() {
        // 中继把携带 finish_reason 的空 delta 收尾块丢掉了，只留下内容和 [DONE]。
        val body = sse(
            """data: {"choices":[{"delta":{"content":"完整回复"},"index":0}]}""",
            """data: [DONE]""",
        )

        val chunks = openAiChunks(body)
        val text = chunks.filterIsInstance<LLMStreamChunk.Text>().joinToString("") { it.text }
        assertEquals("完整回复", text)

        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals(
            "[DONE] is a clean-termination sentinel — a null here is what produced " +
                "the bogus '连接中断…可能不完整' banner",
            "stop",
            finished.first().stopReason,
        )
    }

    @Test
    fun `Chat Completions DONE after tool calls without finish_reason reports tool_calls`() {
        val body = sse(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"shell_execute","arguments":"{}"}}]},"index":0}]}""",
            """data: [DONE]""",
        )

        val chunks = openAiChunks(body)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals("tool_calls", finished.first().stopReason)
    }

    // ── Responses API: [DONE] 没有 response.completed ──────────────────────

    @Test
    fun `Responses DONE without response completed still reports a clean stop`() {
        val body = sse(
            """data: {"type":"response.output_text.delta","delta":"complete reply"}""",
            """data: [DONE]""",
        )

        val chunks = openAiChunks(body, useResponsesAPI = true)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals("stop", finished.first().stopReason)
    }

    @Test
    fun `Responses DONE after function_call without response completed reports tool_use`() {
        val body = sse(
            """data: {"type":"response.output_item.added","item":{"type":"function_call","id":"fc_1","call_id":"c1","name":"shell_execute"}}""",
            """data: {"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{}"}""",
            """data: {"type":"response.output_item.done","item":{"type":"function_call","id":"fc_1","call_id":"c1","name":"shell_execute","arguments":"{}"}}""",
            """data: [DONE]""",
        )

        val chunks = openAiChunks(body, useResponsesAPI = true)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals("tool_use", finished.first().stopReason)
    }

    // ── Anthropic: message_delta 没有 stop_reason / message_stop ──────────

    @Test
    fun `Anthropic message_stop without stop_reason closes the turn cleanly`() {
        val body = sse(
            """data: {"type":"message_start","message":{"usage":{"input_tokens":5,"output_tokens":0}}}""",
            """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"完整回复"}}""",
            """data: {"type":"message_delta","usage":{"output_tokens":7}}""",
            """data: {"type":"message_stop"}""",
        )

        val chunks = anthropicChunks(body)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals(
            "message_stop is the protocol's clean-termination sentinel — a null here is " +
                "what produced the bogus '连接中断…可能不完整' banner",
            "end_turn",
            finished.first().stopReason,
        )
    }

    @Test
    fun `Anthropic usage-only message_delta does not emit a null terminal chunk`() {
        val body = sse(
            """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"完整回复"}}""",
            """data: {"type":"message_delta","usage":{"output_tokens":7}}""",
            """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}""",
            """data: {"type":"message_stop"}""",
        )

        val chunks = anthropicChunks(body)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals("end_turn", finished.first().stopReason)
    }

    @Test
    fun `Anthropic tool turn with message_stop without stop_reason reports tool_use`() {
        val body = sse(
            """data: {"type":"content_block_start","content_block":{"type":"tool_use","id":"toolu_1","name":"shell_execute"}}""",
            """data: {"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{}"}}""",
            """data: {"type":"content_block_stop"}""",
            """data: {"type":"message_delta","usage":{"output_tokens":9}}""",
            """data: {"type":"message_stop"}""",
        )

        val chunks = anthropicChunks(body)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>()
        assertEquals("expected exactly one Finished chunk, got ${finished.size}", 1, finished.size)
        assertEquals("tool_use", finished.first().stopReason)
    }

    @Test
    fun `Anthropic stream without message_stop keeps reporting truncation`() {
        // 内容到达后连接结束，既没有 message_stop 也没有 stop_reason ——
        // 这条流在协议层面无法证明"生成已完成"，必须保持 null，
        // 断流检测（[T-android-silent-stream-drop]）依赖它。
        val body = sse(
            """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"partial reply"}}""",
            """data: {"type":"message_delta","usage":{"output_tokens":3}}""",
        )

        val chunks = anthropicChunks(body)
        val finished = chunks.filterIsInstance<LLMStreamChunk.Finished>().firstOrNull()
        assertNull(
            "a truncated stream must NOT report a finish reason — that null is the signal",
            finished?.stopReason,
        )
    }
}
