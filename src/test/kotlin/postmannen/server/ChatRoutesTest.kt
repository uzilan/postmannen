package postmannen.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import postmannen.service.ChatTurnResult
import postmannen.service.FakeClaudeCliService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRoutesTest {

    @Test
    fun `POST api-chat builds context-prefixed prompt and returns the service's reply`() = testApplication {
        val fake = FakeClaudeCliService().apply {
            result = ChatTurnResult(text = "hi there", toolsUsed = emptyList(), errored = false, sessionId = "session-1")
        }
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { chatRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    prompt = "add a key",
                    context = ChatContextDto(workspaceName = "Engineering", workspaceId = "ws-1", highlightedLabel = "Staging")
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChatResponse>()
        assertEquals("hi there", body.reply)
        assertEquals("session-1", body.sessionId)
        assertTrue(fake.lastPrompt!!.contains("Engineering"))
        assertTrue(fake.lastPrompt!!.contains("ws-1"))
        assertTrue(fake.lastPrompt!!.contains("Staging"))
        assertTrue(fake.lastPrompt!!.endsWith("add a key"))
    }

    @Test
    fun `POST api-chat with no context sends the raw prompt unprefixed`() = testApplication {
        val fake = FakeClaudeCliService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { chatRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(prompt = "hello"))
        }

        assertEquals("hello", fake.lastPrompt)
    }

    @Test
    fun `POST api-chat passes resumeSessionId through to the service`() = testApplication {
        val fake = FakeClaudeCliService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { chatRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(prompt = "hello", resumeSessionId = "session-abc"))
        }

        assertEquals("session-abc", fake.lastResumeSessionId)
    }

    @Test
    fun `POST api-chat surfaces errored and toolsUsed from the service result`() = testApplication {
        val fake = FakeClaudeCliService().apply {
            result = ChatTurnResult(text = "Error: boom", toolsUsed = listOf("update_environment"), errored = true, sessionId = null)
        }
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { chatRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(prompt = "change base_url"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChatResponse>()
        assertEquals(true, body.errored)
        assertEquals(listOf("update_environment"), body.toolsUsed)
        assertNull(body.sessionId)
    }
}
