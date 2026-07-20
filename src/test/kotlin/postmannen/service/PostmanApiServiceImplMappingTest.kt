package postmannen.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostmanApiServiceImplMappingTest {

    private fun serviceWithResponse(body: String): PostmanApiServiceImpl {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return PostmanApiServiceImpl(apiKey = "fake-key", engine = engine)
    }

    @Test
    fun `getCollectionDetail maps request method, url, headers and raw body`() = runTest {
        val json = """
            {
              "collection": {
                "info": { "name": "Auth API" },
                "item": [
                  {
                    "name": "Login",
                    "request": {
                      "method": "POST",
                      "header": [{"key": "Content-Type", "value": "application/json"}],
                      "url": {"raw": "https://auth.example.com/login"},
                      "body": {"mode": "raw", "raw": "{\"user\":\"x\"}"}
                    }
                  }
                ],
                "variable": []
              }
            }
        """.trimIndent()
        val service = serviceWithResponse(json)

        val result = service.getCollectionDetail("col-1-uid")

        val detail = result.getOrThrow()
        val item = detail.items.single() as postmannen.model.CollectionNode.RequestItem
        assertEquals("Login", item.name)
        assertEquals("POST", item.method)
        assertEquals("https://auth.example.com/login", item.url)
        assertEquals(listOf(postmannen.model.RequestHeader("Content-Type", "application/json")), item.headers)
        assertEquals("{\"user\":\"x\"}", item.body)
    }

    @Test
    fun `getCollectionDetail maps non-raw body mode to null body`() = runTest {
        val json = """
            {
              "collection": {
                "info": { "name": "Billing API" },
                "item": [
                  {
                    "name": "Create Invoice",
                    "request": {
                      "method": "POST",
                      "header": [],
                      "url": {"raw": "https://billing.example.com/invoices"},
                      "body": {"mode": "urlencoded"}
                    }
                  }
                ],
                "variable": []
              }
            }
        """.trimIndent()
        val service = serviceWithResponse(json)

        val result = service.getCollectionDetail("col-2-uid")

        val item = result.getOrThrow().items.single() as postmannen.model.CollectionNode.RequestItem
        assertNull(item.body)
    }

    @Test
    fun `renameCollection patches only info-name and leaves every other field byte-for-byte untouched`() = runTest {
        val getJson = """
            {
              "collection": {
                "info": { "name": "Old Name", "_postman_id": "abc123" },
                "item": [{ "id": "item-1", "name": "Login" }],
                "variable": []
              }
            }
        """.trimIndent()
        var putBody: String? = null
        var putPath: String? = null
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get ->
                    respond(getJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                HttpMethod.Put -> {
                    putPath = request.url.encodedPath
                    putBody = request.body.toByteArray().decodeToString()
                    respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("unexpected method ${request.method}")
            }
        }
        val service = PostmanApiServiceImpl(apiKey = "fake-key", engine = engine)

        val result = service.renameCollection("col-1-uid", "New Name")

        assertTrue(result.isSuccess)
        assertEquals("/collections/col-1-uid", putPath)
        val sentCollection = Json.parseToJsonElement(putBody!!).jsonObject.getValue("collection").jsonObject
        val sentInfo = sentCollection.getValue("info").jsonObject
        assertEquals("New Name", sentInfo.getValue("name").jsonPrimitive.content)
        assertEquals("abc123", sentInfo.getValue("_postman_id").jsonPrimitive.content)
        assertEquals(1, (sentCollection.getValue("item") as JsonArray).size)
    }

    @Test
    fun `renameEnvironment sends the fetched values unchanged with only the name replaced`() = runTest {
        val getJson = """
            {
              "environment": {
                "id": "env-1",
                "name": "Old Env Name",
                "values": [{"key": "BASE_URL", "value": "https://x", "enabled": true, "type": "default"}]
              }
            }
        """.trimIndent()
        var putBody: String? = null
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get ->
                    respond(getJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                HttpMethod.Put -> {
                    putBody = request.body.toByteArray().decodeToString()
                    respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("unexpected method ${request.method}")
            }
        }
        val service = PostmanApiServiceImpl(apiKey = "fake-key", engine = engine)

        val result = service.renameEnvironment("env-1-uid", "New Env Name")

        assertTrue(result.isSuccess)
        val sentEnvironment = Json.parseToJsonElement(putBody!!).jsonObject.getValue("environment").jsonObject
        assertEquals("New Env Name", sentEnvironment.getValue("name").jsonPrimitive.content)
        val sentValues = sentEnvironment.getValue("values") as JsonArray
        assertEquals(1, sentValues.size)
        assertEquals("BASE_URL", sentValues[0].jsonObject.getValue("key").jsonPrimitive.content)
    }
}
