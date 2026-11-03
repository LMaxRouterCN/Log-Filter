// Forge 节点构建脚本 (相对模板手术: 砍 AT / validateAT / mixin / datagen / Modrinth 仓库)
// Forge node build script (pared from template: no access transformers, no mixin, no datagen, no Modrinth repo).
// 本地不构建此节点 (legacyforge 插件 marker 仅在 maven.neoforged.net, 本地 DNS 污染), 仅 CI 构建 (见 GOAL-PLAN 网络事实)
plugins {
	id("mod-platform")
	id("net.neoforged.moddev.legacyforge")
}

stonecutter {
	// [2026-11-03 构建修复] active 节点裸编共享根 src, //? 门控注释对 javac 不可见,
	// 需把对端 loader 的平台源整个排除出编译(DisabledSourceSpec 路径排除, 相对 src/main);
	// 非 active 节点编译 generated 剥离副本, 行为不变
	// [LogFilter v6] 三 loader 并列: 排除另外两家平台源
	files.disable(true, "**/platform/fabric/**", "**/platform/neoforge/**")
	// [v6 事件类名分叉] jar 实证: 1.18.2 (40.3.12) = ClientPlayerNetworkEvent.LoggedInEvent;
	// 1.19.2 (43.5.2) 与 1.20.1 (v1 实证) = LoggingIn。源码基准写 LoggingIn,
	// 1.18.2 节点编译期文本替换为 LoggedInEvent (方法名/注释里的子串同被换, 无害)
	replacements.string(current.parsed < "1.19") {
	replace("LoggingIn", "LoggedInEvent")
	// [CI 41702839086 判决] 1.18.2 无 Component.literal (1.19.2-fabric 编译通过实证分界线=1.19); 全限定名构造器免 import 手术
	replace("Component.literal(", "new net.minecraft.network.chat.TextComponent(")
	}
	// [CI 41715154075 判决] 1.20.1-forge 的 Builder 同非泛型 (1.19.2-forge 泛型; 泛型性无版本单调性,
	// 各 userdev 钉的 log4j 独立); raw type 全版本兼容 (非泛型=正常写法, 泛型=raw 合法仅 unchecked 警告)
	// [CI 41708614939 实证] 2.11.2+ 全泛型 -> 1.18.2/1.20.1-forge 钉 ≤2.11.0 形态; 2.8.2 成员全套 javap 实证 (含 BQF/协变 build)
	replacements.string(current.parsed < "1.20.2") {
	replace("AsyncAppender.Builder<?>", "AsyncAppender.Builder")
	}
}

platform {
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("forge") {
			forgeLikeVersionRange.set("[${prop("deps.forge")},)")
		}
	}
}

legacyForge {
	// 版本拼接格式: <mc>-<forge>, 如 1.19.2-43.5.2 (模板原样)
	version = "${prop("deps.minecraft")}-${prop("deps.forge")}"

	runs {
		val devJvmArgs = propsList("mod", "dev_jvm_args")
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "Forge Client (${stonecutter.current.version})"
			programArgument("--username=Dev")
			jvmArguments.addAll(devJvmArgs)
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "Forge Server (${stonecutter.current.version})"
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
	// NightConfig: 编译期需要(源码 import), 运行期由 Forge 发行版自带(FML 配置层即构建于其上), 仅 compileOnly 不嵌入 (与 neoforge 节点同策略)
	compileOnly("com.electronwill.night-config:core:${prop("deps.nightconfig")}")
	compileOnly("com.electronwill.night-config:toml:${prop("deps.nightconfig")}")
}

// legacyforge 的产物任务必须晚于 stonecutter 源码生成 (节点源码先切版本再编译, 模板原样钩子)
tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}
