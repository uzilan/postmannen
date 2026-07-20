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
import java.io.File

data class ChatTurnResult(val text: String, val toolsUsed: List<String>, val errored: Boolean, val sessionId: String?)

interface ClaudeCliService {
    suspend fun sendMessage(prompt: String, resumeSessionId: String?): ChatTurnResult
}

class ClaudeCliServiceImpl(private val postmanApiKey: String) : ClaudeCliService {

    @Volatile private var mcpConfigFile: File? = null
    @Volatile private var workDir: File? = null

    override suspend fun sendMessage(prompt: String, resumeSessionId: String?): ChatTurnResult =
        withContext(Dispatchers.IO) {
            var newSessionId = resumeSessionId
            val textBuilder = StringBuilder()
            val toolsUsed = mutableListOf<String>()
            var errored = false

            val result = runCatching {
                val configFile = ensureMcpConfig()
                val command = mutableListOf(
                    "claude", "-p", prompt,
                    "--output-format", "stream-json",
                    "--verbose",
                    "--mcp-config", configFile.absolutePath,
                    "--allowedTools", "mcp__postman",
                    "--append-system-prompt",
                    "Give short, concise answers. Report only the final outcome of the " +
                        "action taken — do not narrate intermediate steps like looking " +
                        "something up, fetching it, or finding it."
                )
                resumeSessionId?.let { command += listOf("--resume", it) }

                val process = ProcessBuilder(command).directory(ensureWorkDir()).redirectErrorStream(true).start()
                process.outputStream.close()

                val output = StringBuilder()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.forEach { line ->
                        output.appendLine(line)
                        parseLine(
                            line,
                            onSessionId = { newSessionId = it },
                            onEvent = { event ->
                                when (event) {
                                    is ClaudeStreamEvent.TextDelta -> textBuilder.append(event.text)
                                    is ClaudeStreamEvent.ToolUse -> toolsUsed.add(event.name)
                                    is ClaudeStreamEvent.Error -> {
                                        errored = true
                                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                                        textBuilder.append("Error: ${event.message}")
                                    }
                                    ClaudeStreamEvent.TurnComplete -> Unit
                                }
                            }
                        )
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw IllegalStateException("claude exited with code $exitCode: $output")
                }
            }

            if (result.isFailure) {
                errored = true
                if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                textBuilder.append("Error: ${result.exceptionOrNull()?.message}")
            }

            ChatTurnResult(text = textBuilder.toString(), toolsUsed = toolsUsed, errored = errored, sessionId = newSessionId)
        }

    private fun ensureWorkDir(): File {
        workDir?.let { return it }
        val dir = kotlin.io.path.createTempDirectory("postmannen-chat-").toFile()
        dir.deleteOnExit()
        workDir = dir
        return dir
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
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        file.deleteOnExit()
        file.writeText(config.toString())
        mcpConfigFile = file
        return file
    }

    private fun parseLine(line: String, onSessionId: (String) -> Unit, onEvent: (ClaudeStreamEvent) -> Unit) {
        runCatching {
            val json = Json.parseToJsonElement(line).jsonObject
            when (json["type"]?.jsonPrimitive?.content) {
                "system" -> {
                    if (json["subtype"]?.jsonPrimitive?.content == "init") {
                        json["session_id"]?.jsonPrimitive?.content?.let(onSessionId)
                    }
                }
                "assistant" -> {
                    val content: JsonArray = json["message"]?.jsonObject?.get("content")?.jsonArray ?: return@runCatching
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
}

private sealed class ClaudeStreamEvent {
    data class TextDelta(val text: String) : ClaudeStreamEvent()
    data class ToolUse(val name: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
    object TurnComplete : ClaudeStreamEvent()
}
