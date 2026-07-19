package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import postmannen.model.EnvironmentDetail
import postmannen.service.PostmanApiService

@Serializable
data class CreateEnvironmentRequest(val workspaceId: String, val name: String)

fun Route.environmentRoutes(service: PostmanApiService) {
    get("/api/environments") {
        val workspaceId = call.request.queryParameters["workspaceId"]
        if (workspaceId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "workspaceId query parameter is required"))
            return@get
        }
        call.respondResult(service.getEnvironments(workspaceId))
    }

    get("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondResult(service.getEnvironmentDetail(uid))
    }

    put("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        val detail = call.receive<EnvironmentDetail>().copy(uid = uid)
        call.respondUnitResult(service.updateEnvironment(detail))
    }

    post("/api/environments") {
        val request = call.receive<CreateEnvironmentRequest>()
        call.respondResult(service.createEnvironment(request.workspaceId, request.name), HttpStatusCode.Created)
    }
}
