package postmannen.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import postmannen.model.Collection
import postmannen.model.Workspace

class PostmanApiServiceImpl(private val apiKey: String) : PostmanApiService {

    private val client = HttpClient(CIO) {
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
        response.workspace.collections.map { Collection(id = it.id, name = it.name) }
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
