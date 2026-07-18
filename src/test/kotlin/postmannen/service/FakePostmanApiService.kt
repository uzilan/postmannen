package postmannen.service

import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.Workspace

class FakePostmanApiService : PostmanApiService {
    var workspacesResult: Result<List<Workspace>> = Result.success(FIXTURE_WORKSPACES)
    var collectionsResult: Result<List<Collection>> = Result.success(FIXTURE_COLLECTIONS)
    var environmentsResult: Result<List<Environment>> = Result.success(FIXTURE_ENVIRONMENTS)
    var environmentDetailResults: Map<String, Result<EnvironmentDetail>> = mapOf(
        "env-1-uid" to Result.success(FIXTURE_ENVIRONMENT_DETAIL_STAGING),
        "env-2-uid" to Result.success(FIXTURE_ENVIRONMENT_DETAIL_PRODUCTION)
    )
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

    override suspend fun getEnvironmentDetail(environmentUid: String): Result<EnvironmentDetail> =
        environmentDetailResults[environmentUid]
            ?: Result.failure(IllegalStateException("no fixture registered for uid $environmentUid"))

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
            Environment(id = "env-1", name = "Staging", uid = "env-1-uid"),
            Environment(id = "env-2", name = "Production", uid = "env-2-uid")
        )
        val FIXTURE_ENVIRONMENT_DETAIL_STAGING = EnvironmentDetail(
            id = "env-1",
            name = "Staging",
            values = mapOf("BASE_URL" to "https://staging.example.com", "API_KEY" to "stg_xxx")
        )
        val FIXTURE_ENVIRONMENT_DETAIL_PRODUCTION = EnvironmentDetail(
            id = "env-2",
            name = "Production",
            values = mapOf("BASE_URL" to "https://prod.example.com")
        )
    }
}
