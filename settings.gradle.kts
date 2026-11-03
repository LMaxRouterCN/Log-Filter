// 根设置: 插件源 + Stonecutter 多版本节点声明 (5 节点矩阵)
// Root settings: plugin repositories + Stonecutter version node declarations.
pluginManagement {
	// [2026-11-03 本地构建修复] 仓库序: KikuGie 前置(stonecutter / loom-back-compat 插件的发行处),
	// 不可达的 NeoForged 垫底——本机网络对部分海外 maven 不稳定, 可靠源优先
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" } // 本地 DNS 污染不可达, 仅 CI 兜底
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

		// [LogFilter v6] 12 节点矩阵: fabric x5 / forge x3 / neoforge x4 (GOAL-PLAN v6)
		match("1.21.11", "fabric", "neoforge")
		match("1.21.1", "fabric", "neoforge")
		match("1.20.4", "neoforge")
		match("1.20.1", "fabric", "forge", "neoforge")
		match("1.19.2", "fabric", "forge")
		match("1.18.2", "fabric", "forge")

		vcsVersion = "1.20.1-fabric"
	}
}