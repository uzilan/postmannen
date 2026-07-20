package postmannen.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import postmannen.model.CollectionDetail
import postmannen.service.FakePostmanApiService
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionRoutesTest {

    @Test
    fun `GET api-collections-uid returns that collection's detail`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { collectionRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/collections/col-1-uid")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_AUTH, response.body<CollectionDetail>())
    }

    @Test
    fun `GET api-collections-uid returns 502 for unknown uid`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { collectionRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.get("/api/collections/does-not-exist")

        assertEquals(HttpStatusCode.BadGateway, response.status)
    }

    @Test
    fun `POST api-collections creates a new collection`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { collectionRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/api/collections") {
            contentType(ContentType.Application.Json)
            setBody(CreateCollectionRequest(workspaceId = "ws-1", name = "New Collection"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("ws-1", fake.lastCreatedCollectionWorkspaceId)
        assertEquals("New Collection", fake.lastCreatedCollectionName)
    }

    @Test
    fun `DELETE api-collections-uid deletes the collection`() = testApplication {
        val fake = FakePostmanApiService()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { collectionRoutes(fake) }
        }
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.delete("/api/collections/col-1-uid")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("col-1-uid", fake.lastDeletedCollectionUid)
    }
}
