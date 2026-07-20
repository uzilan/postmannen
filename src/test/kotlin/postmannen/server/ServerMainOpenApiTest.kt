package postmannen.server

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import postmannen.service.FakePostmanApiService
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerMainOpenApiTest {

    @OptIn(ExperimentalKtorApi::class)
    @Test
    fun `GET openapi serves the Swagger UI`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                workspaceRoutes(fake)
                registerOpenApi()
            }
        }
        val client = createClient { }

        val response = client.get("/openapi")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
