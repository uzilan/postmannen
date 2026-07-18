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
}
