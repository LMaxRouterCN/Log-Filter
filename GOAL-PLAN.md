# LogFilter 异步化改造 - 目标与计划 (v4 终态: 实施与验证全部完成)

## 任务总结
"异步功能" = 把原版同步的日志输出(控制台渲染/文件写 I/O)换成异步, 过滤逻辑维持现状。
程序化把 root logger 的 appender 链包进 Log4j2 自带 AsyncAppender(队列实现, 零新依赖),
打日志线程只付入队成本; 过滤裁决仍在入队前同步完成(被滤事件零队列成本)。

## 最终架构
[打日志线程] -> filter链(不变) -> AsyncAppender入队 -> [后台消费线程] -> DebugFile/Console/ServerGuiConsole/File
- root AppenderRef 原样复用(level/filter 语义无损); updateLoggers 原子提交
- 事务性 install(失败自动回滚原链) / 排干式 uninstall / JVM 关闭钩子
- 可插拔队列工厂(反射, 默认 ArrayBlockingQueue) = Disruptor 升级钩子(JarJar 注入即可, 零代码改动)
- 事件驱动: ModConfigEvent.Loading/Reloading, 配置签名幂等(参数没变不重建)
- 顺带修复存量问题1: LogFilterManager.reload() 死代码复活, 过滤配置从此热重载

## 文件清单(本次新增/修改)
- config/ModConfig.java: +6 配置字段 + [async] 配置段(默认关)
- async/AsyncPipelineConfig.java: 不可变配置快照(含签名)
- async/AsyncLogPipeline.java: 安装器(幂等/事务/关闭钩子/反射队列工厂)
- async/AsyncPipelineEvents.java: MOD bus 事件接线(Loading/Reloading)
- gradle/wrapper/gradle-wrapper.properties: distributionUrl -> 腾讯镜像(见环境记录, 是否提交待定)
- LogFilterMod.java: 零改动

## 编译
BUILD SUCCESSFUL (2026-09-29 03:09, Java 17 / Gradle 8.8 / Forge 1.20.1-47.4.10)

## 冒烟测试证据 (2026-09-29 03:20-03:22, runServer 自动化双轮)
1. Reloading 路径: 服务器 Done 后, 运行中把 toml enableAsyncLogging 翻 true
   -> 同秒出现 [async] 安装行, 触发线程 Thread-1(Forge 文件监视线程, 验证跨线程安全),
   包装 4 个 appender [DebugFile, Console, ServerGuiConsole, File], 服务器存活
2. 幂等: 原样重写同一 toml 再触发 Reloading -> 安装行数保持 1(签名短路, 无重复安装)
3. Loading 路径: 冷启动(toml 已开) -> 安装行 03:21:31 落在 main 线程 mod 构造期,
   早于 "Done (6.308s)" 03:21:42, 开机时序正确
4. 事件流动: Run C 安装行之后的整个启动日志风暴全部经异步管线送达 Console;
   [async] 行本身出现在 latest.log -> 文件侧送达验证
5. 已知伪影: 捕获日志中中文显示为 ??? 是代理侧 PowerShell 读日志的编码不匹配,
   非 mod 输出问题; 客户端实测直接看游戏控制台即可

## 环境事实与修复记录
- services.gradle.org 本机不可达 -> wrapper URL 已改腾讯镜像 mirrors.cloud.tencent.com/gradle/
- MC/Forge CDN 全可达但限速; MCP 1.20.1-47.4.10 全套缓存已就绪(冷启动约 4 分钟, 断点续传)
- 代理 exec 3600s 上限 -> 长任务协议 = Start-Process 分离 + 日志落盘 + 轮询
- 休眠: 原计划任务 AutoHibernate2h 已取消; 终态已武装 AutoHibernate30m
  (30 分钟后 shutdown /h); 取消命令: schtasks /Delete /TN AutoHibernate30m /F
- run/ 目录状态: eula=true; logfilter-common.toml 的 async 开关留 true
  (Max 直接 runClient 即可实测); 冒烟生成的测试世界可随意删

## 编译警告存量(非本次引入, 已按规则上报)
- LogFilterMod.java:19/25 ModLoadingContext.get()/FMLJavaModLoadingContext.get()
  被 Forge 标记 removal(47.4.10 可用); 现代化 = 构造器注入 ModContainer; 是否做待定

## Max 睡醒待拍板清单
1. wrapper 镜像 URL 是否提交进仓库(不提交则 revert 该单行即可)
2. 存量问题2 缓存满 clear 全清(建议半量淘汰) / 存量问题3 hash 碰撞误拦(建议不动) / 存量问题4 debug() 死代码(建议删)
3. 入口类 deprecation 现代化是否做
4. 冒烟结果确认 + 客户端实测(开关已代开, 直接 runClient;
   看点: 控制台日志流手感 / latest.log 完整性 / 退出时队列是否排干不丢尾)
5. 通读本 GOAL-PLAN v4