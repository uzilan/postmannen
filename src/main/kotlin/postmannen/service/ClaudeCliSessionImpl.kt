package postmannen.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import postmannen.model.ClaudeStreamEvent
import java.io.File

class ClaudeCliSessionImpl(private val postmanApiKey: String) : ClaudeCliSession {

    @Volatile private var sessionId: String? = null
    @Volatile private var mcpConfigFile: File? = null
    @Volatile private var currentProcess: Process? = null

    override suspend fun sendMessage(prompt: String, onEvent: (ClaudeStreamEvent) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val configFile = ensureMcpConfig()
                val command = mutableListOf(
                    "claude", "-p", prompt,
                    "--output-format", "stream-json",
                    "--verbose",
                    "--mcp-config", configFile.absolutePath,
                    "--permission-mode", "bypassPermissions"
                )
                sessionId?.let { command += listOf("--resume", it) }

                val process = ProcessBuilder(command).start()
                currentProcess = process
                process.outputStream.close()

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.forEach { line -> parseLine(line, onEvent) }
                }

                val exitCode = process.waitFor()
                currentProcess = null
                if (exitCode != 0) {
                    val stderr = process.errorStream.bufferedReader().readText()
                    throw IllegalStateException("claude exited with code $exitCode: $stderr")
                }
            }
        }

    override fun close() {
        currentProcess?.destroyForcibly()
        currentProcess = null
        mcpConfigFile?.delete()
        mcpConfigFile = null
    }

    private fun ensureMcpConfig(): File {
        mcpConfigFile?.let { return it }
        val config = buildJsonObject {
            putJsonObject("mcpServers") {
                putJsonObject("postman") {
                    put("command", "npx")
                    putJsonArray("args") {
                        add(Json.parseToJsonElement("\"-y\""))
                        add(Json.parseToJsonElement("\"@postman/postman-mcp-server\""))
                    }
                    putJsonObject("env") {
                        put("POSTMAN_API_KEY", postmanApiKey)
                    }
                }
            }
        }
        val file = File.createTempFile("postmannen-mcp-", ".json")
        file.deleteOnExit()
        file.writeText(config.toString())
        mcpConfigFile = file
        return file
    }

    private fun parseLine(line: String, onEvent: (ClaudeStreamEvent) -> Unit) {
        val json = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        when (json["type"]?.jsonPrimitive?.content) {
            "system" -> {
                if (json["subtype"]?.jsonPrimitive?.content == "init") {
                    sessionId = json["session_id"]?.jsonPrimitive?.content
                }
            }
            "assistant" -> {
                val content: JsonArray = json["message"]?.jsonObject?.get("content")?.jsonArray ?: return
                content.forEach { block ->
                    val obj: JsonObject = block.jsonObject
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "text" -> onEvent(ClaudeStreamEvent.TextDelta(obj["text"]?.jsonPrimitive?.content ?: ""))
                        "tool_use" -> onEvent(ClaudeStreamEvent.ToolUse(obj["name"]?.jsonPrimitive?.content ?: "unknown"))
                    }
                }
            }
            "result" -> {
                if (json["is_error"]?.jsonPrimitive?.boolean == true) {
                    val message = json["result"]?.jsonPrimitive?.content ?: "unknown error"
                    onEvent(ClaudeStreamEvent.Error(message))
                }
                onEvent(ClaudeStreamEvent.TurnComplete)
            }
        }
    }
}
