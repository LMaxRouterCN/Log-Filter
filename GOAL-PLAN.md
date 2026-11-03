# LogFilter 多版本迁移 Stonecutter - 目标与计划 (v6)

> v6 由 v5 演进 (2026-11-04): 矩阵五节点 -> 十二节点, fabric + forge + neoforge 三 loader 并列。
> v5 除 tag 发版外全闭环, 详见文末收尾记录。本文件为 v6 主文件, v5 全文见 git 历史 (分支 stonecutter)。

## 任务总结
把 LogFilter 1.2.0 (stonecutter 五节点, fabric + neoforge) 扩展为全 loader 矩阵:
- 加载器矩阵: Fabric + Forge + NeoForge (forge 回归, max 拍板)
- MC 版本: 主版本 1.18.2 / 1.19.2 / 1.20.1 / 1.21.1, 其余能做就做不勉强 (max 拍板)
- 十二节点:
  - fabric: 1.18.2 / 1.19.2 / 1.20.1 / 1.21.1 / 1.21.11
  - forge: 1.18.2 / 1.19.2 / 1.20.1
  - neoforge: 1.20.1 / 1.20.4 / 1.21.1 / 1.21.11
- 次要档: 1.20.4-neoforge 与 1.21.11 全系 (能做就做, 失败不救)
- 版本号: 1.2.0 (v5 已发, 本轮只扩兼容范围, 不改号), channel_tag 空 (stable)
- 配置文件: config/logfilter-common.toml 三 loader 同格式互通 (TOML, NightConfig)
- 核心行为 (过滤 + 异步管线) 逐字平移, 不改逻辑, 存量问题 2/3/4 冻结不动

## 网络事实 (2026-11-03/04 实测)
- maven.neoforged.net: 本地死刑 (DNS 污染, alidns 应答为假 IP 199.59.148.89; 4 个墙外 DoH 全 12s 超时, 无 hosts 绕行可用)
  - legacyforge 插件 marker 只在 NeoForged maven (Portal 只有插件介绍页, marker 与具体版本 POM 均 404)
  - forge + neoforge 节点本地构建全禁, 全交 CI (CI 墙外无障碍, 与 v5 neoforge 路线一致)
- maven.minecraftforge.net: 本地直连 200 (官方 maven, 全量 metadata 可拉)
- BMCLAPI: neoforge jar 可达但 POM / neoform 缺失, 且无 moddev 插件 -> 本地构建救不回来, 佐证死刑判决
- Gradle wrapper: 腾讯镜像 (mirrors.cloud.tencent.com/gradle/), gradle-9.7.1 200
- fabricmc / kikugie (releases + snapshots) / plugins.gradle.org / repo1 / parchmentmc: 全 200 本地可达
- gh / git: Steam++ 代理 + hosts + 系统证书库 = 健康
- exec 3600s 上限 -> 长构建用 Start-Process 分离 + 日志落盘 + 轮询 (v5 方案沿用)

## 版本锚点 (maven 实查 2026-11-04)
| 组件 | 版本 | 来源 |
|---|---|---|
| fabric-loader | 0.19.2 | 模板值 (release 最新 0.20.1, 保守取模板) |
| fabric-api 1.18.2 | 0.77.0+1.18.2 | Modrinth, 与模板同代互证 |
| fabric-api 1.19.2 | 0.77.0+1.19.2 | Modrinth, 模板值互证 |
| fabric-api 1.20.1 | 0.92.12 | v5 实证 (沿用) |
| fabric-api 1.21.1 | 0.116.7 | v5 实证 (沿用) |
| fabric-api 1.21.11 | 0.141.6 | v5 实证 (沿用) |
| forge 1.18.2 | 40.3.12 | minecraftforge.net 官方 maven 直查 |
| forge 1.19.2 | 43.5.2 | minecraftforge.net 官方 maven 直查 |
| forge 1.20.1 | 47.4.28 | minecraftforge.net 官方 maven 直查 |
| neoforge 1.20.1 | 47.1.82 | BMCLAPI (net/neoforged/forge 坐标, CI 用官方源) |
| neoforge 1.20.4 | 20.4.253 | BMCLAPI (net/neoforged/neoforge) |
| neoforge 1.21.1 | 21.1.213 | v5 实证 (沿用) |
| neoforge 1.21.11 | 21.11.48 | BMCLAPI 尾部追加区互证 (沿用) |
| moddev + legacyforge 插件 | 2.0.147 | 模板与项目 version.ref 同源 |
| java | 1.18.2/1.19.2/1.20.1 -> 17, 1.21.x -> 21 | Context.kt javaVersion 映射, 无需手术 |

注意: NeoForge 1.20.1 版本号格式是 47.1.82 (坐标 net/neoforged/forge), 与其他 neoforge 版本的 2x.x.x 格式 (坐标 net/neoforged/neoforge) 不同, 是从 Forge 分叉的过渡产物, 坐标体系特殊。

## 架构

### 构建体系 (参考 stonecutter-mod-template 本地参考件)
- Gradle 9.7.1 wrapper, distributionUrl 腾讯镜像
- settings.gradle.kts: pluginManagement 仓库 (mavenLocal + mavenCentral + gradlePluginPortal + Fabric + NeoForged + KikuGie Releases + KikuGie Snapshots + ParchmentMC), includeBuild(build-logic), foojay 1.0.0, stonecutter 0.10-beta.1, loom-back-compat 0.4.1
  - v6 变更: pluginManagement 回补 NeoForged maven (模板原样有, v5 裁剪掉了, forge 节点的 legacyforge 插件 marker 只能从这里解析; 本地不可达但声明无害, CI 墙外畅通)
- stonecutter create:
  - match("1.18.2", "fabric", "forge") / match("1.19.2", "fabric", "forge") / match("1.20.1", "fabric", "forge", "neoforge") / match("1.21.1", "fabric", "neoforge") / match("1.21.11", "fabric", "neoforge")
  - vcsVersion = "1.20.1-fabric"
- 根构建脚本 stonecutter.gradle.kts: swaps (mod_version / mod_id / mod_name / mod_group / minecraft) + loader 常量 + release 常量
- 节点脚本: build.fabric.gradle.kts (loom-back-compat + loom) / build.forge.gradle.kts (legacyforge) / build.neoforge.gradle.kts (moddev)
- build-logic (kotlin-dsl, 平移模板后手术, 见清单)
- CI: build.yml (push 触发全节点 buildAndCollect) / release.yml (tag 触发发布)

### build-logic 手术清单 (v5 已完成的不再列; 相对模板的 forge 相关改动)
1. [ ] 版本目录回补: plugins 段加 legacyforge-moddev = { id = "net.neoforged.moddev.legacyforge", version.ref = "neoforge-moddev" }
2. [x] [2026-11-04 砍掉] loaderVersion 参数化取消: 查模板 Loader.kt 实证其 ForgeLike.generateManifest 不传 loaderVersion (默认 [2,)), 模板 1.19.2-forge 构建通过; 版本数学亦支持: [2,) 涵盖 Forge 40/43/47 与 NeoForge 20.4+/21.x 全部 javafml。原设想的 [40.3.12,) 特化属无实证发明, 弃
3. [x] ModPlatformPlugin.configureJarTask: MixinConfigs 强写删除 (LogFilter 无 mixin)
4. [x] Loader.kt: 删 Loader.Forge.mixinConfigAttribute (联动上一条)
5. [ ] 节点脚本自行声明插件 (build.forge.gradle.kts 的 plugins 块 apply legacyforge), build-logic 的 ModPlatformPlugin 通过 platform DSL 感知 loader, 不重复 apply
6. [ ] iconFile 字段 (NeoForge 26.2+): 旧 Forge mods.toml 解析器对未知字段的容忍度未证, 实测验, 失败则按 loader 分叉
7. [x] pack.mcmeta: Forge 分支 excludedResources 补排除 (与 Fabric/NeoForge 节点对齐)

### 节点脚本 build.forge.gradle.kts (从模板平移后裁剪)
- plugins 块: id("mod-platform") + id("net.neoforged.moddev.legacyforge") 保留
- platform 块: loader = "forge", required("minecraft") + required("forge") 依赖声明保留, forgeLikeVersionRange 取 deps 常量
- legacyForge 块: version 用 deps.minecraft 与 deps.forge 拼接 保留; runs (client + server) 保留, 砍 mixin debug jvm 参数; mods 块保留
- 砍: mixin 块 (无 mixin) / accessTransformers (无 AT) / validateAccessTransformers / datagen sourceSets (无 datagen) / repositories 的 Modrinth maven (无 Modrinth 依赖) / annotationProcessor mixin processor (无 mixin)
- 保留: createMinecraftArtifacts dependsOn stonecutterGenerate (模板的构建顺序钩子)
- 1.21.11 的 ResourceLocation -> Identifier replacements: forge 节点不存在 (forge 只到 1.20.1), 不带

### 源码包结构 (com.lmaxrouter.logfilter)
- 核心 5 类 + platform/fabric + platform/neoforge: v5 已定, 沿用
- 新增 platform/forge:
  - ForgeMod.java: Mod 注解构造器 -> LogFilterBootstrap.init(FMLPaths.CONFIGDIR), 事件注册 MinecraftForge.EVENT_BUS
  - ForgeClientEvents.java: 客户端事件 (LoggingIn 冲刷, ConfigIssues 钩子); 事件类名 1.18.2 = LoggedInEvent, 1.20.1 = LoggingIn, 1.19.2 处于改名期具体名待实测确认
  - 整文件 //? forge {} 条件块 (与 fabric / neoforge 块同构)
- v1 时代的 LogFilterMod.java / AsyncPipelineEvents.java / ModConfig.java 在 v5 已删, 职责由 LogFilterBootstrap / ConfigWatcher / LogFilterConfig 承接, forge 平台复用同一套, 不复活旧类

### 配置系统
- v5 已定: NightConfig 自读 TOML 三 loader 同构, LogFilterConfig 单例, 文件 config/logfilter-common.toml 互通
- fabric: NightConfig jar-in-jar (loom include), Apache-2.0 文件级分许可, v5 已实现沿用
- neoforge: NightConfig compileOnly (发行版自带) 沿用
- forge: 同 neoforge, compileOnly + 发行版依赖 FML 自带 NightConfig, 实测验 (devruntime 是否需要额外依赖)
- 热重载: ConfigWatcher (NIO WatchService, 事件驱动, 批内合并) 平台无关, 三 loader 复用, 不依赖 loader 配置事件

## 实施步骤
1. [x] 信息收集 (源码 10/10 + 模板全件含 forge 节点脚本 + 网络全测 + 版本锚点全实查)
2. [x] GOAL-PLAN v6 落盘
3. [ ] git commit 快照 (v6 改动前现状)
4. [ ] build-logic 手术 (清单 1-7)
5. [ ] 根配置: settings.gradle.kts (match 矩阵 + NeoForged maven 回补) / stonecutter.gradle.kts (deps.forge 常量) / libs.versions.toml (legacyforge 插件回补)
6. [ ] 节点脚本 build.forge.gradle.kts (平移 + 裁剪)
7. [ ] 源码: platform/forge 两类 + //? forge {} 条件块
8. [ ] 本地试构建 fabric 1.18.2 (loom-back-compat 下探测试, 超模板下限属高危)
9. [ ] push CI 全节点 buildAndCollect (forge + neoforge 全交 CI)
10. [ ] 修问题循环: fabric 本地修, forge/neoforge 看日志修
11. [ ] 冒烟: CI 产物 jar 下载, 手动装实例跑 (fabric 1.20.1 本地深冒烟 v5 已做过, v6 只对新节点抽测)
12. [ ] 完工: 终态 memory + GOAL-PLAN 收尾 + 休眠 bat

## 风险表
- fabric 1.18.2 loom-back-compat 下探: 模板下限 1.19.2, 1.18.2 超范围高危; 失败则考虑 fabric 1.18.2 节点降级或砍
- fabric-loader 0.19.2 跑 1.18.2: 2026 loader 跑 2022 游戏, 未证; 失败回退 per-version 覆盖旧 loader (0.14.x)
- NeoForge 1.20.1 (47.1.82): 过渡坐标 + moddev 支持未证, CI 试, 失败即砍 (次要档精神)
- 1.19.2 事件类名: LoggedInEvent -> LoggingIn 改名期, 具体小版本归属未证, 编译期见真章, 失败则版本分叉
- forge mods.toml 的 iconFile 未知字段容忍度: 未证, 实测验
- forge 打包 pack.mcmeta 是否入 jar: 未证, 实测后决定是否补 excludedResources
- 1.20.4-neoforge + 1.21.11 次要档: 单独失败不影响主版本交付
- WatchService 防抖: v5 已处理 (500ms 合并窗口), 沿用

## 待 max 拍板 (非阻塞, 默认按注释执行)
1. NeoForge 1.20.1: 默认 CI 试, 失败即砍
2. forge 端 NightConfig devruntime: 默认 compileOnly, CI 实测验
3. 产物命名: 沿用 v5 格式 logfilter-1.2.0-fabric+1.21.1.jar 式
4. 存量问题 2/3/4 + debug() 死代码: 冻结不动
5. build/ 与 run/ 残留: 构建失败才清, 不失败留观察

## 历史版本归档
- v5 (stonecutter 五节点时代): git 分支 stonecutter, 本文件被 v6 覆盖前的版本
- v4 (1.1.1 时代): git 历史 f0f0eb4 的 GOAL-PLAN.md (异步化改造 + 1.1.0/1.1.1 发布记录)

## v5 收尾记录 (2026-11-03, 原文平移)
- 步骤 4-9 完成 (e6c5138): fabric 1.20.1 全量通过, maven.neoforged.net 本地 DNS 污染 -> NF 节点编译交 CI, 五节点 buildAndCollect 全绿
- 修复链: efb121d 根治 NightConfig 3.6.7 load() 0 字节空壳 (TestNight 实证, CI 41604700412 五节点绿)
- 深度冒烟完成: config 自愈 (0B -> 4167B 默认落盘) / 过滤零泄漏 (latest-run2.log = run#3b: DEBUG 证据行 1 条, 真目标残留 0) / 热重载三探针 (debug.log 17:03: 坏 TOML 拒载 + Invalid regex 容错 WARN + 还原后干净 reload) / 终态 config 已还原默认
- 步骤 11 完成: 休眠 bat 复核健康 (AddMinutes(30)), 收尾轮已触发
- 步骤 12 完成: 终态 memory + 本记录
- 待 max (v5 遗留): 亲手打 tag 1.2.0 -> release.yml; 决策: 旧 build\libs 三 forge jar 删否 (v6 forge 回归后语境变化: 若 forge 节点重建, 这些 1.1.1 时代 jar 可作对照) / merge main / README 大改版 / 上游 issue (stonecutter 模板 files.disable 对 active 节点必要性)