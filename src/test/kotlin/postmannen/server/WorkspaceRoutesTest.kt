package postmannen.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import postmannen.model.Collection
import postmannen.model.Workspace
import postmannen.service.FakePostmanApiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceRoutesTest {

    @Test
    fun `GET api-workspaces returns fixture workspaces`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { workspaceRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/workspaces")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FakePostmanApiService.FIXTURE_WORKSPACES, response.body<List<Workspace>>())
    }

    @Test
    fun `GET api-workspaces-id returns that workspace's collections`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { workspaceRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/workspaces/ws-1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FakePostmanApiService.FIXTURE_COLLECTIONS, response.body<List<Collection>>())
        assertEquals("ws-1", fake.lastRequestedWorkspaceId)
    }

    @Test
    fun `POST api-workspaces-id-refresh invalidates the workspace and returns 204`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { workspaceRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/api/workspaces/ws-1/refresh")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue("ws-1" in fake.invalidateWorkspaceCalls)
    }
}
