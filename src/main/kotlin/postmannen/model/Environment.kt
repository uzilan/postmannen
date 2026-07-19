package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class Environment(val id: String, val name: String, val uid: String)
