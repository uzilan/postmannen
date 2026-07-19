package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class Workspace(val id: String, val name: String, val type: String) {
    override fun toString(): String = "$name ($type)"
}
