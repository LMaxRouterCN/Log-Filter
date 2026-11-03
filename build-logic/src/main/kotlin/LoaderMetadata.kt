import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FabricManifest(
	val schemaVersion: Int = 1,
	val id: String,
	val name: String,
	val version: String,
	val authors: List<String>,
	val contributors: List<String>,
	val contact: Map<String, String>,
	val custom: JsonObject?,
	val description: String,
	val icon: String,
	val license: String,
	val environment: String,
	val entrypoints: Map<String, List<String>>,
	// [LogFilter 迁移] 无 AW / mixin: 对应字段已移除
	val depends: Map<String, String> = emptyMap(),
	val recommends: Map<String, String> = emptyMap(),
	val breaks: Map<String, String> = emptyMap(),
	val provides: List<String> = emptyList()
)

@Serializable
data class ForgeManifest(
	val modLoader: String = "javafml",
	val loaderVersion: String = "[2,)",
	val license: String,
	val issueTrackerURL: String,
	val clientSideOnly: Boolean,
	val mods: List<ForgeMod>,
	val dependencies: Map<String, List<ForgeDependency>> = emptyMap()
	// [LogFilter 迁移] 无 mixin / AT: 对应字段已移除
)

@Serializable
data class ForgeMod(
	val modId: String,
	val displayName: String,
	val version: String,
	val displayURL: String,
	val modUrl: String,
	val logoFile: String,
	val iconFile: String,
	val authors: String,
	val logoBlur: Boolean = false,
	val credits: String,
	val description: String
)

@Serializable
data class ForgeDependency(
	val modId: String,
	val side: String,
	val versionRange: String,
	val mandatory: Boolean,
	val type: String
)

// [LogFilter 迁移] Forge 侧 mixin/AT 数据类已删除 (本项目无 mixin 无 AT)
