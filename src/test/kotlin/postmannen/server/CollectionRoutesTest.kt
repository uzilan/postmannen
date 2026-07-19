package postmannen.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
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
}
