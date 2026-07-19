package postmannen.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.ChatContext
import postmannen.model.Collection
import postmannen.model.Tab
import postmannen.model.Workspace
import postmannen.viewmodel.AppViewModel
import postmannen.viewmodel.ChatViewModel
import java.util.concurrent.atomic.AtomicBoolean

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val chatViewModel: ChatViewModel,
    private val scope: CoroutineScope
) {
    private val workspaceDropdown = WorkspaceDropdown()
    private val tabbedListPanel = TabbedListPanel()
    private val detailPanel = DetailPanel(
        gui = gui,
        onValueChanged = { uid, key, newValue -> viewModel.updateEnvironmentValue(uid, key, newValue) },
        onEnabledToggled = { uid, key -> viewModel.toggleEnvironmentValueEnabled(uid, key) },
        onKeyRenamed = { oldKey, newKey -> viewModel.renameEnvironmentKey(oldKey, newKey) },
        onKeyDeleted = { key -> viewModel.deleteEnvironmentKey(key) },
        onChatFocusRequested = { chatPanel.focusInput(); chatFocused = true; gridFocused = false }
    )
    private val detailPanelBordered = detailPanel.withBorder(Borders.singleLine())
    private val chatPanel = ChatPanel(onSubmit = { text -> chatViewModel.sendMessage(text, buildChatContext()) }).apply {
        preferredSize = TerminalSize(30, preferredSize.rows)
    }
    private val chatPanelBordered = chatPanel.withBorder(Borders.singleLine())
    private val centerLayout = ThreeColumnLayout()
    private val centerPanel = Panel(centerLayout)
    private val statusBar = StatusBar()
    private val hintLabel = Label("")
    private val window = BasicWindow("postmannen")
    private var namePromptWindow: NamePromptOverlay? = null
    private var focusPendingForWorkspaceIndex: Int? = null
    private var collectionsBeforeSwitch: List<Collection> = emptyList()
    private var gridFocused = false
    private var chatFocused = false

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        val topPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        topPanel.addComponent(Label("Workspace:"))
        topPanel.addComponent(workspaceDropdown)
        topPanel.addComponent(Label("  "))
        topPanel.addComponent(tabbedListPanel.tabBar)
        root.addComponent(topPanel, BorderLayout.Location.TOP)
        centerPanel.addComponent(tabbedListPanel.withBorder(Borders.singleLine()))
        centerPanel.addComponent(ColumnSplitter(centerLayout, splitterIndex = 0))
        centerPanel.addComponent(detailPanelBordered)
        centerPanel.addComponent(ColumnSplitter(centerLayout, splitterIndex = 1))
        centerPanel.addComponent(chatPanelBordered)
        root.addComponent(centerPanel, BorderLayout.Location.CENTER)

        val bottomPanel = Panel(LinearLayout(Direction.VERTICAL))
        bottomPanel.addComponent(statusBar)
        bottomPanel.addComponent(hintLabel)
        root.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                collectionsBeforeSwitch = viewModel.state.value.collections
                viewModel.selectWorkspace(selectedIndex)
                focusPendingForWorkspaceIndex = selectedIndex
                gridFocused = false
            }
        }

        tabbedListPanel.onSpaceKey = {
            val state = viewModel.state.value
            if (state.activeTab == Tab.ENVIRONMENTS) {
                state.environments.getOrNull(tabbedListPanel.selectedIndex)?.let {
                    viewModel.toggleEnvironmentSelection(it.id)
                }
            }
        }

        tabbedListPanel.onEnterKey = {
            if (viewModel.state.value.activeTab == Tab.COLLECTIONS) {
                tabbedListPanel.selectedNodeId?.let { viewModel.toggleNodeCollapsed(it) }
            }
        }

        tabbedListPanel.onTabKey = {
            if (viewModel.state.value.activeTab == Tab.ENVIRONMENTS && detailPanel.gridIsFocusable) {
                detailPanel.focusGrid()
                gridFocused = true
            }
        }

        tabbedListPanel.onSelectionMaybeChanged = { refreshDetailPanel() }

        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                when {
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'q' -> {
                        window.close()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'r' -> {
                        viewModel.refreshWorkspace()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Escape && (gridFocused || chatFocused) -> {
                        tabbedListPanel.focusList()
                        gridFocused = false
                        chatFocused = false
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'k' && keyStroke.isCtrlDown -> {
                        chatPanel.focusInput()
                        chatFocused = true
                        gridFocused = false
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.ArrowLeft || keyStroke.keyType == KeyType.ArrowRight -> {
                        val next = if (viewModel.state.value.activeTab == Tab.COLLECTIONS) Tab.ENVIRONMENTS else Tab.COLLECTIONS
                        viewModel.setActiveTab(next)
                        gridFocused = false
                        chatFocused = false
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'n' && keyStroke.isCtrlDown -> {
                        if (gridFocused) {
                            detailPanel.handleAddDeleteShortcut(keyStroke)
                        } else if (viewModel.state.value.activeTab == Tab.ENVIRONMENTS && namePromptWindow == null) {
                            openNamePrompt()
                        }
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'd' && keyStroke.isCtrlDown && gridFocused -> {
                        detailPanel.handleAddDeleteShortcut(keyStroke)
                        hasBeenHandled.set(true)
                    }
                }
            }
        })

        scope.launch {
            viewModel.state.collect { state ->
                synchronized(gui) {
                    applyingState = true
                    applyState(state)
                    applyingState = false
                    try { gui.updateScreen() } catch (_: Exception) {}
                }
            }
        }

        scope.launch {
            chatViewModel.state.collect { chatState ->
                synchronized(gui) {
                    chatPanel.applyState(chatState)
                    try { gui.updateScreen() } catch (_: Exception) {}
                }
            }
        }

        gui.addWindowAndWait(window)
        chatViewModel.close()
    }

    private var lastWorkspaces: List<Workspace> = emptyList()

    private fun openNamePrompt() {
        val win = NamePromptOverlay(
            onSubmit = { name ->
                namePromptWindow?.close()
                namePromptWindow = null
                viewModel.createEnvironment(name)
            },
            onCancel = {
                namePromptWindow?.close()
                namePromptWindow = null
            }
        )
        namePromptWindow = win
        gui.addWindow(win)
    }

    private fun refreshDetailPanel() {
        val state = viewModel.state.value
        val content: DetailContent
        if (state.activeTab == Tab.COLLECTIONS) {
            val highlightedId = tabbedListPanel.selectedNodeId
            val collection = state.collections.firstOrNull { it.uid == highlightedId }
            content = if (collection == null) {
                DetailContent.None
            } else {
                val detail = state.collectionDetails.firstOrNull { it.uid == collection.uid }
                if (detail == null) DetailContent.Loading else DetailContent.CollectionVariables(detail.variables)
            }
        } else {
            val highlightedId = state.environments.getOrNull(tabbedListPanel.selectedIndex)?.id
            viewModel.refreshEnvironmentPanel(highlightedId)
            content = if (state.environmentPanelDetails.isEmpty()) DetailContent.None else DetailContent.Environments(state.environmentPanelDetails)
        }
        val rebuiltGrid = detailPanel.applyContent(content)
        if (gridFocused && !detailPanel.gridIsFocusable) {
            tabbedListPanel.focusList()
            gridFocused = false
        } else if (rebuiltGrid && gridFocused) {
            detailPanel.focusGrid()
        }

    }

    private fun buildChatContext(): ChatContext {
        val state = viewModel.state.value
        val workspace = state.workspaces.getOrNull(state.selectedWorkspaceIndex)
        val highlightedLabel = if (state.activeTab == Tab.COLLECTIONS) {
            val id = tabbedListPanel.selectedNodeId
            state.collections.firstOrNull { it.uid == id }?.let { "collection: ${it.name}" }
        } else {
            state.environments.getOrNull(tabbedListPanel.selectedIndex)?.let { "environment: ${it.name}" }
        }
        return ChatContext(
            workspaceName = workspace?.name,
            workspaceId = workspace?.id,
            highlightedLabel = highlightedLabel
        )
    }

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        tabbedListPanel.applyState(state)
        refreshDetailPanel()

        // Deferred from the workspace-switch dropdown listener: collections for the
        // new workspace load asynchronously, so we can't decide "is there anything to
        // focus" synchronously at click time. Wait until the collections list actually
        // changes (fetch landed, success or failure) for the workspace we're waiting
        // on, then move focus into the list only if it ended up non-empty — otherwise
        // leave focus on the dropdown, since there's nothing useful to highlight.
        val pendingIndex = focusPendingForWorkspaceIndex
        if (pendingIndex != null && pendingIndex == state.selectedWorkspaceIndex && state.collections != collectionsBeforeSwitch) {
            focusPendingForWorkspaceIndex = null
            if (state.collections.isNotEmpty()) {
                tabbedListPanel.focusList()
            }
        }

        hintLabel.text = when {
            gridFocused -> "  [esc] back to list  ^N add key  ^D delete key"
            state.activeTab == Tab.ENVIRONMENTS -> "  [space] select  [tab] edit  ^N new  [←][→] tabs  ^K chat  r-refresh  q-quit"
            state.activeTab == Tab.COLLECTIONS -> "  [enter] expand/collapse  [←][→] tabs  ^K chat  r-refresh  q-quit"
            else -> "  [←][→] tabs  ^K chat  r-refresh  q-quit"
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)
    }
}
