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
Max 工作机(Windows 11 工作站)环境事实(2026-09 实测, 跨任务复用):
- services.gradle.org 网络不可达(pwsh 直连也超时, 非证书问题, Java 侧表现为 PKIX); 腾讯镜像 mirrors.cloud.tencent.com/gradle/ 可用(200)
- MC/Forge 各端点(piston-meta/piston-data/libraries.minecraft.net/maven.minecraftforge.net/repo1.maven.org)全可达但限速慢, 大文件下载会拖很久; BMCLAPI(bmclapi2.bangbang93.com)可达可作后备
- forge_gradle MCP 缓存按文件断点续传, 中断不归零; 1.20.1-47.4.10 全套环境已就绪于 C:\Users\LLL95\.gradle\caches\forge_gradle (冷启动约4分钟)
- PokerAgent exec 3600s 超时且强杀进程树+残留进程; 长任务协议: Start-Process 分离启动 + 日志落盘 $env:TEMP\logfilter-*.log + 每轮 watcher 轮询 PID/tail
- Java: PATH 上的 java=zulu21, JAVA_HOME=E:\Java\Zulu\zulu-17(Forge 1.20.1 用17, 恰好匹配); 无系统 gradle
- Max 休眠管理: D:\Desktop\取消休眠.bat 会删除计划任务 AutoHibernate2h; 自主模式收尾用 schtasks 创建一次性倒计时休眠任务(30分钟)替代
- 用户目录 C:\Users\LLL95, 项目目录 D:\Documents\mcmod\
tag: 环境, 网络, 构建, PokerAgent
<!-- END:002 -->
