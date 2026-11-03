# LogFilter 多版本迁移 Stonecutter - 目标与计划 (v5)

## 任务总结
把 LogFilter 1.1.1 (Forge 1.20.1 单版本) 迁移到 Stonecutter 多版本多加载器体系:
- 加载器矩阵: Fabric + NeoForge (无 Forge, max 指令)
- MC 版本: 1.20.1 / 1.21.1 / 1.21.11
- 五节点: 1.20.1-fabric / 1.21.1-fabric / 1.21.11-fabric / 1.21.1-neoforge / 1.21.11-neoforge
- 版本号: 1.2.0, channel_tag 空 (stable)
- 配置文件: config/logfilter-common.toml 三平台同一格式互通 (TOML, NightConfig)
- 核心行为 (过滤 + 异步管线) 逐字平移, 不改逻辑, 存量问题 2/3/4 冻结不动

## 架构

### 构建体系 (参考 stonecutter-mod-template 本地参考件)
- Gradle 9.7.1 wrapper, distributionUrl 换腾讯镜像 (mirrors.cloud.tencent.com/gradle/)
- settings.gradle.kts: pluginManagement (fabricmc + kikugie 两源 + pluginPortal + mavenCentral), includeBuild(build-logic), foojay 1.0.0, stonecutter 0.10-beta.1, loom-back-compat 0.4.1
- stonecutter create: match("1.20.1","fabric") / match("1.21.1","fabric","neoforge") / match("1.21.11","fabric","neoforge"), vcsVersion = "1.20.1-fabric"
- 根构建脚本 stonecutter.gradle.kts: swaps (mod_version/mod_id/mod_name/mod_group/minecraft) + loader 常量 + release 常量
- 节点脚本 build.fabric.gradle.kts (loom-back-compat + loom) / build.neoforge.gradle.kts (moddev)
- build-logic (kotlin-dsl, 平移模板后手术, 见清单)
- CI: build.yml (push 触发全节点 buildAndCollect) / release.yml (tag 触发发布)

### build-logic 手术清单 (相对模板的改动)
1. 砍 fletching-table: ModPlatformPlugin 的 apply 列表删该项, configureFletchingTable() 删
2. processResources 的 kspKotlin 依赖删 (kotlin/ksp 对节点项目不再使用)
3. Loader.kt: FabricManifest.mixins 与 ForgeManifest.mixins 硬编码改空列表 (无 mixin)
4. Loader.kt: accessWidener / accessTransformers 字段置空 (无 AW/AT)
5. entrypoints 硬编码 fabric main/client 恰好匹配 com.lmaxrouter.logfilter.platform.fabric.* 推导, 零改动; datagen 入口移除
6. vanniktech 运行时 apply 块删 (不发 Maven Central)
7. buildAndCollect 任务保留 (CI 产物收集)
8. ModRootPlugin 保留 (publishMods + publish$version + runActive*)
9. 版本目录裁剪: 保留 stonecutter/loomx/moddev/foojay/mod-publish/serialization-json/serialization-toml (Loader.kt 生成 manifest 要用); 砍 postprocess/fletching/mixinconstraints/vanniktech/ksp/kotlin-jvm
10. cliff.toml + .env.template + workflows + .editorconfig 从模板平移; pre-commit 不平移

### 源码包结构 (com.lmaxrouter.logfilter)
- Constants.java: MOD_ID / MOD_VERSION / MOD_NAME / LOGGER (slf4j) [新写, 占位符用模板 swap 语法]
- filter/: LogFilter.java / LogFilterManager.java [逐字平移, LogFilterMod 引用改 Constants]
- async/: AsyncLogPipeline.java / AsyncPipelineConfig.java [平移; snapshot() 读 NFModConfig 或 CommonConfig 由平台接线决定]
- config/: LogFilterConfig.java [新写: NightConfig TOML 自读单例, schema/默认值/范围钳制与 Forge 版 ModConfig 一致, 文件名 logfilter-common.toml 旧配置零迁移] / FilterConfig.java [改写: 从 LogFilterConfig 读] / ConfigIssues.java [平移, LogFilterMod 改 Constants]
- platform/fabric/: FabricEntrypoint.java [新写: ModInitializer → LogFilterBootstrap.init] / FabricClientEntrypoint.java [新写: ClientModInitializer, 注入 ConfigIssues 钩子 + ClientPlayConnectionEvents.JOIN 冲刷]; 整文件 //? fabric {} 条件块; ConfigWatcher 平台无关置于 async/, 聊天框展示复用 client/ClientConfigWarnings
- platform/neoforge/: NeoForgeMod.java [新写: @Mod 构造器 → LogFilterBootstrap.init(FMLPaths.CONFIGDIR)] / NeoForgeClientEvents.java [新写: @EventBusSubscriber(Dist.CLIENT) + LoggingIn 冲刷]; 整文件 //? neoforge {} 条件块
- 删: logfilter.json (v1.0 死资源) / LogFilterMod.java / AsyncPipelineEvents.java / ModConfig.java (职责由 LogFilterBootstrap / ConfigWatcher / LogFilterConfig 承接); ClientConfigWarnings.java 保留 (纯 flush 本体, 双平台共用)
- 资源: pack.png 保留; pack.mcmeta 视模板机制 (fabric/NF 构建均排除, 仓库可不带)

### 配置系统
- Fabric: NightConfig 经 loom include 嵌入 (jar-in-jar), Apache-2.0 与 MPL-2.0 文件级分许可兼容, 待 max 审
- NeoForge: NightConfig compileOnly (发行版自带, 不嵌入); 配置层与 fabric 完全同构 = LogFilterConfig 自读 TOML, 不用 ModConfigSpec
- 热重载: 双平台统一 = async/ConfigWatcher (NIO WatchService 事件驱动, 批内合并 + 下游幂等吸收), 不依赖 loader 配置事件
- 文件: config/logfilter-common.toml 三平台路径一致 = 互通

### 网络事实 (2026-11-03 实测)
- wrapper 腾讯镜像 gradle-9.7.1 200 (validateDistributionUrl 可过)
- fabricmc / kikugie-snapshots / plugins.gradle.org / repo1 / parchmentmc: 全 200
- maven.neoforged.net: DNS 污染, 本地不可达 -> NF 节点构建全交 CI (墙外无障碍)
- BMCLAPI: 有 neoforge jar 无 POM 无 neoform -> 本地 NF 构建死刑, 佐证上一条
- gh/git: Steam++ 代理 + hosts + 系统证书库 = 健康
- exec 3600s 上限 -> 本地 build 用 Start-Process 分离 + 日志落盘 + 轮询

### 版本矩阵 (maven 实查 2026-11-03)
- fabric-loader: 0.19.2 (模板值; release 最新 0.20.1, 保守取模板)
- fabric-api: 1.20.1 = 0.92.12 / 1.21.1 = 0.116.7 / 1.21.11 = 0.141.6
- neoforge: 1.21.1 = 21.1.213 / 1.21.11 = 21.11.48
- yarn 备胎 (1.20.1+b10 / 1.21.1+b3 / 1.21.11+b6), 主路线 mojmap
- java: 1.20.1 -> 17 / 1.21.1+ -> 21

## 实施步骤
1. [x] 信息收集 (代码 10/10, 模板全件, 网络全测)
2. [x] GOAL-PLAN v5 落盘
3. [x] git commit 快照 (含 .agent)
4. [ ] 建 stonecutter 分支 + wrapper + 根级五件配置
5. [ ] build-logic 平移 + 手术 + cliff/env/workflows/editorconfig
6. [ ] 源码迁移 (按包结构清单)
7. [ ] 逐节点 fabric build (1.20.1 -> 1.21.1 -> 1.21.11, Start-Process 协议, 首次全量拉依赖可能 30-60min/节点)
8. [ ] server 冒烟 (active 版本 runServer: 启动不炸 / 过滤生效 / 热重载 / async 安装行 / 退出排干)
9. [ ] push origin stonecutter -> 查 CI build.yml 五节点
10. [ ] release: max 亲手打 tag 1.2.0 触发 release.yml (tomllib 验 tag = version+channel_tag)
11. [ ] 完工: D:\Desktop\30分钟后休眠.bat (跑前读内容, AddHours(2) bug 则修 AddMinutes(30), 留痕)
12. [ ] 终态 memory + GOAL-PLAN 收尾记录

## 风险表
- fabric 1.20.1 用 loom 1.18-SNAPSHOT (loom-back-compat 处理), 首次构建拉 SNAPSHOT, 网络通
- mojmap 类名 1.21.11 漂移 (ResourceLocation -> Identifier 已知): ClientConfigWarnings 用 Component/ChatFormatting, 编译期见真章, 备 replacement
- NightConfig jar-in-jar: 检查产物 jar 内嵌与 fabric.mod.json 依赖自动声明
- WatchService 防抖: 编辑器保存可能产生双事件, 500ms 合并窗口
- 旧 run/ (Forge) 残留: fabric runServer 共享 run/ 目录, 观察, 必要时切 run-fabric/
- 免费版本 (publish.additionalVersions) 机制预留, 首发只发精确版本

## 待 max 拍板
1. NightConfig jar-in-jar (Apache-2.0) - 默认执行
2. README 大改版 (多版本说明) - 构建通过后做, 不阻塞
3. 产物命名 logfilter-1.2.0-fabric+1.21.1.jar 式 (模板 fullVersion 格式) - 默认执行
4. 存量问题 2/3/4 + debug() 死代码: 冻结不动 (迁移不改行为)

## 历史版本归档
- v4 (1.1.1 时代): 见 git 历史 f0f0eb4 的 GOAL-PLAN.md (异步化改造 + 1.1.0/1.1.1 发布记录)