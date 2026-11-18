package com.marketplace.backend.service.ai;

import java.util.List;
import java.util.Map;

/**
 * A conversation in which the model may call tools ("function calling"), independent of the provider:
 * OpenAiProvider and ClaudeProvider translate it to their own API format.
 *
 *   the app sends:  system prompt + messages + the tools it offers
 *   the model says: an answer (text), or "call tool X with these arguments" (calls)
 *   the app runs the tools, adds their results (ToolResult) and asks again, until the model answers
 */
public final class Agent {

    private Agent() {
    }

    /** schema: JSON Schema of the arguments ({"type":"object","properties":{...},"required":[...]}). */
    public record Tool(String name, String description, Map<String, Object> schema) {}

    public record Call(String id, String name, Map<String, Object> args) {}

    public sealed interface Msg permits User, Assistant, ToolResult {}

    public record User(String text) implements Msg {}

    /** What the model said: text and/or tool calls. */
    public record Assistant(String text, List<Call> calls) implements Msg {}

    /** content: the tool's result, usually JSON. */
    public record ToolResult(String callId, String name, String content) implements Msg {}

    /** One answer of the model. Never thrown: a failure comes back with error set. */
    public record Reply(String text, List<Call> calls, int promptTokens, int completionTokens, String error) {
        public static Reply failed(String error) {
            return new Reply(null, List.of(), 0, 0, error);
        }
    }
}
