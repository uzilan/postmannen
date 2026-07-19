package postmannen.service

import postmannen.model.Collection
import postmannen.model.CollectionDetail
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.Workspace

interface PostmanApiService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
    suspend fun getCollectionDetail(collectionUid: String): Result<CollectionDetail>
    suspend fun getEnvironments(workspaceId: String): Result<List<Environment>>
    suspend fun getEnvironmentDetail(environmentUid: String): Result<EnvironmentDetail>
    suspend fun updateEnvironment(detail: EnvironmentDetail): Result<Unit>
    suspend fun createEnvironment(workspaceId: String, name: String): Result<Environment>
    fun invalidateWorkspace(workspaceId: String) {}
}
