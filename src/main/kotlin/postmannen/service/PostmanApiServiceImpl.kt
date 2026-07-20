package postmannen.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import postmannen.model.Collection
import postmannen.model.CollectionDetail
import postmannen.model.CollectionNode
import postmannen.model.CollectionVariable
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.EnvironmentValue
import postmannen.model.RequestHeader
import postmannen.model.Workspace

class PostmanApiServiceImpl(
    private val apiKey: String,
    engine: HttpClientEngine = CIO.create()
) : PostmanApiService {

    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            url(BASE_URL)
            header("X-Api-Key", apiKey)
        }
    }

    override suspend fun getWorkspaces(): Result<List<Workspace>> = runCatching {
        val response: WorkspacesResponse = client.get { url { appendPathSegments("workspaces") } }.body()
        response.workspaces.map { Workspace(id = it.id, name = it.name, type = it.type) }
    }

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> = runCatching {
        val response: WorkspaceDetailResponse =
            client.get { url { appendPathSegments("workspaces", workspaceId) } }.body()
        response.workspace.collections.map { Collection(id = it.id, name = it.name, uid = it.uid) }
    }

    override suspend fun getCollectionDetail(collectionUid: String): Result<CollectionDetail> = runCatching {
        val response: CollectionDetailResponse =
            client.get { url { appendPathSegments("collections", collectionUid) } }.body()
        CollectionDetail(
            uid = collectionUid,
            name = response.collection.info.name,
            items = response.collection.item.map { it.toNode() },
            variables = response.collection.variable.map {
                CollectionVariable(key = it.key, value = it.value, enabled = it.enabled)
            }
        )
    }

    override suspend fun getEnvironments(workspaceId: String): Result<List<Environment>> = runCatching {
        val response: EnvironmentsResponse =
            client.get {
                url {
                    appendPathSegments("environments")
                    parameters.append("workspace", workspaceId)
                }
            }.body()
        response.environments.map { Environment(id = it.id, name = it.name, uid = it.uid) }
    }

    override suspend fun getEnvironmentDetail(environmentUid: String): Result<EnvironmentDetail> = runCatching {
        val response: EnvironmentDetailResponse =
            client.get { url { appendPathSegments("environments", environmentUid) } }.body()
        val env = response.environment
        val values = env.values.map {
            EnvironmentValue(key = it.key, value = it.value, enabled = it.enabled, type = it.type)
        }
        EnvironmentDetail(id = env.id, uid = environmentUid, name = env.name, values = values)
    }

    override suspend fun updateEnvironment(detail: EnvironmentDetail): Result<Unit> = runCatching {
        client.put {
            url { appendPathSegments("environments", detail.uid) }
            contentType(ContentType.Application.Json)
            setBody(
                EnvironmentUpdateRequest(
                    environment = EnvironmentUpdateDto(
                        name = detail.name,
                        values = detail.values.map {
                            EnvironmentValueDto(key = it.key, value = it.value, enabled = it.enabled, type = it.type)
                        }
                    )
                )
            )
        }
    }

    override suspend fun createEnvironment(workspaceId: String, name: String): Result<Environment> = runCatching {
        val response: EnvironmentCreateResponse = client.post {
            url {
                appendPathSegments("environments")
                parameters.append("workspace", workspaceId)
            }
            contentType(ContentType.Application.Json)
            setBody(EnvironmentCreateRequest(EnvironmentCreateDto(name = name, values = emptyList())))
        }.body()
        Environment(id = response.environment.id, name = response.environment.name, uid = response.environment.uid)
    }

    override suspend fun createCollection(workspaceId: String, name: String): Result<Collection> = runCatching {
        val response: CollectionCreateResponse = client.post {
            url {
                appendPathSegments("collections")
                parameters.append("workspace", workspaceId)
            }
            contentType(ContentType.Application.Json)
            setBody(
                CollectionCreateRequest(
                    CollectionCreateDto(
                        info = CollectionCreateInfoDto(
                            name = name,
                            schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                        ),
                        item = emptyList()
                    )
                )
            )
        }.body()
        Collection(id = response.collection.id, name = response.collection.name, uid = response.collection.uid)
    }

    override suspend fun deleteCollection(uid: String): Result<Unit> = runCatching {
        client.delete { url { appendPathSegments("collections", uid) } }
    }

    override suspend fun deleteEnvironment(uid: String): Result<Unit> = runCatching {
        client.delete { url { appendPathSegments("environments", uid) } }
    }

    override suspend fun renameCollection(uid: String, name: String): Result<Unit> = runCatching {
        val response: JsonObject = client.get { url { appendPathSegments("collections", uid) } }.body()
        val collection = response.getValue("collection").jsonObject
        val info = collection.getValue("info").jsonObject
        val updatedInfo = JsonObject(info + ("name" to JsonPrimitive(name)))
        val updatedCollection = JsonObject(collection + ("info" to updatedInfo))
        client.put {
            url { appendPathSegments("collections", uid) }
            contentType(ContentType.Application.Json)
            setBody(JsonObject(mapOf("collection" to updatedCollection)))
        }
    }

    override suspend fun renameEnvironment(uid: String, name: String): Result<Unit> = runCatching {
        val detail = getEnvironmentDetail(uid).getOrThrow()
        updateEnvironment(detail.copy(name = name)).getOrThrow()
    }

    companion object {
        const val BASE_URL = "https://api.getpostman.com"
    }
}

@Serializable
private data class WorkspacesResponse(val workspaces: List<WorkspaceDto>)

@Serializable
private data class WorkspaceDto(val id: String, val name: String, val type: String)

@Serializable
private data class WorkspaceDetailResponse(val workspace: WorkspaceDetailDto)

@Serializable
private data class WorkspaceDetailDto(val id: String, val name: String, val collections: List<CollectionDto> = emptyList())

@Serializable
private data class CollectionDto(val id: String, val name: String, val uid: String)

@Serializable
private data class EnvironmentsResponse(val environments: List<EnvironmentDto>)

@Serializable
private data class EnvironmentDto(val id: String, val name: String, val uid: String)

@Serializable
private data class EnvironmentDetailResponse(val environment: EnvironmentDetailDto)

@Serializable
private data class EnvironmentDetailDto(val id: String, val name: String, val values: List<EnvironmentValueDto> = emptyList())

@Serializable
private data class EnvironmentValueDto(val key: String, val value: String, val enabled: Boolean = true, val type: String = "default")

@Serializable
private data class EnvironmentUpdateRequest(val environment: EnvironmentUpdateDto)

@Serializable
private data class EnvironmentUpdateDto(val name: String, val values: List<EnvironmentValueDto>)

@Serializable
private data class EnvironmentCreateRequest(val environment: EnvironmentCreateDto)

@Serializable
private data class EnvironmentCreateDto(val name: String, val values: List<EnvironmentValueDto>)

@Serializable
private data class EnvironmentCreateResponse(val environment: EnvironmentCreateResponseDto)

@Serializable
private data class EnvironmentCreateResponseDto(val id: String, val name: String, val uid: String)

@Serializable
private data class CollectionCreateRequest(val collection: CollectionCreateDto)

@Serializable
private data class CollectionCreateDto(val info: CollectionCreateInfoDto, val item: List<CollectionItemDto>)

@Serializable
private data class CollectionCreateInfoDto(val name: String, val schema: String)

@Serializable
private data class CollectionCreateResponse(val collection: CollectionCreateResponseDto)

@Serializable
private data class CollectionCreateResponseDto(val id: String, val name: String, val uid: String)

@Serializable
private data class CollectionDetailResponse(val collection: CollectionDetailDto)

@Serializable
private data class CollectionDetailDto(
    val info: CollectionInfoDto,
    val item: List<CollectionItemDto> = emptyList(),
    val variable: List<CollectionVariableDto> = emptyList()
)

@Serializable
private data class CollectionInfoDto(val name: String)

@Serializable
private data class CollectionVariableDto(val key: String, val value: String = "", val enabled: Boolean = true)

@Serializable
private data class CollectionItemDto(
    val name: String,
    val item: List<CollectionItemDto>? = null,
    val request: CollectionRequestDto? = null
)

@Serializable
private data class CollectionRequestDto(
    val method: String = "GET",
    val header: List<CollectionRequestHeaderDto> = emptyList(),
    val url: CollectionRequestUrlDto? = null,
    val body: CollectionRequestBodyDto? = null
)

@Serializable
private data class CollectionRequestHeaderDto(val key: String, val value: String = "")

@Serializable
private data class CollectionRequestUrlDto(val raw: String = "")

@Serializable
private data class CollectionRequestBodyDto(val mode: String? = null, val raw: String? = null)

private fun CollectionItemDto.toNode(): CollectionNode =
    if (item != null) {
        CollectionNode.Folder(name, item.map { it.toNode() })
    } else {
        val req = request
        CollectionNode.RequestItem(
            name = name,
            method = req?.method ?: "GET",
            url = req?.url?.raw ?: "",
            headers = req?.header?.map { RequestHeader(it.key, it.value) } ?: emptyList(),
            body = req?.body?.takeIf { it.mode == "raw" }?.raw
        )
    }
