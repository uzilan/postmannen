package postmannen.model

sealed class CollectionNode {
    abstract val name: String

    data class Folder(override val name: String, val children: List<CollectionNode>) : CollectionNode()
    data class RequestItem(override val name: String) : CollectionNode()
}
