package postmannen.service

import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Workspace

class FakePostmanApiService : PostmanApiService {
    var workspacesResult: Result<List<Workspace>> = Result.success(FIXTURE_WORKSPACES)
    var collectionsResult: Result<List<Collection>> = Result.success(FIXTURE_COLLECTIONS)
    var environmentsResult: Result<List<Environment>> = Result.success(FIXTURE_ENVIRONMENTS)
    var lastRequestedWorkspaceId: String? = null

    override suspend fun getWorkspaces(): Result<List<Workspace>> = workspacesResult

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> {
        lastRequestedWorkspaceId = workspaceId
        return collectionsResult
    }

    override suspend fun getEnvironments(workspaceId: String): Result<List<Environment>> {
        lastRequestedWorkspaceId = workspaceId
        return environmentsResult
    }

    companion object {
        val FIXTURE_WORKSPACES = listOf(
            Workspace(id = "ws-1", name = "Engineering", type = "team"),
            Workspace(id = "ws-2", name = "Personal", type = "personal")
        )
        val FIXTURE_COLLECTIONS = listOf(
            Collection(id = "col-1", name = "Auth API"),
            Collection(id = "col-2", name = "Billing API")
        )
        val FIXTURE_ENVIRONMENTS = listOf(
            Environment(id = "env-1", name = "Staging"),
            Environment(id = "env-2", name = "Production")
        )
    }
}
