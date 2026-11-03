// Fabric 节点构建脚本 (相对模板手术: 砍 AW / datagen / modmenu / mixin-debug / parchment)
// Fabric node build script (pared from template: no AW, no datagen, no modmenu, no mixin debug args).
plugins {
	id("mod-platform")
	id("dev.kikugie.loom-back-compat")
}

stonecutter {
	// 1.21.11+ mojmap 类名漂移: ResourceLocation -> Identifier (见 GOAL-PLAN 风险表)
	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
		replace("location()", "identifier()")
	}
	// [2026-11-03 构建修复] active 节点裸编共享根 src, //? 门控注释对 javac 不可见,
	// 需把对端 loader 的平台源整个排除出编译(DisabledSourceSpec 路径排除, 相对 src/main);
	// 非 active 节点编译 generated 剥离副本, 行为不变
	// [LogFilter v6] 三 loader 并列: 排除另外两家平台源 (active 节点裸编共享根 src, //? 门控对 javac 不可见)
	files.disable(true, "**/platform/neoforge/**", "**/platform/forge/**")
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			fabricLikeVersionRange = prop("deps.minecraft")
		}
		required("fabric-api") {
			slug("fabric-api")
			fabricLikeVersionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			fabricLikeVersionRange = ">=${prop("deps.fabric-loader")}"
		}
	}
}

loom {
	val devJvmArgs = propsList("mod", "dev_jvm_args")
	runs.named("client") {
		client()
		generateRunConfig.set(true)
		runDirectory.set(layout.projectDirectory.dir("run"))
		programArguments.add("--username=Dev")
		jvmArguments.addAll(devJvmArgs)
		displayName.set("Fabric Client")
	}
	runs.named("server") {
		server()
		generateRunConfig.set(true)
		runDirectory.set(layout.projectDirectory.dir("run"))
		jvmArguments.addAll(devJvmArgs)
		displayName.set("Fabric Server")
	}
}

repositories {
	// NightConfig (core + toml) 在 Maven Central, 无需其他源
	mavenCentral()
}

configurations.all {
	resolutionStrategy {
		force("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	mappings(loom.layered {
		officialMojangMappings()
	})
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	// NightConfig: TOML 配置读取 (Apache-2.0), jar-in-jar 嵌入产物 (见 GOAL-PLAN 待拍板 1)
	implementation(include("com.electronwill.night-config:core:${prop("deps.nightconfig")}")!!)
	implementation(include("com.electronwill.night-config:toml:${prop("deps.nightconfig")}")!!)
}
