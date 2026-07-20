package postmannen.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
