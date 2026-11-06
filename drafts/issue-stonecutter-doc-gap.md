# 上游 issue 草稿 — Stonecutter active 节点直通与两条未记载解法 (文档缺口)

> 状态: 已投递 2026-11-06 (max 授权: 两个项目都发, 只发英文版; 中文正文留档未投)
> A. Codeberg stonecutter/stonecutter 主项目: https://codeberg.org/stonecutter/stonecutter/issues/77
> B. GitHub rotgruengelb/stonecutter-mod-template: https://github.com/rotgruengelb/stonecutter-mod-template/issues/40
> 上游答复 (2026-11-06, KikuGie @ Codeberg#77): active 版本条件状态不正确时, 设计意图是先跑 Reset active project 任务; files.disable 与星注释壳两条解法均被确认正确

## 标题候选

- EN: Active node compiles shared root sources directly — loader gates are invisible to javac; two community workarounds (files.disable / star-comment shell) are undocumented
- CN: active 节点直接编译共享根源码, 门控对 javac 不可见; 两条社区解法 (files.disable / 星注释壳) 均未见于文档

## 正文 (中文)

**环境**: Stonecutter 0.10-beta.1; 多 loader 多版本项目 (fabric / forge / neoforge, 11 节点); 平台代码按 platform/<loader> 分目录, 其余共享根 src.

**现象**: active 节点 (vcsVersion) 直接编译共享根源码 — //? 门控注释对 javac 不可见. 平台目录中对端 loader 的文件 (带对端专属 import) 一并入编译, 直接炸. 非 active 节点无此问题: 编译对象是 generated 门控剥离副本.

**解法 1 (本项目在用)** — files.disable 把对端平台源排除出编译:

    stonecutter {
        // active 节点裸编共享根 src, 门控对 javac 不可见, 排除对端 loader 平台源
        files.disable(true, "**/platform/neoforge/**", "**/platform/forge/**")
    }

**解法 2 (rotgruengelb/stonecutter-mod-template 在用)** — 星注释壳: 对端 loader 专属文件整体包进星注释, 门控条件激活时被 stitcher 剥壳, 禁用时是合法空文件:

    //? forge {

    /*import com.example.modtemplate.ModTemplate;
    import net.minecraftforge.fml.common.Mod;

    @Mod(ModTemplate.MOD_ID)
    public class ForgeEntrypoint {
        public ForgeEntrypoint() {
            ModTemplate.onInitialize();
        }
    }
    *///?}

(模板 ForgeEntrypoint.java 自首日 commit 17384b3 即此形态)

**行为验证** (2026-11-05, 本地, stonecutter 0.10-beta.1):

- 探针 A: //? fabric { + 星壳, 在 1.18.2-fabric (非 active) 节点: generate 副本 = 壳被剥离, 裸码
- 探针 B: //? neoforge { + 星壳, 同节点: 副本 = 壳原样保留 (禁用条件, javac 视角合法空文件)
- 源码链: stitcher FileAstUncommenter.uncommentCode → SlashStarUncommentingStrategy (删除 COMMENT_OPEN / COMMENT_CLOSE); StarCommentEscaper 以 ~ 转义处理嵌套星注释

**请求**: 这两条路在多 loader 项目里几乎是硬需求, 但文档 (README / docs 站) 均未记载. 是否可以补充:

1. active 节点直通行为, 及其对门控语义的影响
2. 两条解法各自的官方定位 (受支持特性? 推荐哪条?)
3. 若星壳手法并非预期用法, 正确姿势是什么?

若已有记载, 请指路 — 我没有搜到. 谢谢!

## English

**Environment**: Stonecutter 0.10-beta.1; a multi-loader project (fabric / forge / neoforge, 11 nodes); platform code under platform/<loader>, everything else in the shared root src.

**Symptom**: the active node (vcsVersion) compiles the shared root sources directly — //? gate comments are invisible to javac. Files of the other loaders (with loader-specific imports) join the compilation and break it. Non-active nodes are unaffected: they compile the generated, condition-stripped copies.

**Workaround 1 (we use this)** — files.disable excluding the other loaders' platform sources:

    stonecutter {
        files.disable(true, "**/platform/neoforge/**", "**/platform/forge/**")
    }

**Workaround 2 (used by rotgruengelb/stonecutter-mod-template)** — a star-comment shell: loader-specific files are wrapped in /* ... */ inside the gate; when the condition is enabled stitcher strips the shell, when disabled the file stays a legal empty file:

    //? forge {

    /*import ...;
    @Mod(...)
    public class ForgeEntrypoint { ... }
    *///?}

(present in the template since its first commit, 17384b3)

**Verified behavior** (2026-11-05, local, 0.10-beta.1):

- Probe A: //? fabric { + shell, on 1.18.2-fabric (non-active): generated copy = shell stripped, live code
- Probe B: //? neoforge { + shell, same node: copy = shell kept (disabled condition, legal empty file for javac)
- Source chain: stitcher FileAstUncommenter.uncommentCode → SlashStarUncommentingStrategy (deletes COMMENT_OPEN / COMMENT_CLOSE); StarCommentEscaper handles nested star comments via ~ escaping

**Ask**: both routes look like hard requirements for multi-loader projects, yet neither is documented (README / docs). Could you cover:

1. the active-node pass-through behavior and its impact on gate semantics
2. the official status of each workaround (supported? recommended?)
3. if the shell trick is unintended, what is the intended way?

If this is already documented somewhere, a pointer would be great — I could not find it. Thanks!
