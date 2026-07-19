package postmannen.ui

import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import postmannen.model.CollectionVariable

class DetailPanel : Panel(LinearLayout(Direction.VERTICAL)) {
    private var lastVariables: List<CollectionVariable> = emptyList()

    fun applyVariables(variables: List<CollectionVariable>) {
        if (variables == lastVariables) return
        lastVariables = variables
        removeAllComponents()
        variables.forEach { addComponent(Label("${it.key} = ${it.value}")) }
    }
}
