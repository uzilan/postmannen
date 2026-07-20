package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import postmannen.model.Collection
import postmannen.model.Workspace
import postmannen.service.PostmanApiService

@OptIn(ExperimentalKtorApi::class)
fun Route.workspaceRoutes(service: PostmanApiService) {
    get("/api/workspaces") {
        call.respondResult(service.getWorkspaces())
    }.describe {
        summary = "List workspaces"
        responses {
            HttpStatusCode.OK {
                description = "The available Postman workspaces"
                schema = jsonSchema<List<Workspace>>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    get("/api/workspaces/{id}") {
        val id = call.parameters["id"]!!
        call.respondResult(service.getCollections(id))
    }.describe {
        summary = "Get a workspace's collections"
        parameters {
            path("id") {
                description = "The workspace id"
                required = true
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "The workspace's collections"
                schema = jsonSchema<List<Collection>>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    post("/api/workspaces/{id}/refresh") {
        val id = call.parameters["id"]!!
        service.invalidateWorkspace(id)
        call.respond(HttpStatusCode.NoContent)
    }.describe {
        summary = "Invalidate a workspace's cache"
        parameters {
            path("id") {
                description = "The workspace id"
                required = true
            }
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The workspace's cached data was invalidated"
            }
        }
    }
}
