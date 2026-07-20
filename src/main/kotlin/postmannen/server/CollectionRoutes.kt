package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import postmannen.model.CollectionDetail
import postmannen.service.PostmanApiService

@Serializable
data class CreateCollectionRequest(val workspaceId: String, val name: String)

@Serializable
data class RenameRequest(val name: String)

@OptIn(ExperimentalKtorApi::class)
fun Route.collectionRoutes(service: PostmanApiService) {
    get("/api/collections/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondResult(service.getCollectionDetail(uid))
    }.describe {
        summary = "Get a collection's folder/request tree and variables"
        parameters {
            path("uid") {
                description = "The collection uid"
                required = true
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "The collection detail"
                schema = jsonSchema<CollectionDetail>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    post("/api/collections") {
        val request = call.receive<CreateCollectionRequest>()
        call.respondResult(service.createCollection(request.workspaceId, request.name), HttpStatusCode.Created)
    }.describe {
        summary = "Create a collection"
        requestBody {
            description = "The workspace to create the collection in and its name"
            schema = jsonSchema<CreateCollectionRequest>()
        }
        responses {
            HttpStatusCode.Created {
                description = "The created collection"
                schema = jsonSchema<CollectionDetail>()
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    delete("/api/collections/{uid}") {
        val uid = call.parameters["uid"]!!
        call.respondUnitResult(service.deleteCollection(uid))
    }.describe {
        summary = "Delete a collection"
        parameters {
            path("uid") {
                description = "The collection uid"
                required = true
            }
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The collection was deleted"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }

    patch("/api/collections/{uid}") {
        val uid = call.parameters["uid"]!!
        val request = call.receive<RenameRequest>()
        call.respondUnitResult(service.renameCollection(uid, request.name))
    }.describe {
        summary = "Rename a collection"
        parameters {
            path("uid") {
                description = "The collection uid"
                required = true
            }
        }
        requestBody {
            description = "The collection's new name"
            schema = jsonSchema<RenameRequest>()
        }
        responses {
            HttpStatusCode.NoContent {
                description = "The collection was renamed"
            }
            HttpStatusCode.BadGateway {
                description = "The Postman API request failed"
            }
        }
    }
}
