package postmannen.model

data class AppState(
    val workspaces: List<Workspace> = emptyList(),
    val selectedWorkspaceIndex: Int = 0,
    val collections: List<Collection> = emptyList(),
    val environments: List<Environment> = emptyList(),
    val activeTab: Tab = Tab.COLLECTIONS,
    val loading: Boolean = false,
    val statusMessage: String = ""
)
