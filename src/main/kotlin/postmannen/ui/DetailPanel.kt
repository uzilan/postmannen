package postmannen.ui

import com.googlecode.lanterna.gui2.Border
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.input.KeyStroke
import postmannen.model.CollectionVariable
import postmannen.model.EnvironmentDetail

sealed class DetailContent {
    object None : DetailContent()
    object Loading : DetailContent()
    data class CollectionVariables(val variables: List<CollectionVariable>) : DetailContent()
    data class Environments(val details: List<EnvironmentDetail>) : DetailContent()
}

fun DetailContent.titleFor(): String = when (this) {
    is DetailContent.None -> "Details"
    is DetailContent.Loading -> "Details"
    is DetailContent.CollectionVariables -> "Variables"
    is DetailContent.Environments -> "Environments"
}

// Border's title is a private final field on the package-private StandardBorder
// superclass with no public setter — Lanterna gives no extension point for
// changing a border's title after construction, same class of gap as
// WorkspaceDropdown's popup-listbox reflection.
fun Border.setTitle(newTitle: String) {
    try {
        val field = javaClass.superclass.getDeclaredField("title")
        field.isAccessible = true
        field.set(this, newTitle)
    } catch (_: Exception) {}
}

class DetailPanel(
    private val gui: MultiWindowTextGUI,
    private val onValueChanged: (environmentUid: String, key: String, newValue: String) -> Unit,
    private val onEnabledToggled: (environmentUid: String, key: String) -> Unit,
    private val onKeyRenamed: (oldKey: String, newKey: String) -> Unit,
    private val onKeyDeleted: (key: String) -> Unit,
    private val onChatFocusRequested: () -> Unit
) : Panel(LinearLayout(Direction.VERTICAL)) {
    private var lastContent: DetailContent = DetailContent.None
    private var environmentGrid: EnvironmentGridPanel? = null
    private var lastEnvironmentUids: Set<String> = emptySet()

    val gridIsFocusable: Boolean get() = environmentGrid?.let { !it.isEmpty } ?: false

    fun focusGrid() {
        environmentGrid?.takeFocus()
    }

    fun handleAddDeleteShortcut(key: KeyStroke): Interactable.Result? = environmentGrid?.handleAddDeleteShortcut(key)

    // Returns true only when the environment grid was rebuilt from scratch (the
    // uid set changed) rather than patched in place — App.kt uses this to know
    // whether it must explicitly move focus back into the grid: a freshly built
    // Panel has no focus of its own (Lanterna doesn't auto-focus a child added
    // to an already-open window, unlike opening a brand new popup window), while
    // a patched grid already holds whatever focus it had before the edit that
    // triggered this call — forcibly refocusing on every patch would fight the
    // user's natural in-grid Tab navigation while editing.
    fun applyContent(content: DetailContent): Boolean {
        if (content is DetailContent.Environments) {
            val uids = content.details.map { it.uid }.toSet()
            lastContent = content
            if (uids == lastEnvironmentUids) {
                environmentGrid?.applyDetails(content.details)
                return false
            }
            removeAllComponents()
            val grid = EnvironmentGridPanel(
                gui = gui,
                initialDetails = content.details,
                onValueChanged = onValueChanged,
                onEnabledToggled = onEnabledToggled,
                onKeyRenamed = onKeyRenamed,
                onKeyDeleted = onKeyDeleted,
                onChatFocusRequested = onChatFocusRequested
            )
            environmentGrid = grid
            lastEnvironmentUids = uids
            addComponent(grid)
            return true
        }

        environmentGrid = null
        lastEnvironmentUids = emptySet()
        if (content == lastContent) return false
        lastContent = content
        removeAllComponents()
        when (content) {
            is DetailContent.None -> addComponent(Label(""))
            is DetailContent.Loading -> addComponent(Label("Loading..."))
            is DetailContent.CollectionVariables -> {
                content.variables.forEach { addComponent(Label("${it.key} = ${it.value}")) }
            }
            is DetailContent.Environments -> Unit // unreachable, handled above
        }
        return false
    }
}
