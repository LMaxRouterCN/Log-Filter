plugins {
	`kotlin-dsl`
	kotlin("plugin.serialization") version embeddedKotlinVersion
}

gradlePlugin {
	plugins {
		register("modPlatform") {
			id = "mod-platform"
			implementationClass = "ModPlatformPlugin"
		}
		register("modRoot") {
			id = "mod-root"
			implementationClass = "ModRootPlugin"
		}
	}
}

repositories {
	// [2026-11-03 本地构建修复] 仓库序: 可达源前置; NeoForged/Jitpack 本地不可达垫底, CI 全通不受序影响
	mavenCentral()
	gradlePluginPortal()
	maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
	maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
	maven("https://maven.fabricmc.net/") { name = "Fabric" }
	maven("https://maven.neoforged.net/releases/") { name = "NeoForged" } // 本地 DNS 污染不可达, 仅 CI 兜底
	maven("https://jitpack.io") { name = "Jitpack" } // 本地不可达, 仅 CI 兜底
}

dependencies {
	// [LogFilter 迁移] 裁剪: postprocess / fletching / maven-central 依赖已砍 (纯 Java, 不发 Maven Central)
	implementation(libs.kikugie.stonecutter)
	implementation(libs.kikugie.loomx)
	implementation(libs.mod.publish.plugin)
	implementation(libs.foojay.resolver)
	implementation(libs.serialization.json)
	implementation(libs.serialization.toml)
}
