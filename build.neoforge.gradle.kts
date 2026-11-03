// NeoForge 节点构建脚本 (相对模板手术: 砍 AT / validateAT / datagen / parchment)
// NeoForge node build script (pared from template: no access transformers, no datagen, no parchment).
// 本地不构建此节点 (maven.neoforged.net DNS 污染), 仅 CI 构建 (见 GOAL-PLAN 网络事实)
plugins {
	id("mod-platform")
	id("net.neoforged.moddev")
}

stonecutter {
	// 1.21.11+ mojmap 类名漂移: ResourceLocation -> Identifier
	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
		replace("location()", "identifier()")
	}
	// [2026-11-03 构建修复] active 节点裸编共享根 src, //? 门控注释对 javac 不可见,
	// 需把对端 loader 的平台源整个排除出编译(DisabledSourceSpec 路径排除, 相对 src/main);
	// 非 active 节点编译 generated 剥离副本, 行为不变
	// [LogFilter v6] 三 loader 并列: 排除另外两家平台源 (active 节点裸编共享根 src, //? 门控对 javac 不可见)
	files.disable(true, "**/platform/fabric/**", "**/platform/forge/**")
}

platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("neoforge") {
			forgeLikeVersionRange.set("[${prop("deps.neoforge")},)")
		}
	}
}

neoForge {
	version = prop("deps.neoforge")

	runs {
		val devJvmArgs = propsList("mod", "dev_jvm_args")
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "NeoForge Client (${stonecutter.current.version})"
			programArgument("--username=Dev")
			jvmArguments.addAll(devJvmArgs)
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "NeoForge Server (${stonecutter.current.version})"
			jvmArguments.addAll(devJvmArgs)
		}
	}

	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// NightConfig: 编译期需要(源码 import), 运行期由 NeoForge 发行版自带(FML 配置层即构建于其上), 仅 compileOnly 不嵌入
	compileOnly("com.electronwill.night-config:core:${prop("deps.nightconfig")}")
	compileOnly("com.electronwill.night-config:toml:${prop("deps.nightconfig")}")
}

// moddev 的产物任务必须晚于 stonecutter 源码生成 (节点源码先切版本再编译)
tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}
