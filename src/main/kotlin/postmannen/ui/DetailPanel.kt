package postmannen.ui

import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import postmannen.model.CollectionVariable

sealed class DetailContent {
    object None : DetailContent()
    object Loading : DetailContent()
    data class Variables(val variables: List<CollectionVariable>) : DetailContent()
}

class DetailPanel : Panel(LinearLayout(Direction.VERTICAL)) {
    private var lastContent: DetailContent = DetailContent.None

    fun applyContent(content: DetailContent) {
        if (content == lastContent) return
        lastContent = content
        removeAllComponents()
        when (content) {
            is DetailContent.None -> {}
            is DetailContent.Loading -> addComponent(Label("Loading..."))
            is DetailContent.Variables -> {
                if (content.variables.isEmpty()) return
                addComponent(Label("Variables"))
                content.variables.forEach { addComponent(Label("${it.key} = ${it.value}")) }
            }
        }
    }
}
