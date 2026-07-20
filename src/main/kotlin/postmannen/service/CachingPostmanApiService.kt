package postmannen.service

import postmannen.model.Collection
import postmannen.model.CollectionDetail
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.Workspace
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class CachingPostmanApiService(private val delegate: PostmanApiService) : PostmanApiService {
    private val workspacesCache = AtomicReference<List<Workspace>?>(null)
    private val collectionsCache = ConcurrentHashMap<String, List<Collection>>()
    private val environmentsCache = ConcurrentHashMap<String, List<Environment>>()
    private val collectionDetailCache = ConcurrentHashMap<String, CollectionDetail>()
    private val environmentDetailCache = ConcurrentHashMap<String, EnvironmentDetail>()

    override suspend fun getWorkspaces(): Result<List<Workspace>> {
        workspacesCache.get()?.let { return Result.success(it) }
        return delegate.getWorkspaces().onSuccess { workspacesCache.set(it) }
    }

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> {
        collectionsCache[workspaceId]?.let { return Result.success(it) }
        return delegate.getCollections(workspaceId).onSuccess { collectionsCache[workspaceId] = it }
    }

    override suspend fun getCollectionDetail(collectionUid: String): Result<CollectionDetail> {
        collectionDetailCache[collectionUid]?.let { return Result.success(it) }
        return delegate.getCollectionDetail(collectionUid).onSuccess { collectionDetailCache[collectionUid] = it }
    }

    override suspend fun getEnvironments(workspaceId: String): Result<List<Environment>> {
        environmentsCache[workspaceId]?.let { return Result.success(it) }
        return delegate.getEnvironments(workspaceId).onSuccess { environmentsCache[workspaceId] = it }
    }

    override suspend fun getEnvironmentDetail(environmentUid: String): Result<EnvironmentDetail> {
        environmentDetailCache[environmentUid]?.let { return Result.success(it) }
        return delegate.getEnvironmentDetail(environmentUid).onSuccess { environmentDetailCache[environmentUid] = it }
    }

    override suspend fun updateEnvironment(detail: EnvironmentDetail): Result<Unit> =
        delegate.updateEnvironment(detail).onSuccess { environmentDetailCache[detail.uid] = detail }

    override suspend fun createEnvironment(workspaceId: String, name: String): Result<Environment> =
        delegate.createEnvironment(workspaceId, name).onSuccess { env ->
            environmentsCache[workspaceId] = (environmentsCache[workspaceId] ?: emptyList()) + env
        }

    override suspend fun createCollection(workspaceId: String, name: String): Result<Collection> =
        delegate.createCollection(workspaceId, name).onSuccess { col ->
            collectionsCache[workspaceId] = (collectionsCache[workspaceId] ?: emptyList()) + col
        }

    override suspend fun deleteCollection(uid: String): Result<Unit> =
        delegate.deleteCollection(uid).onSuccess {
            for (workspaceId in collectionsCache.keys) {
                collectionsCache[workspaceId]?.let { cols -> collectionsCache[workspaceId] = cols.filterNot { it.uid == uid } }
            }
            collectionDetailCache.remove(uid)
        }

    override fun invalidateWorkspace(workspaceId: String) {
        val collectionUids = collectionsCache.remove(workspaceId)?.map { it.uid } ?: emptyList()
        val environmentUids = environmentsCache.remove(workspaceId)?.map { it.uid } ?: emptyList()
        collectionUids.forEach { collectionDetailCache.remove(it) }
        environmentUids.forEach { environmentDetailCache.remove(it) }
    }
}
