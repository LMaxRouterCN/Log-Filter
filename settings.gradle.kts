// 根设置: 插件源 + Stonecutter 多版本节点声明 (5 节点矩阵)
// Root settings: plugin repositories + Stonecutter version node declarations.
pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" } // 本地 DNS 污染不可达, 仅 CI/快速失败兜底用
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	// Keep in sync with the `stonecutter` version in gradle/libs.versions.toml!
	id("dev.kikugie.stonecutter") version "0.10-beta.1"
	id("dev.kikugie.loom-back-compat") version "0.4.1"
}

stonecutter {
	create(rootProject) {
		fun match(version: String, vararg loaders: String) =
			loaders.forEach { version("$version-$it", version).buildscript = "build.$it.gradle.kts" }

		match("1.21.11", "fabric", "neoforge")
		match("1.21.1", "fabric", "neoforge")
		match("1.20.1", "fabric")

		vcsVersion = "1.20.1-fabric"
	}
}