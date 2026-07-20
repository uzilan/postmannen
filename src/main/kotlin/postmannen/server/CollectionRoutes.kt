package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import postmannen.service.PostmanApiService

@Serializable
data class CreateCollectionRequest(val workspaceId: String, val name: String)

fun Route.collectionRoutes(service: PostmanApiService) {
    get("/api/collections/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondResult(service.getCollectionDetail(uid))
    }

    post("/api/collections") {
        val request = call.receive<CreateCollectionRequest>()
        call.respondResult(service.createCollection(request.workspaceId, request.name), HttpStatusCode.Created)
    }
}
