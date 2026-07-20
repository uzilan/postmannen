package postmannen.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import postmannen.model.McpTool
import java.io.File

data class ChatTurnResult(val text: String, val toolsUsed: List<String>, val errored: Boolean, val sessionId: String?)

interface ClaudeCliService {
    suspend fun sendMessage(prompt: String, resumeSessionId: String?): ChatTurnResult
    suspend fun getAvailableTools(): Result<List<McpTool>>
}

class ClaudeCliServiceImpl(private val postmanApiKey: String) : ClaudeCliService {

    @Volatile private var mcpConfigFile: File? = null
    @Volatile private var workDir: File? = null
    @Volatile private var cachedTools: List<McpTool>? = null

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
                    "Reply in ONE short sentence stating only the final result. " +
                        "Never mention lookups, searches, fetches, ids, or any other " +
                        "intermediate step, even in passing. Example: user asks to set " +
                        "a value, you reply exactly like \"ggg = lll in test3.\" — nothing more."
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
                                    is ClaudeStreamEvent.AssistantText -> {
                                        textBuilder.setLength(0)
                                        textBuilder.append(event.text)
                                    }
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

    override suspend fun getAvailableTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        cachedTools?.let { return@withContext Result.success(it) }
        runCatching {
            val processBuilder = ProcessBuilder("npx", "-y", "@postman/postman-mcp-server")
            processBuilder.environment()["POSTMAN_API_KEY"] = postmanApiKey
            val process = processBuilder.start()
            try {
                val writer = process.outputStream.bufferedWriter()
                val reader = process.inputStream.bufferedReader()

                fun send(id: Int?, method: String, params: JsonObject) {
                    val request = buildJsonObject {
                        put("jsonrpc", "2.0")
                        if (id != null) put("id", id)
                        put("method", method)
                        put("params", params)
                    }
                    writer.write(request.toString())
                    writer.write("\n")
                    writer.flush()
                }

                send(
                    id = 1,
                    method = "initialize",
                    params = buildJsonObject {
                        put("protocolVersion", "2024-11-05")
                        put("capabilities", buildJsonObject {})
                        put("clientInfo", buildJsonObject { put("name", "postmannen"); put("version", "1.0") })
                    }
                )
                reader.readLine()

                send(id = null, method = "notifications/initialized", params = buildJsonObject {})
                send(id = 2, method = "tools/list", params = buildJsonObject {})

                var toolsResponse: JsonObject? = null
                while (toolsResponse == null) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val parsed = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    if (parsed["id"]?.jsonPrimitive?.intOrNull == 2) toolsResponse = parsed
                }

                val toolsArray = toolsResponse?.get("result")?.jsonObject?.get("tools")?.jsonArray
                    ?: throw IllegalStateException("postman-mcp-server did not return a tools/list response")

                val tools = toolsArray.map { tool ->
                    val obj = tool.jsonObject
                    McpTool(
                        name = obj["name"]?.jsonPrimitive?.content ?: "unknown",
                        description = obj["description"]?.jsonPrimitive?.content ?: ""
                    )
                }
                cachedTools = tools
                tools
            } finally {
                process.destroy()
            }
        }
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
                    val messageText = StringBuilder()
                    content.forEach { block ->
                        val obj: JsonObject = block.jsonObject
                        when (obj["type"]?.jsonPrimitive?.content) {
                            "text" -> messageText.append(obj["text"]?.jsonPrimitive?.content ?: "")
                            "tool_use" -> onEvent(ClaudeStreamEvent.ToolUse(obj["name"]?.jsonPrimitive?.content ?: "unknown"))
                        }
                    }
                    if (messageText.isNotEmpty()) onEvent(ClaudeStreamEvent.AssistantText(messageText.toString()))
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
    data class AssistantText(val text: String) : ClaudeStreamEvent()
    data class ToolUse(val name: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
    object TurnComplete : ClaudeStreamEvent()
}
