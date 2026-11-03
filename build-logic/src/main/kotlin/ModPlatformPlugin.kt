import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.expand
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import javax.inject.Inject

abstract class ModPlatformPlugin @Inject constructor() : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		val inferredLoader = Loader.of(project.buildFile.name.substringAfter('.').replace(".gradle.kts", ""))

		val extension = extensions.create("platform", ModPlatformExtension::class.java).apply {
			loader.convention(inferredLoader.id)
		}

		when (inferredLoader) {
			is Loader.Fabric -> {
				extension.jarTask.convention(providers.provider {
					extensions.getByType<dev.kikugie.loomx.LoomCompatProjectExtension>().modJar.name
				})
				extension.sourcesJarTask.convention(providers.provider {
					extensions.getByType<dev.kikugie.loomx.LoomCompatProjectExtension>().modSourcesJar.name
				})
			}

			is Loader.Forge -> {
				extension.jarTask.convention("reobfJar")
				extension.sourcesJarTask.convention("sourcesJar")
			}

			else -> {
				extension.jarTask.convention("jar")
				extension.sourcesJarTask.convention("sourcesJar")
			}
		}

		// [LogFilter 迁移] 纯 Java 模组: 只保留发布插件 (kotlin/ksp/fletching 已按需移除)
		apply(plugin = "me.modmuss50.mod-publish-plugin")

		val (version, loader) = name.split('-', limit = 2)
		project.sc.properties.tags(version, loader)

		val ctx = Context(
			project = this,
			extension = extension,
			loader = Loader.of(extension.loader.get()),
			stonecutter = project.sc
		)
		configureProject(ctx)
	}

	private fun Project.configureProject(ctx: Context) {
		version = ctx.fullVersion

		listOf("java", "idea").forEach { apply(plugin = it) }
		ctx.extension.requiredJava.set(ctx.javaVersion)

		if (ctx.loader.isFabricLike) {
			ctx.extension.dependencies {
				required("java") { fabricLikeVersionRange = ">=${ctx.javaVersion.majorVersion}" }
			}
		}

		registerGenerateManifestTask(ctx)
		configureJarTask(ctx)
		configureIdea()
		configureProcessResources(ctx)
		configureJava(ctx)
		registerBuildAndCollectTask(ctx)

		configureModPublishing(ctx)
		// [LogFilter 迁移] Maven Central 发布链路已砍除: 不发 Maven, 见 GOAL-PLAN v5
	}

	private fun Project.configureJava(ctx: Context) {
		extensions.configure<JavaPluginExtension>("java") {
			withSourcesJar()
			withJavadocJar()
			sourceCompatibility = ctx.javaVersion
			targetCompatibility = ctx.javaVersion
			toolchain {
				languageVersion.set(JavaLanguageVersion.of(ctx.javaVersion.majorVersion))
			}
		}
		// [2026-11-03 构建修复] 源码为 UTF-8(中文注释): javac 走 daemon(JDK25, JEP400 默认 UTF-8)侥幸通过,
		// javadoc.exe 由 toolchain(JDK17) fork, 中文 Windows 默认 GBK 读 UTF-8 报 100 个不可映射字符;
		// 根治: 编译与 javadoc 显式声明源编码, 输出侧同步钉 UTF-8, 不依赖任何平台默认
		tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
		tasks.withType<Javadoc>().configureEach {
			options.encoding = "UTF-8"
			(options as? StandardJavadocDocletOptions)?.apply {
				charSet = "UTF-8"
				docEncoding = "UTF-8"
			}
		}
	}

	private fun Project.registerGenerateManifestTask(ctx: Context) {
		val manifestOutputDir = layout.buildDirectory.dir("generated/modManifest")
		abstract class GenerateModManifestTask : DefaultTask() {
			@get:Input
			abstract val content: Property<String>

			@get:OutputFile
			abstract val outputFile: RegularFileProperty

			@TaskAction
			fun generate() {
				val file = outputFile.get().asFile
				file.parentFile.mkdirs()
				file.writeText(content.get())
			}
		}
		val generateTask = tasks.register<GenerateModManifestTask>("generateModManifest") {
			content.set(ctx.loader.generateManifest(ctx))
			outputFile.set(layout.buildDirectory.file("generated/modManifest/${ctx.loader.modManifestPath}"))
		}

		the<JavaPluginExtension>().sourceSets.named("main") { resources.srcDir(manifestOutputDir) }
		tasks.named<ProcessResources>("processResources") { dependsOn(generateTask) }
	}

	private fun Project.configureProcessResources(ctx: Context) {
		val javaVersionValue = "JAVA_${ctx.javaVersion.majorVersion}"
		val excluded = ctx.loader.excludedResources
		tasks.named<ProcessResources>("processResources") {
			// [LogFilter 迁移] 无 Kotlin/KSP: 仅依赖 stonecutterGenerate
			dependsOn(tasks.named("stonecutterGenerate"))
			filesMatching("*.mixins.json") {
				expand("java" to javaVersionValue)
			}
			exclude(excluded)
		}
	}

	private fun Project.configureJarTask(ctx: Context) {
		val generateTask = tasks.named("generateModManifest")
		tasks.withType<Jar>().configureEach {
			archiveBaseName.set(ctx.modId)
			dependsOn(generateTask)
			// [LogFilter v6] MixinConfigs jar 强写已删: 本项目无 mixin; Loader.Forge.mixinConfigAttribute 联动移除 (见 Loader.kt)
		}
	}

	private fun Project.configureIdea() {
		extensions.configure<IdeaModel>("idea") {
			module {
				isDownloadJavadoc = true
				isDownloadSources = true
			}
		}
	}

	private fun Project.registerBuildAndCollectTask(ctx: Context) {
		tasks.register<Copy>("buildAndCollect") {
			from(
				tasks.named(ctx.extension.jarTask.get()),
				tasks.named(ctx.extension.sourcesJarTask.get()),
				tasks.named("javadocJar")
			)
			into(rootProject.layout.buildDirectory.file("libs/${ctx.basicVersion}"))
			dependsOn("build")
			group = "build"
		}
	}
}
