// 根构建脚本: mod-root 插件装配 + stonecutter 参数 (swaps 占位符 + loader 常量)
// Root build script: applies mod-root plugin and declares stonecutter swaps/loader constants.
plugins {
	alias(libs.plugins.mod.publish.plugin)
	alias(libs.plugins.loom.back.compat).apply(false)
	alias(libs.plugins.neoforged.moddev).apply(false)
	id("mod-root")
}

stonecutter active file(".sc_active_version")

stonecutter parameters {
	// loader 常量: 节点源码里 //? fabric { } / //? neoforge { } 块的开关依据
	// Loader constants: toggles for //? fabric { } / //? neoforge { } blocks in shared sources.
	constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")
	// 代码占位符: Constants.java 中 /*$ xxx */ 后的字面量在生成时被替换为这些值
	// Code placeholders: literals after /*$ xxx */ markers in Constants.java are swapped at generation time.
	swaps["mod_version"] = "\"${properties.getAs<String>("mod.version")}\";"
	swaps["mod_id"] = "\"${properties.getAs<String>("mod.id")}\";"
	swaps["mod_name"] = "\"${properties.getAs<String>("mod.name")}\";"
	swaps["mod_group"] = "\"${properties.getAs<String>("mod.group")}\";"
	swaps["minecraft"] = "\"${current.version}\";"
}