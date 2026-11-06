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
