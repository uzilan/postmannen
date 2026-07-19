package postmannen.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.CollectionNode
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.EnvironmentValue
import postmannen.model.Tab
import postmannen.service.PostmanApiService

class AppViewModel(
    private val service: PostmanApiService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun update(block: AppState.() -> AppState) = _state.update(block)

    fun loadWorkspaces() {
        scope.launch {
            update { copy(loading = true) }
            service.getWorkspaces()
                .onSuccess { workspaces ->
                    update {
                        copy(
                            workspaces = workspaces,
                            loading = false,
                            selectedWorkspaceIndex = 0,
                            selectedEnvironmentIds = emptySet()
                        )
                    }
                    workspaces.firstOrNull()?.let {
                        loadCollections(it.id)
                        loadEnvironments(it.id)
                    }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun selectWorkspace(index: Int) {
        val workspace = _state.value.workspaces.getOrNull(index) ?: return
        update { copy(selectedWorkspaceIndex = index, selectedEnvironmentIds = emptySet()) }
        loadCollections(workspace.id)
        loadEnvironments(workspace.id)
    }

    fun loadCollections(workspaceId: String) {
        scope.launch {
            update { copy(loading = true) }
            service.getCollections(workspaceId)
                .onSuccess { collections ->
                    update {
                        copy(
                            collections = collections,
                            loading = false,
                            collapsedNodeIds = collections.map { it.uid }.toSet()
                        )
                    }
                    scope.launch {
                        val results = collections.map { c -> c.uid to scope.async { service.getCollectionDetail(c.uid) } }
                            .map { (uid, deferred) -> uid to deferred.await() }
                        val details = results.mapNotNull { (_, result) -> result.getOrNull() }
                        val failedNames = results.filter { (_, result) -> result.isFailure }
                            .mapNotNull { (uid, _) -> collections.firstOrNull { it.uid == uid }?.name }
                        val folderIds = details.flatMap { detail -> collectFolderIds(detail.uid, detail.items) }.toSet()
                        update {
                            copy(
                                collectionDetails = details,
                                collapsedNodeIds = collapsedNodeIds + folderIds,
                                statusMessage = if (failedNames.isNotEmpty()) "Error loading tree for: ${failedNames.joinToString(", ")}" else statusMessage
                            )
                        }
                    }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun loadEnvironments(workspaceId: String) {
        scope.launch {
            update { copy(loading = true) }
            service.getEnvironments(workspaceId)
                .onSuccess { environments ->
                    update { copy(environments = environments, loading = false) }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun createEnvironment(name: String) {
        if (name.isBlank()) return
        val workspace = _state.value.workspaces.getOrNull(_state.value.selectedWorkspaceIndex) ?: return
        scope.launch {
            service.createEnvironment(workspace.id, name)
                .onSuccess { env -> update { copy(environments = environments + env) } }
                .onFailure { e -> update { copy(statusMessage = "Error: ${e.message}") } }
        }
    }

    // Position-based path, matching TabbedListPanel's flattenChildren scheme exactly —
    // both must agree on node ids, or a folder collapsed here won't match the row the
    // UI tries to toggle.
    private fun collectFolderIds(parentId: String, nodes: List<CollectionNode>): List<String> =
        nodes.flatMapIndexed { i, node ->
            when (node) {
                is CollectionNode.Folder -> {
                    val id = "$parentId/$i"
                    listOf(id) + collectFolderIds(id, node.children)
                }
                is CollectionNode.RequestItem -> emptyList()
            }
        }

    fun toggleNodeCollapsed(nodeId: String) {
        update {
            val newSet = if (nodeId in collapsedNodeIds) collapsedNodeIds - nodeId else collapsedNodeIds + nodeId
            copy(collapsedNodeIds = newSet)
        }
    }

    fun setActiveTab(tab: Tab) {
        update { copy(activeTab = tab) }
    }

    fun toggleEnvironmentSelection(id: String) {
        update {
            val newSet = if (id in selectedEnvironmentIds) selectedEnvironmentIds - id else selectedEnvironmentIds + id
            copy(selectedEnvironmentIds = newSet)
        }
    }

    fun openComparison() {
        val selectedIds = _state.value.selectedEnvironmentIds
        if (selectedIds.size < 2) {
            update { copy(statusMessage = "Select at least 2 environments to compare") }
            return
        }
        val targets = _state.value.environments.filter { it.id in selectedIds }
        openDetailsOverlay(targets)
    }

    fun viewEnvironment(id: String) {
        val target = _state.value.environments.firstOrNull { it.id == id } ?: return
        openDetailsOverlay(listOf(target))
    }

    private fun openDetailsOverlay(targets: List<Environment>) {
        scope.launch {
            val results = targets.map { env -> scope.async { service.getEnvironmentDetail(env.uid) } }.awaitAll()
            val firstFailure = results.firstOrNull { it.isFailure }
            if (firstFailure != null) {
                update { copy(statusMessage = "Error: ${firstFailure.exceptionOrNull()?.message}") }
                return@launch
            }
            val details = results.map { it.getOrThrow() }
            update { copy(comparisonDetails = details, comparisonVisible = true) }
        }
    }

    fun closeComparison() {
        update { copy(comparisonVisible = false) }
    }

    fun updateEnvironmentValue(environmentUid: String, key: String, newValue: String) {
        val current = _state.value.comparisonDetails.find { it.uid == environmentUid } ?: return
        val existingIndex = current.values.indexOfFirst { it.key == key }
        val newValues = if (existingIndex >= 0) {
            current.values.toMutableList().also { it[existingIndex] = it[existingIndex].copy(value = newValue) }
        } else {
            current.values + EnvironmentValue(key = key, value = newValue, enabled = true, type = "default")
        }
        val updatedDetail = current.copy(values = newValues)
        scope.launch {
            service.updateEnvironment(updatedDetail)
                .onSuccess {
                    // Merge into whatever comparisonDetails is *now*, not the snapshot taken
                    // before this PUT started — a concurrent edit to a different key in the
                    // same environment may have already landed, and overwriting wholesale
                    // from a stale snapshot would silently revert it.
                    update {
                        copy(comparisonDetails = comparisonDetails.map { detail ->
                            if (detail.uid != environmentUid) return@map detail
                            val idx = detail.values.indexOfFirst { it.key == key }
                            val mergedValues = if (idx >= 0) {
                                detail.values.toMutableList().also { it[idx] = it[idx].copy(value = newValue) }
                            } else {
                                detail.values + EnvironmentValue(key = key, value = newValue, enabled = true, type = "default")
                            }
                            detail.copy(values = mergedValues)
                        })
                    }
                }
                .onFailure { e ->
                    update { copy(statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun toggleEnvironmentValueEnabled(environmentUid: String, key: String) {
        val current = _state.value.comparisonDetails.find { it.uid == environmentUid } ?: return
        val existingIndex = current.values.indexOfFirst { it.key == key }
        if (existingIndex < 0) return
        val newEnabled = !current.values[existingIndex].enabled
        val newValues = current.values.toMutableList().also { it[existingIndex] = it[existingIndex].copy(enabled = newEnabled) }
        val updatedDetail = current.copy(values = newValues)
        scope.launch {
            service.updateEnvironment(updatedDetail)
                .onSuccess {
                    // Same merge-not-replace reasoning as updateEnvironmentValue above.
                    update {
                        copy(comparisonDetails = comparisonDetails.map { detail ->
                            if (detail.uid != environmentUid) return@map detail
                            val idx = detail.values.indexOfFirst { it.key == key }
                            if (idx < 0) return@map detail
                            val mergedValues = detail.values.toMutableList().also { it[idx] = it[idx].copy(enabled = newEnabled) }
                            detail.copy(values = mergedValues)
                        })
                    }
                }
                .onFailure { e ->
                    update { copy(statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun renameEnvironmentKey(oldKey: String, newKey: String) {
        if (newKey.isBlank() || newKey == oldKey) return
        val affected = _state.value.comparisonDetails.filter { detail -> detail.values.any { it.key == oldKey } }
        if (affected.isEmpty()) return

        val collision = affected.firstOrNull { detail -> detail.values.any { it.key == newKey } }
        if (collision != null) {
            update { copy(statusMessage = "Key already exists in ${collision.name}") }
            return
        }

        fanOutKeyUpdate(
            key = oldKey,
            operationName = "rename",
            buildUpdatedDetail = { detail ->
                val idx = detail.values.indexOfFirst { it.key == oldKey }
                detail.copy(values = detail.values.toMutableList().also { it[idx] = it[idx].copy(key = newKey) })
            },
            mergeIntoCurrent = { detail ->
                val idx = detail.values.indexOfFirst { it.key == oldKey }
                if (idx < 0) detail else detail.copy(values = detail.values.toMutableList().also { it[idx] = it[idx].copy(key = newKey) })
            }
        )
    }

    fun deleteEnvironmentKey(key: String) {
        fanOutKeyUpdate(
            key = key,
            operationName = "delete",
            buildUpdatedDetail = { detail -> detail.copy(values = detail.values.filterNot { it.key == key }) },
            mergeIntoCurrent = { detail -> detail.copy(values = detail.values.filterNot { it.key == key }) }
        )
    }

    // Shared by renameEnvironmentKey and deleteEnvironmentKey: find every environment
    // that currently has `key`, apply `buildUpdatedDetail` to each and PUT them all
    // concurrently. On full success, merge each affected environment's result into
    // whatever comparisonDetails is *now* (via mergeIntoCurrent), not a stale snapshot —
    // same reasoning as updateEnvironmentValue/toggleEnvironmentValueEnabled above. On
    // any failure, roll back every environment that did succeed by re-PUTting its
    // original (untransformed) detail, and never touch comparisonDetails.
    private fun fanOutKeyUpdate(
        key: String,
        operationName: String,
        buildUpdatedDetail: (EnvironmentDetail) -> EnvironmentDetail,
        mergeIntoCurrent: (EnvironmentDetail) -> EnvironmentDetail
    ) {
        val affected = _state.value.comparisonDetails.filter { detail -> detail.values.any { it.key == key } }
        if (affected.isEmpty()) return

        val transformed = affected.map { detail -> detail to buildUpdatedDetail(detail) }

        scope.launch {
            val results = transformed
                .map { (original, updated) -> Triple(original, updated, scope.async { service.updateEnvironment(updated) }) }
                .map { (original, updated, deferred) -> Triple(original, updated, deferred.await()) }

            val failed = results.filter { (_, _, result) -> result.isFailure }
            val succeeded = results.filter { (_, _, result) -> result.isSuccess }

            if (failed.isEmpty()) {
                val affectedUids = affected.map { it.uid }.toSet()
                update {
                    copy(comparisonDetails = comparisonDetails.map { detail ->
                        if (detail.uid !in affectedUids) detail else mergeIntoCurrent(detail)
                    })
                }
                return@launch
            }

            val rollbackResults = succeeded
                .map { (original, _, _) -> scope.async { service.updateEnvironment(original) } }
                .awaitAll()

            val firstFailureMessage = failed.first().third.exceptionOrNull()?.message
            if (rollbackResults.any { it.isFailure }) {
                update { copy(statusMessage = "Error: $operationName failed and rollback also failed — verify these environments directly in Postman") }
            } else {
                update { copy(statusMessage = "Error: $firstFailureMessage") }
            }
        }
    }
}
