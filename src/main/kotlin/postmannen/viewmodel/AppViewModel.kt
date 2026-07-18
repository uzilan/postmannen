package postmannen.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import postmannen.model.AppState
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
                    update { copy(workspaces = workspaces, loading = false, selectedWorkspaceIndex = 0) }
                    workspaces.firstOrNull()?.let { loadCollections(it.id) }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun selectWorkspace(index: Int) {
        val workspace = _state.value.workspaces.getOrNull(index) ?: return
        update { copy(selectedWorkspaceIndex = index) }
        loadCollections(workspace.id)
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
}
