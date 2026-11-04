# 构建系统与模板使用指南

给要改动本仓库的人：这套 Stonecutter 多版本模板的部件地图、日常操作、发布流水线和已知雷区。玩家向的安装与配置说明在 README，这里只讲开发。

## 目录地图

    src/                          共享源码树, 11 个版本节点全部从这里切出, 不要建版本子目录
    versions/                     构建时动态生成的节点目录 (git 不跟踪), 里面只有构建产物
    build-logic/                  kotlin-dsl 约定插件: loom / moddev / legacyforge 装配 + 元数据生成 + 发布逻辑
    stonecutter.properties.toml   数据中枢: [mod] 元数据 + 各节点版本依赖表
    settings.gradle.kts           插件仓库 + 节点矩阵 (match 声明)
    stonecutter.gradle.kts        stonecutter 参数: loader 常量 + swaps 占位符
    build.<loader>.gradle.kts     fabric / forge / neoforge 三个节点脚本 (平台配置 + replace 规则)
    .github/workflows/            build.yml (push 触发全量构建) + release.yml (tag 触发发布)

## 多版本机制: 一棵源码树, 11 个节点

节点矩阵在 settings.gradle.kts 的 stonecutter 块里声明, 当前 11 节点: fabric x5 / forge x3 / neoforge x3 (1.20.1-neoforge 不存在, 原因见雷区)。

### 条件块 (源码层)

节点构建时从共享 src 生成自己的源码副本, 只保留条件成立的行:

    //? if >=1.21.11 {
    import net.minecraft.resources.Identifier;
    //? }

loader 维度同理, 常量来自 stonecutter.gradle.kts 的 constants.match (取节点名后缀):

    //? fabric {
    // 只有 fabric 节点能看到这里
    //? }

### 占位符 (swaps)

    public static final String VERSION = /*$ mod_version */ "";

生成时 /*$ 名字 */ 后面的字面量被换成 stonecutter.gradle.kts 里 swaps 表的值, 数据源头都是 stonecutter.properties.toml 的 [mod] 段 (版本号 / modid / MC 版本)。

### 编译期替换 (build 脚本层)

类名或方法名跨版本搬家但用法没变的, 不用条件块写两份, 在节点脚本的 stonecutter 块里做字符串替换:

    stonecutter {
        replacements.string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }

用法本身变了 (参数不同语义不同) 的, 老实用条件块。

## 日常命令

    ./gradlew buildAndCollect            全量构建 11 节点, CI 同款
    ./gradlew :1.21.1-fabric:runClient   单节点跑客户端 (全路径寻址永远有效)
    ./gradlew :1.20.4-neoforge:build     单节点构建

本地环境: JDK 25 (gradle daemon 要求); 网络需可达 KikuGie / Fabric maven, NeoForged maven 部分网络不可达, 仓库序已把可靠源前置, 其声明保留作 CI 兜底。

## 版本号与元数据

唯一入口: stonecutter.properties.toml 的 [mod].version。改完自动传导到: jar 文件名、平台发布版本号、swaps 注入的 Constants、fabric.mod.json / neoforge.mods.toml 元数据。

channel_tag: 空 = 正式版 (发布渠道 stable); "-beta.1" 形态会拼进版本号后缀并决定发布渠道 (beta / alpha)。

## 加新版本节点

    1. settings.gradle.kts: match("x.y.z", "loaderA", "loaderB")
    2. stonecutter.properties.toml: 加对应 loader 的版本段 (deps.minecraft + loader 版本锚点)
    3. 源码按需补条件块, 节点脚本按需补 replace
    4. ./gradlew buildAndCollect 验证

## 已知雷区 (全部实测付过学费)

- log4j2 AsyncAppender.Builder 的泛型性与 MC 版本无单调性: 1.18.2 / 1.20.1-forge 的 classpath 里非泛型, 1.19.2-forge 泛型。raw 写法两侧都合法 (泛型侧只吃 unchecked 警告, javac 无 -Werror)。跨版本统一用 raw, 需要时编译期 replace 擦除。
- NeoForge 1.20.4 (FML 1.0.x 过渡期): EventBusSubscriber 还嵌套在 Mod 里 (Mod.EventBusSubscriber, 注解元素 value: Dist[] + modid: String), 21.x 才拆成顶层类。仓库已用 <1.20.5 门控 replace 处理, 新写事件订阅类时留意。
- javadoc doclint 把注释里的裸 < 当非法 HTML 标签头: 注释里写 &lt;。
- 配置首建判定必须先于 NightConfig open, 否则留 0 字节空壳文件 (已修, 动配置加载逻辑时留意顺序)。
- 1.20.1 的 NeoForge 过渡版不可用: moddev 插件只认 net.neoforged:neoforge 坐标, 过渡版 47.1.82 实际发布在 net.neoforged:forge 坐标下, 解析失败 (CI 41690712571), 该节点已砍勿加回。

## 发布流水线

推 tag 触发 release.yml, 链路: 校验 tag (必须等于 [mod].version + channel_tag, 不匹配自动删 release 和 tag) → git-cliff 生成 CHANGELOG.md (配置在 cliff.toml, 按约定式提交前缀分组; 注意本仓库历史提交多为非约定式消息, 生成的分组会偏粗, 发版前核对或手动整理) → MOD_IS_RELEASE=true 构建 (剥 -SNAPSHOT 后缀) → ./gradlew publishMods 投递。

关键形态: 11 个节点各自独立投递, 没有聚合层。1.20.1-fabric 和 1.20.1-forge 是两个独立发布单元, 一次发版在平台落地 11 个条目; 中途挂了重跑等于重发, 不要假设幂等。

投递内容: 主 jar + sources jar (javadoc 不投), changelog 取根目录 CHANGELOG.md 全文 (构建配置期直读, 本地手跑 publishMods 前必须先有这个文件)。

### 发布开关 (GitHub 仓库 Variables / Secrets)

    PUB_MODS_ENABLE                 总闸, 不设 true 则全程 dryRun (构建照跑, 发布只打印)
    PUB_DRY_RUN                     强制 dryRun, 排演用
    PUB_MODRINTH_TOKEN              Modrinth API token (Secret)
    PUB_MODRINTH_PROJECT_ID         Modrinth 项目 ID (Variable)
    PUB_CURSEFORGE_TOKEN            CurseForge API token (Secret)
    PUB_CURSEFORGE_PROJECT_ID       CurseForge 项目 ID (Variable)
    PUB_CURSEFORGE_ENABLE            CF 渠道总闸, 未过审或暂不发 CF 时关 (不连累 MR/GH 投递)
    PUB_GITHUB_ENABLE               开了才建 GitHub Release
    PUB_MODRINTH_STAGING            Modrinth 走 staging 沙箱 (CurseForge 整个跳过)

### 本地发布后门

env 解析顺序: 进程环境变量 → 仓库根 .env 文件 (java Properties 格式)。本地真跑 publishMods 用的; .env 已被 gitignore 覆盖, token 别提交。

### 依赖链接

节点脚本 dependencies 块声明的模组关系 (required / optional / incompatible / embeds) 发布时转成平台依赖链接, 但只有设置了 slug 的才生效。当前只有 fabric-api 设了 slug, 所以 forge 系条目在平台上没有依赖链接 (minecraft / forge / neoforge 属于平台本体, 不链也正常)。