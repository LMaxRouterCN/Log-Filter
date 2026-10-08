<!-- ID:001 -->
LogFilter 模组(Forge 1.20.1-47.4.10, D:\Documents\mcmod\LogFilter)项目终态(2026-09-29):
v2 异步化改造已完成并验证: Log4j2 AsyncAppender(2.19.0 自带)包 root 全部 appender,
ModConfigEvent.Loading/Reloading 事件驱动, 配置签名幂等, 事务性安装(失败回滚),
排干式卸载 + JVM 关闭钩子, 可插拔队列工厂(反射, 默认 ArrayBlockingQueue, Disruptor 升级钩子)。
新增 async/ 包三类 + ModConfig [async] 配置段(6 参数, 默认关); LogFilterMod 入口零改动。
冒烟全绿: 运行中翻开关同秒安装(Thread-1 跨线程验证), 幂等复测不重复,
冷启动安装行早于 Done, 启动日志风暴全经异步管线送达 Console+File 双侧。
顺带修复: LogFilterManager.reload() 死代码复活(过滤配置热重载)。
待 Max 拍板: wrapper 镜像 URL 是否提交 git / 存量问题2,3,4 / 入口类 deprecation 现代化 / client 实测。
v1 过滤功能不变。环境事实见记忆 002。
tag: LogFilter, 异步化, 项目
<!-- END:001 -->
<!-- ID:002 -->
Max 工作机(Windows 11 工作站)环境事实(2026-09实测, 2026-10-09补充修订, 跨任务复用):
- services.gradle.org 网络不可达(pwsh 直连也超时, 非证书问题, Java 侧表现为 PKIX); 腾讯镜像 mirrors.cloud.tencent.com/gradle/ 可用(200)
- MC/Forge 各端点(piston-meta/piston-data/libraries.minecraft.net/maven.minecraftforge.net/repo1.maven.org)全可达但限速慢, 大文件下载会拖很久; BMCLAPI(bmclapi2.bangbang93.com)可达可作后备; api.github.com 连通(200)——GitHub 通道与 gradle 通道命运不同
- gh CLI 2.102.0 已 winget 装好(MSI 布局: gh.exe 直在 C:\Program Files\GitHub CLI\ 根目录, 无 bin\ 子目录, 别猜), 交互登录用临时 ps1+Start-Process powershell -NoExit 弹窗由 Max 亲手操作
- forge_gradle MCP 缓存按文件断点续传, 中断不归零; 1.20.1-47.4.10 全套环境已就绪于 C:\Users\LLL95\.gradle\caches\forge_gradle (冷启动约4分钟)
- PokerAgent exec 3600s 超时且强杀进程树+残留进程; 长任务协议: Start-Process 分离启动 + 日志落盘 $env:TEMP\logfilter-*.log + 每轮 watcher 轮询 PID/tail
- Java: PATH 上的 java=zulu21, JAVA_HOME=E:\Java\Zulu\zulu-17(Forge 1.20.1 用17, 恰好匹配); 无系统 gradle
- Max 休眠管理: D:\Desktop\取消休眠.bat 会删除计划任务 AutoHibernate2h; 自主模式收尾用 schtasks 创建一次性倒计时休眠任务(30分钟)替代
- 用户目录 C:\Users\LLL95, 项目目录 D:\Documents\mcmod\
- 系统时钟以文件系统时间戳为准; 本次实测构建日=2026-10-09(此前误标10-10, 已在 gradle.properties/GOAL-PLAN 纠正; 散落代码注释中的 10-10 需逐个核对 mtime 后修)
[2026-10-09] PowerShell 下当前目录可执行文件必须带 .\ 前缀(裸 gradlew.bat 报 not recognized)
[2026-10-09] ForgeGradle 6.0.+ 动态版本解析会被 forge maven 限速拖死; --offline 走缓存可过插件解析, 但应用插件时仍连 forge maven 校验撞 PKIX; 出口=命令行 -Dnet.minecraftforge.gradle.check.certs=false(单主机/offline无下载/不落盘可回退); build 任务同参数 BUILD SUCCESSFUL 20s, reobfJar 原地替换产物(libs 内同名仅一个 jar 即发布产物)
[2026-10-09] exec 终端实测确认: pwsh 7.7.0-preview.3($PSVersionTable 亲测), 即回退链顶层 PowerShell 7+; 故 PS7 专有语法(三元运算符等)在 exec 中合法, 仅当后端配置变更回退 5.1 时才需回避; exec 正确用法详单(@@help实读): ①直接把 PS 语句交给 exec, 严禁包一层 powershell.exe(曾致 $变量被外层双引号插值吞空三连翻车); ②优先代码块格式(单行模式特殊字符易碎); ③单代码块=同进程共享变量, 多代码块=独立进程无状态不拼接; ④exec代码块不做```还原; ⑤含del/rd/rm/Remove-Item等危险关键词弹用户确认窗; ⑥顶层进程退出+1.5s排水窗即收工, Start-Process后台须脚本内自加-Wait
[2026-10-09] 指令格式红线: 多行指令必须完整闭合为 【cmd】+参数行、
tag: 环境, 网络, 构建, PokerAgent
<!-- END:002 -->
<!-- ID:003 -->
[2026-10-09] LogFilter 发版进行中: (1)Max三决策已落地执行: license MIT→MPL-2.0(LICENSE经gh api取mozilla/pdf.js权威全文, mods.toml license字段, build.gradle经查无license行无需动), mods.toml description双语化(英前中后, 原中文句语义为扩展版子集), README(EN)四处手术(安装热重载化/崩溃句改容错/AsyncOutputPipeline与ConfigHotReload插入, 锚点取自实读); (2)资源变更必须rebuild, jar内META-INF/mods.toml license复核; (3)changelog终稿按Max结构调整: Added只有异步管线/容错进Fixed/既有功能清单压轴做以后更新日志基准/语言英前中后, 已贴聊天等Max确认; (4)Modrinth已存在两个旧版本, changelog不写"首个正式版"; (5)gh已登录(LMaxRouterCN, keyring), repo=Log-Filter.git无旧tag; (6)commit范围=全部脏文件含.agent(Max拍板); (7)旧包101.beta.jar删除未决; (8)红线: release创建前正文必须Max确认; 待确认后→git add -A+commit→gh release create "1.1.0+1.20.1" 传jar→回执验URL.
tag: LogFilter, 待办, 发版, 配置, 正则
<!-- END:003 -->
