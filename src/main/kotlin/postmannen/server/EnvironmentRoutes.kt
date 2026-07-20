package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.service.PostmanApiService

@Serializable
data class CreateEnvironmentRequest(val workspaceId: String, val name: String)

@OptIn(ExperimentalKtorApi::class)
fun Route.environmentRoutes(service: PostmanApiService) {
    get("/api/environments") {
        val workspaceId = call.request.queryParameters["workspaceId"]
        if (workspaceId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "workspaceId query parameter is required"))
            return@get
        }
        call.respondResult(service.getEnvironments(workspaceId))
    }.describe {
        summary = "List environments for a workspace"
        parameters {
            query("workspaceId") {
                description = "The workspace id to list environments for"
                required = true
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "The workspace's environments"
                schema = jsonSchema<List<Environment>>()
            }
            HttpStatusCode.BadRequest {
                description = "workspaceId query parameter is missing"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    get("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondResult(service.getEnvironmentDetail(uid))
    }.describe {
        summary = "Get an environment's key/value details"
        parameters {
            path("uid") {
                description = "The environment uid"
                required = true
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "The environment detail"
                schema = jsonSchema<EnvironmentDetail>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    put("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        val detail = call.receive<EnvironmentDetail>().copy(uid = uid)
        call.respondUnitResult(service.updateEnvironment(detail))
    }.describe {
        summary = "Update an environment's key/value details"
        parameters {
            path("uid") {
                description = "The environment uid"
                required = true
            }
        }
        requestBody {
            description = "The environment's new key/value details"
            schema = jsonSchema<EnvironmentDetail>()
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The environment was updated"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    post("/api/environments") {
        val request = call.receive<CreateEnvironmentRequest>()
        call.respondResult(service.createEnvironment(request.workspaceId, request.name), HttpStatusCode.Created)
    }.describe {
        summary = "Create an environment"
        requestBody {
            description = "The workspace to create the environment in and its name"
            schema = jsonSchema<CreateEnvironmentRequest>()
        }
        responses {
            HttpStatusCode.Created {
                description = "The created environment"
                schema = jsonSchema<EnvironmentDetail>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    delete("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondUnitResult(service.deleteEnvironment(uid))
    }.describe {
        summary = "Delete an environment"
        parameters {
            path("uid") {
                description = "The environment uid"
                required = true
            }
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The environment was deleted"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    patch("/api/environments/{uid}") {
        val uid = call.parameters["uid"]!!
        val request = call.receive<RenameRequest>()
        call.respondUnitResult(service.renameEnvironment(uid, request.name))
    }.describe {
        summary = "Rename an environment"
        parameters {
            path("uid") {
                description = "The environment uid"
                required = true
            }
        }
        requestBody {
            description = "The environment's new name"
            schema = jsonSchema<RenameRequest>()
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The environment was renamed"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }
}
