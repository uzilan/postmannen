package postmannen.service

import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.Workspace

interface PostmanApiService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
    suspend fun getEnvironments(workspaceId: String): Result<List<Environment>>
    suspend fun getEnvironmentDetail(environmentUid: String): Result<EnvironmentDetail>
}
