package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class EnvironmentValue(val key: String, val value: String, val enabled: Boolean, val type: String)
