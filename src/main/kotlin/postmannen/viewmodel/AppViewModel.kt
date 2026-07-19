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
                    update { copy(collections = collections, loading = false) }
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

        val renamedDetails = affected.map { detail ->
            val idx = detail.values.indexOfFirst { it.key == oldKey }
            detail to detail.copy(values = detail.values.toMutableList().also { it[idx] = it[idx].copy(key = newKey) })
        }

        scope.launch {
            val renameResults = renamedDetails
                .map { (original, renamed) -> Triple(original, renamed, scope.async { service.updateEnvironment(renamed) }) }
                .map { (original, renamed, deferred) -> Triple(original, renamed, deferred.await()) }

            val failed = renameResults.filter { (_, _, result) -> result.isFailure }
            val succeeded = renameResults.filter { (_, _, result) -> result.isSuccess }

            if (failed.isEmpty()) {
                val affectedUids = affected.map { it.uid }.toSet()
                update {
                    copy(comparisonDetails = comparisonDetails.map { detail ->
                        if (detail.uid !in affectedUids) return@map detail
                        val idx = detail.values.indexOfFirst { it.key == oldKey }
                        if (idx < 0) return@map detail
                        val mergedValues = detail.values.toMutableList().also { it[idx] = it[idx].copy(key = newKey) }
                        detail.copy(values = mergedValues)
                    })
                }
                return@launch
            }

            val rollbackResults = succeeded
                .map { (original, _, _) -> scope.async { service.updateEnvironment(original) } }
                .awaitAll()

            val firstFailureMessage = failed.first().third.exceptionOrNull()?.message
            if (rollbackResults.any { it.isFailure }) {
                update { copy(statusMessage = "Error: rename failed and rollback also failed — verify these environments directly in Postman") }
            } else {
                update { copy(statusMessage = "Error: $firstFailureMessage") }
            }
        }
    }
}
