package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import postmannen.service.PostmanApiService

fun Route.workspaceRoutes(service: PostmanApiService) {
    get("/api/workspaces") {
        call.respondResult(service.getWorkspaces())
    }

    get("/api/workspaces/{id}") {
        val id = call.parameters["id"]!!
        call.respondResult(service.getCollections(id))
    }

    post("/api/workspaces/{id}/refresh") {
        val id = call.parameters["id"]!!
        service.invalidateWorkspace(id)
        call.respond(HttpStatusCode.NoContent)
    }
}
