package postmannen.server

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import postmannen.service.PostmanApiService

fun Route.collectionRoutes(service: PostmanApiService) {
    get("/api/collections/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondResult(service.getCollectionDetail(uid))
    }
}
