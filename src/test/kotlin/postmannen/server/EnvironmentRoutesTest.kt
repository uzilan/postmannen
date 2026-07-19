package postmannen.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.service.FakePostmanApiService
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentRoutesTest {

    private fun ApplicationTestBuilder.setup(fake: FakePostmanApiService) {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { environmentRoutes(fake) }
        }
    }

    @Test
    fun `GET api-environments requires workspaceId and returns fixture list`() = testApplication {
        val fake = FakePostmanApiService()
        setup(fake)
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/environments?workspaceId=ws-1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENTS, response.body<List<Environment>>())
        assertEquals("ws-1", fake.lastRequestedWorkspaceId)
    }

    @Test
    fun `GET api-environments without workspaceId returns 400`() = testApplication {
        setup(FakePostmanApiService())
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/environments")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET api-environments-uid returns environment detail`() = testApplication {
        val fake = FakePostmanApiService()
        setup(fake)
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/environments/env-1-uid")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING, response.body<EnvironmentDetail>())
    }

    @Test
    fun `PUT api-environments-uid updates and returns 204`() = testApplication {
        val fake = FakePostmanApiService()
        setup(fake)
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val updated = FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING.copy(name = "Staging Renamed")

        val response = client.put("/api/environments/env-1-uid") {
            contentType(ContentType.Application.Json)
            setBody(updated)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(updated.copy(uid = "env-1-uid"), fake.lastUpdatedEnvironmentDetail)
    }

    @Test
    fun `POST api-environments creates a new environment`() = testApplication {
        val fake = FakePostmanApiService()
        setup(fake)
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/api/environments") {
            contentType(ContentType.Application.Json)
            setBody(CreateEnvironmentRequest(workspaceId = "ws-1", name = "New Env"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("ws-1", fake.lastCreatedEnvironmentWorkspaceId)
        assertEquals("New Env", fake.lastCreatedEnvironmentName)
    }
}
