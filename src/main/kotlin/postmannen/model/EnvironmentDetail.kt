package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class EnvironmentDetail(val id: String, val uid: String, val name: String, val values: List<EnvironmentValue>)
