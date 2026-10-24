# r22 IDEA 最新源码与三可选模组：配置已落盘，运行验收待执行

时间基准：用户指定 2026-10-24 06:23 EDT。此批为有界配置实施，**不是已修好 IDEA 的运行证明**。没有启动 Gradle/javac/client/桌面/截图，没有改业务源码、固定 integration 快照或正式 mods/存档；没有启用水系统或机器生产。

## 实际交付

- 原 build.gradle 前态 SHA256：D90812F697B5FB467158DCF489C49396D1661C51D084FF1A1D34428A87C255B0。
- 修改后 SHA256：C136EC9486710A11ABEF79DD7033D90D9FAA085CBAE1B0C54503A340A69A5693。
- 新共享配置 `.run/Client_Latest_Sources.run.xml`：**Client - Latest Sources + Curios Jade JEI**。
- 新共享配置 `.run/Client_Latest_Sources_Bare.run.xml`：**Client - Latest Sources Bare (no optional mods)**。
- 前态原字节保存在本目录 build.gradle.before（仅证据，不自动恢复）。applied.patch 只显示本批相对前态增量；git diff 相对索引还包含用户既有改动，不能把它们归为本批。

## 变化与理由

1. Gradle client 默认工作目录为工程专用 `run/compat-r17`，直接复用其中三 jar，不复制、不移动、不修改 jar，不装到全局 runtimeOnly；没有 jar 时不因文件缺席主动报错。`withCurios/withJade/withJei=true` 会明确拒绝，避免目录与runtimeOnly重复 mod ID；默认均false。旧 `compatR17=true` 仍可传，但无需再传。
2. `-PideaWithoutOptionalMods=true` 选择 `run/idea-debug-bare-r22`。共享 Bare 配置已带该 property，无需搬走三个 jar；该目录若已存在额外 jar，先只读记录并协调，不能删除它们来伪造缺依赖测试。
3. `prepareIdeaClient` 只依赖 `classes` 和 `prepareClientRun`。classes 包含 compileJava/processResources（含 metadata 生成），不依赖稳定 JAR、完整 build、check 或 GameTest；不跳编译。Java 编译失败必须阻止启动，不能悄悄退回旧 classes/JAR。允许未通过 GameTest 的最新代码调试，不等于允许编译错误绕过。
4. 实查已安装 ModDevGradle **2.0.148 sources.jar**：IntelliJIntegration 用 run.ideName 注册配置，用 run.tasksBefore 添加 Gradle前置；ModDevRunWorkflow 的 runClient 依赖 prepareClientRun 和 run.tasksBefore；prepareClientRun 不反向依赖 tasksBefore。故额外设置 `neoForge.runs.named('client') { taskBefore prepareIdeaClient }`，生成的 Client 重导入也保留确定的编译前置；不把 prepareIdeaClient 挂到 prepareClientRun（会成环）。独立共享配置名称不同于自动生成的 Client，不去覆盖 workspace.xml。插件源码关键行见 moddev-generation-evidence.txt。
5. 独立 Application 配置保留实查 DevLaunch 主类、ChemicalElements.main 模块及 @clientRunProgramArgs/@clientRunVmArgs；fml.modFolders **显式固定本工程 build/classes/java/main 与 build/resources/main**，不依赖 IDEA 是否委托构建而漂到 out，不指向 integration 快照/JAR。使用 Gradle before-launch 替代 Make，并限制 2 workers / no parallel；客户端VM附 ActiveProcessorCount=2、Xmx2G。普通 runClient CLI 没有额外游戏VM限额，CLI备用须显式传限额（下文）。
6. Server/Data/GameTestServer 分别隔离到 `run/idea-server-r22`、`run/idea-data-r22`、`run/idea-gametest-r22`。data仍输出src/generated/resources，不要求普通客户端先跑runData。**旧IDE内存里的Server/Data/GameTest配置需要Reload后才获得新目录**，本批未动其workspace；不要在Reload前点旧配置。

## 静态核验与并发事实

- 原诊断 REPORT.md 与其 artifacts-sha256.json 全部匹配后，才以当前build精确上下文替换；写入前再次校验build原字节，保留CRLF。两新XML用独占新建模式，存在同名就拒绝，不覆盖用户配置。
- XML解析成功、字段/路径/主类/模块/before-launch检查成功；显式有向任务图无环，未出现jar/check/GameTest依赖；git diff --check -- build.gradle exit0。见static-validation.json。**这是静态检查，不是Groovy/Gradle实际解析，--dry-run仍待唯一owner执行。**
- 实施时22项保护文件立即回读全MATCH，保存在protected-before/after.json；随后静态检查发现外部活动更新了 `.idea/workspace.xml` 与 `build/moddev/minecraft_assets.properties`。本任务从未写这两文件，未恢复覆盖它们；分别记录新旧hash于static-validation.json。不能据此断言是哪一个外部进程写入，也不能宣称IDE已导入。
- index SHA256仍82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69，13条索引文件清单不变。三jar hash与诊断全MATCH。没有git add/reset/commit。
- 固定快照由原integration owner独占；本批既不修改也不重新散列其全部树，避免不必要扫描。原classes/resources运行权未接管。

## 用户IDE操作（应由唯一运行owner协调窗口后执行）

1. 打开唯一工程 E:/Projects/ChemicalElements；先协调唯一输出窗口，再点 Gradle工具窗口 → Reload All Gradle Projects。Reload会触发ModDev准备任务写build/moddev，不能与其他构建并跑。
2. Run → Edit Configurations → 选 **Client - Latest Sources + Curios Jade JEI**（不是旧Client）。JDK=21，主类net.neoforged.devlaunch.Main，模块ChemicalElements.main；工作目录为工程run/compat-r17。
3. Before launch应只有启用的 Run Gradle task：Tasks=prepareIdeaClient，工程=$PROJECT_DIR$，Arguments=`--max-workers=2 --no-parallel`。此XML序列化尚未经IDE导入验证：若IDE没有显示，点+ → Run Gradle task输入上述值；只删除本新配置的Make，保存后回读XML，不能点“Build, no error check”。
4. 启动客户端前必须先通知用户将弹窗；然后Debug该配置。不得截图，除非另获许可。编译错误应停下并报告具体文件/行号，不能跳过compileJava/processResources。
5. 主菜单 Mods应同时显示 chemicalelements、curios 9.5.1、jade 15.10.6、jei 19.69.0.457，无重复ID；不进入已有世界。新增类、注册表与资源变动需要停掉重启同一配置，不承诺热替换。
6. Bare路径另选 **Client - Latest Sources Bare (no optional mods)**，串行启动，前置含`-PideaWithoutOptionalMods=true`；工作目录为idea-debug-bare-r22，三可选mod不加载但chemicalelements正常到主菜单且无NoClassDefFoundError。回到正常配置时再次跑其前置，不能沿用Bare生成参数。
7. 不因本次可见性测试解除水/机器安全门；不打开正式存档、不改正式mods。

## 给唯一integration owner的精确验证顺序（本批未执行）

只在编排交接原工程输出窗口后，位于工程根；以下不作用于build/integration-r22-20261024-0555。每步保存命令/exit/log与当前build及src指纹，失败先停止处理，不能继续拿旧输出启动。

```powershell
# 验证任务图，不启动游戏；Gradle配置阶段仍会写缓存/生成参数，需唯一owner
.\gradlew.bat prepareIdeaClient --dry-run --max-workers=2 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2'
.\gradlew.bat runClient --dry-run --max-workers=2 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2'
# 应含compileJava/processResources/classes/prepareClientRun；不含check/GameTest/jar，不成环
.\gradlew.bat prepareIdeaClient --max-workers=2 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2'
# 完成后由IDE上述新Application真正Debug；不可用CLI代替IDE验收
# 服务端/数据仅检查准备参数，不运行server/data/GameTest；此处不会运行data generator
.\gradlew.bat prepareServerRun prepareDataRun prepareGameTestServerRun --max-workers=2 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2'
# Bare与正常串行，检查缺依赖；随后用Bare新Application Debug
.\gradlew.bat prepareIdeaClient -PideaWithoutOptionalMods=true --max-workers=2 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2'
```

若只需CLI辅助启动、已经告知用户且owner获运行权，可在本次PowerShell进程临时设置JAVA_TOOL_OPTIONS为`-XX:ActiveProcessorCount=2 -Xmx2G`后运行`runClient --max-workers=2 --no-parallel`，在finally恢复原环境值，不持久改用户环境；此辅助路径不是IDE Debug验收替代。不要传-x、不要--refresh-dependencies、不要先build/check。

### 可观察验收点

- 新class：记录当前src/main/java/org/goldgom/chemicalelements/core/VanillaCraftingAdmission.java与生成class hash/mtime，确认class产生；并记录当前主mod类及实际业务断点命中，不以class存在替代已加载。IDE初始化后Evaluate `org.goldgom.chemicalelements.core.VanillaCraftingAdmission.class.getProtectionDomain().getCodeSource().getLocation()` 与 `.class.getResource("VanillaCraftingAdmission.class")`；union/secure-jar包装可接受，但需追到当前build/classes/java/main，不可为稳定jar/快照。仅Class.forName人为加载不证明业务执行。
- 资源：processResources后对明确未展开静态资源做源/输出hash相等检查，例如 `src/main/resources/assets/chemicalelements/lang/en_us.json` 对 `build/resources/main/assets/chemicalelements/lang/en_us.json`。metadata经过展开，应检查build/resources/main/META-INF/neoforge.mods.toml的内容/hash与当前属性及生成输出对应，不能直接和模板hash比较。
- 生成参数：确认build/moddev/clientRunProgramArgs.txt与VM文件当前生成，GAMEDIR/MODSDIR为compat-r17（Bare为bare目录）；fml.modFolders当前classes/resources。Server/Data/GameTest三准备任务生成目录各异，data输出仍为src/generated/resources。不运行runData来冒充普通资源处理。
- 四mod：新运行的run/compat-r17/logs/latest.log记录版本/目录且无duplicate mod ID，主菜单Mods实际四项同在。历史日志不算本轮证据；没有实际主菜单核验就保留“待验”。
- bare：新日志不加载curios/jade/jei，classpath无对应runtimeOnly，chemicalelements到主菜单无可选API链接错误；检查Curios缺席探测空返回，JEI/Jade不注册（所需功能交互另按许可）。第三方jar保留原位/hash不变；不能通过删除/移动jar伪造降级。
- 索引仍13项/hash不变；客户端结束后确认进程已退出再交回窗口。截图仍待明确许可。

## 剩余责任

本配置批已实际落盘且静态检查通过；**IDE重导入接受XML、Gradle解析/编译、最新类实际加载、四mod主菜单与Bare缺依赖启动都未执行**。交编排协调原integration owner在唯一输出窗口完成；本节点不自行抢构建/客户端权。manifest.json索引本批文件hash，不能以manifest存在推导运行可用。若验收发现配置问题，应按具体证据补修，不默默退回旧产物。
