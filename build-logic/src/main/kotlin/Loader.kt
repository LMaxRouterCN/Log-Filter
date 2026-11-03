@file:Suppress("unused")

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.modmuss50.mpp.platforms.modrinth.ModrinthEnvironment
import org.gradle.api.NamedDomainObjectContainer
import java.util.*

sealed class Loader(val id: String) {
	abstract val modManifestPath: String
	abstract val excludedResources: List<String>

	open val isFabricLike: Boolean = false

	abstract fun generateManifest(ctx: Context): String

	object Fabric : Loader("fabric") {
		override val isFabricLike = true
		override val modManifestPath = "fabric.mod.json"
		// [LogFilter 迁移] 无 AT: 砍 "aw/*.cfg" 死排除规则
		override val excludedResources = listOf(
			"META-INF/mods.toml", "META-INF/neoforge.mods.toml", ".cache", "pack.mcmeta"
		)

		override fun generateManifest(ctx: Context): String {
			val manifest = FabricManifest(
				id = ctx.modId,
				name = ctx.modName,
				version = ctx.baseVersion,
				authors = ctx.authors,
				contributors = ctx.contributors,
				contact = mapOf(
					"sources" to ctx.sourcesUrl, "issues" to ctx.issuesUrl, "homepage" to ctx.homepageUrl
				),
				custom = ctx.discordUrl.takeIf { it.isNotEmpty() }?.let { url ->
					buildJsonObject {
						putJsonObject("modmenu") {
							putJsonObject("links") {
								put("modmenu.discord", url)
							}
						}
					}
				},
				description = ctx.description,
				icon = ctx.icon, // [LogFilter 迁移] 图标路径可配置 (mod.icon, 本体为 pack.png)
				license = ctx.licenseName,
				environment = when (ctx.environment) {
					ModrinthEnvironment.CLIENT_ONLY, ModrinthEnvironment.SINGLEPLAYER_ONLY -> "client"

					ModrinthEnvironment.DEDICATED_SERVER_ONLY -> "server"

					ModrinthEnvironment.SERVER_ONLY, ModrinthEnvironment.SERVER_ONLY_CLIENT_OPTIONAL,
					ModrinthEnvironment.CLIENT_ONLY_SERVER_OPTIONAL, ModrinthEnvironment.CLIENT_AND_SERVER,
					ModrinthEnvironment.CLIENT_OR_SERVER_PREFERS_BOTH, ModrinthEnvironment.CLIENT_OR_SERVER -> "*"
				},
				// [LogFilter 迁移] 无 AW / datagen 入口 / mixin: entrypoints 只留 main+client
				entrypoints = mapOf( // [2026-11-03 构建修复] 本项目包根 = modGroup(无 modId 段), 类在 com.lmaxrouter.logfilter.platform.fabric; 原模板拼法 group.modid 适配的是 com.example.modtemplate 型结构, 导致 ClassNotFound("com.lmaxrouter.logfilter.logfilter...")
					"main" to listOf("${ctx.modGroup}.platform.fabric.FabricEntrypoint"),
					"client" to listOf("${ctx.modGroup}.platform.fabric.FabricClientEntrypoint")
				),
				depends = ctx.extension.dependencies.required.associate { it.modid.get() to it.fabricLikeVersionRange.get() },
				recommends = ctx.extension.dependencies.optional.associate { it.modid.get() to it.fabricLikeVersionRange.get() },
				breaks = ctx.extension.dependencies.incompatible.associate { it.modid.get() to it.fabricLikeVersionRange.get() },
				provides = ctx.extension.dependencies.embeds.map { it.modid.get() }
			)
			return JSON.encodeToString(manifest)
		}
	}

	sealed class ForgeLike(id: String) : Loader(id) {
		// [LogFilter 迁移] 无 AW: 砍 "aw/*.accesswidener" 死排除规则
		override val excludedResources = listOf(
			"fabric.mod.json", ".cache"
		)

		override fun generateManifest(ctx: Context): String {
			val forgeDeps = mutableListOf<ForgeDependency>()

			fun addDeps(container: NamedDomainObjectContainer<Dependency>, type: String) {
				container.forEach {
					forgeDeps.add(
						ForgeDependency(
							modId = it.modid.get(),
							side = it.environment.get().uppercase(Locale.ROOT),
							versionRange = it.forgeLikeVersionRange.get(),
							mandatory = type == "required",
							type = type
						)
					)
				}
			}

			addDeps(ctx.extension.dependencies.required, "required")
			addDeps(ctx.extension.dependencies.optional, "optional")
			addDeps(ctx.extension.dependencies.incompatible, "incompatible")

			val manifest = ForgeManifest(
				license = ctx.licenseName,
				issueTrackerURL = ctx.issuesUrl,
				clientSideOnly = !ctx.environmentPhysicalServer,
				mods = listOf(
					ForgeMod(
						modId = ctx.modId,
						displayName = ctx.modName,
						version = ctx.baseVersion,
						displayURL = ctx.homepageUrl,
						modUrl = ctx.homepageUrl,
						logoFile = ctx.icon,
						iconFile = ctx.icon, // NeoForge 26.2 +; [LogFilter 迁移] 图标路径可配置
						authors = ctx.authors.joinToString(", "),
						credits = "${ctx.authors.joinToString(", ")} Contributors: ${ctx.contributors.joinToString(", ")}",
						description = ctx.description
					)
				),
				dependencies = mapOf(ctx.modId to forgeDeps)
				// [LogFilter 迁移] 无 mixin 配置 / AT: 字段移除
			)

			return TOML.encodeToString(manifest)
		}
	}

	object NeoForge : ForgeLike("neoforge") {
		override val modManifestPath = "META-INF/neoforge.mods.toml"
		override val excludedResources = (super.excludedResources + "META-INF/mods.toml") + "pack.mcmeta"
	}

	object Forge : ForgeLike("forge") {
		override val modManifestPath = "META-INF/mods.toml"
		override val excludedResources = super.excludedResources + "META-INF/neoforge.mods.toml"
		val mixinConfigAttribute = "MixinConfigs"
	}

	companion object {
		fun of(id: String): Loader = when (id) {
			"fabric" -> Fabric
			"neoforge" -> NeoForge
			"forge" -> Forge
			else -> error("Unknown loader: '$id'")
		}
	}
}
