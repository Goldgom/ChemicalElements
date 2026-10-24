# IDEA Debug 最新源码 + Curios/Jade/JEI：r22 有界诊断

时间基准：用户指定 2026-10-24 06:09 EDT。仅诊断准备完成；**配置未修改、未编译、未运行、未截图，不声称问题已修复**。

## 边界

工程唯一 E:/Projects/ChemicalElements。仅新建本 docs 目录写报告、证据摘要和候选文本；没有操作正式 mods/存档，没有复制/修改第三方 jar，没有 git add/reset/commit，没有 Gradle/javac/client/桌面/截图。A/B 原 src 并行写，未冻结它们。已读当前 chem-idea-debug-r22 计划及 chem-integration-r22-snapshot rev2：固定快照 build/integration-r22-20261024-0555 与构建配置/Gradle/classes 仍归原 integration owner，本节点不接管。后续必须由编排正式交接原 owner 再实施。

## 实查结论（证据与假设分开）

### 已证：第三方缺失是运行目录错位

- build.gradle 使用 `net.neoforged.moddev` **2.0.148**；NeoForge **21.1.264**；Java toolchain **21**；wrapper Gradle **8.8**；settings 的 foojay convention **0.8.0**。
- `.idea/runConfigurations` 和 `.run` 均不存在。现有配置实际保存在 `.idea/workspace.xml` 的 RunManager，磁盘 selected=`Application.Client`。本目录仅保存所需配置摘录，未复制整个 workspace。
- `Client` / `Data` / `Server` / `GameTestServer` 都是 Application：主类 `net.neoforged.devlaunch.Main`，模块 `ChemicalElements.main`，before-launch 只有启用的 **Make**。此主类来自实际配置，并非按 NeoForge 经验硬编码。
- Client workingDirectory=`$PROJECT_DIR$/run`；program args=`@E:\\Projects\\ChemicalElements\\build\\moddev\\clientRunProgramArgs.txt`；VM args 引用同目录 clientRunVmArgs.txt，并显式 `-Dfml.modFolders=chemicalelements%%E:\\Projects\\ChemicalElements\\build\\classes\\java\\main;chemicalelements%%E:\\Projects\\ChemicalElements\\build\\resources\\main`。
- clientRunProgramArgs.txt 内部下一层主类是 `cpw.mods.bootstraplauncher.BootstrapLauncher`，target=`forgeclientdev`，`--gameDir .`。它由 DevLaunch 读取，不应把 Application 主类直接替换成 BootstrapLauncher。
- `run/mods` 存在但 **0 文件**；三 jar 只在 `run/compat-r17/mods`。磁盘 Client 因 `--gameDir .` 解析到 run，不会自动扫描 compat-r17/mods。
- build.gradle 仅在 `-PcompatR17=true` 时改变 Gradle client 的 gameDirectory；三个 runtimeOnly 开关默认 false，生成的 client/server/data legacy classpath 均未命中 Curios/Jade/JEI 路径。
- install-manifest 与本次三 jar SHA256 全匹配（下表）；无需重复安装。

| 文件（均在 run/compat-r17/mods） | SHA256 |
|---|---|
| curios-neoforge-9.5.1+1.21.1.jar | A45DF2125C26219974ABA7507FFC9AFE7B83ACC941A386AF3FAACB1CC0056FDE |
| Jade-1.21.1-NeoForge-15.10.6.jar | 276F1F65A20D04F536989C4F311949D770B52C440C23C2B904A08860F3C93810 |
| jei-1.21.1-neoforge-19.69.0.457.jar | 5F09D199DBA1265E131EF1672597EA07857220E4CC2FF1DC754E8BD0E6378925 |

### 已证：当前输出不包含全部最新源码；并非证实 IDE 跑旧 JAR

- 磁盘 Client 已绑定 **classes + resources**，不是 build/libs JAR；不能把根因写成“IDE 指向旧 jar”。
- `src/main/java/org/goldgom/chemicalelements/core/VanillaCraftingAdmission.java` 已存在，mtime **06:02:58 EDT**，SHA256 **AEFA8798EA8D4B7F375D283F728D6B2599DF57E122DEF55E9B252F47AA8B1E97**；对应 `build/classes/java/main/.../VanillaCraftingAdmission.class` **不存在**。当前 classes 明确没有该新增成果，但它不是自动注册物品，不可把看不到它的物品当 UI 检验。
- 主模组 Chemicalelements.java 的 class 样本 mtime **01:15:38 EDT**，SHA256 **4B6C143F94A797ABB016469C6EF46C42224962FAE2089B570831BE7297AD59A6**。mtime/hash 只说明输出版本，不证明 IDEA 调试这一刻真的加载了该 class。
- workspace 的 Client 无 Gradle 参数，不存在 `-x compileJava` / `-x processResources`；历史手工命令不能直接归因为用户 IDE 命令。
- `.idea/gradle.xml` 没有 delegatedBuild/testRunner/gradleJvm/buildAndRun 等明确值；main.iml 仅 Facet/Mcp 信息，没有 compiler output 或 sourceFolder。Make 到底委托 Gradle，是否输出到 build/classes，用户是否选择了别的内存配置，磁盘证据**不可推断**。不用桌面读取内存状态。
- 需要消除的不确定性是“只有 Make、没有确定生成 classes/resources 和启动参数的 Gradle before-launch”。候选提供显式 prepareIdeaClient，而不是绕过编译用旧产物。

### 资源与隔离

- sourceSets.main 与 neoForge.mods.chemicalelements.sourceSet(main) 绑定；main.resources 加入 src/generated/resources，并把 generateModMetadata 任务输出加入资源源目录；metadata 输出 build/generated/sources/modMetadata，实际 processResources 产物 build/resources/main/META-INF/neoforge.mods.toml 已存在。
- runData 生成参数写 `src/generated/resources`，existing 为 src/main/resources；本次该生成目录不存在。并非所有当前静态资源都依赖 runData；**不要为了普通 Debug 强制先 runData**。新生成后需 processResources 才会进入运行资源输出。
- 当前 Server/Data/GameTestServer 的 IDEA workingDirectory 也为 run，且生成 program args 使用 `--gameDir .`。候选只隔离其 gameDirectory，不改 data 的输出路径，不把可选 jars 加入全局 runtimeOnly。
- 可选性静态证据：compileOnly 依赖；DetectorAccess 在调用 CuriosDetectors 前 isLoaded("curios") 守卫，缺失返回 Optional.empty；mods.toml Curios 为 optional，Jade/JEI 不为 required。此不足以证明所有缺依赖启动路径安全，必须另跑 bare 客户端（未运行）。安全门源码不修改，水系统与机器生产仍禁用。

## 最小候选修复（未应用）

1. `build.gradle.candidate.patch`：从本次实际 build.gradle 精确生成 unified diff。
   - client 默认复用工程 **run/compat-r17**，不复制 jar、不触碰已有测试数据；不启动/修改既有世界。
   - 拒绝 withCurios/withJade/withJei runtimeOnly flags 与目录加载同时使用，防止重复 mod ID；不把兼容库变成必需依赖。
   - `-PideaWithoutOptionalMods=true` 改用空的 run/idea-debug-bare-r22，缺 jar 仍可进入启动流程，不主动 throw 缺文件错误；真正优雅降级仍待实跑。
   - server/data/gameTest 分别使用 run/idea-server-r22、run/idea-data-r22、run/idea-gametest-r22，不污染或删除原 run 世界。
   - prepareIdeaClient 依赖 **classes + prepareClientRun**；classes 含 compileJava/processResources。prepareClientRun 名称由现有 prepare-client.log 真实执行成功证实。没有 stable jar/check/GameTest 前置。
2. `Client_Latest.run.xml.candidate.txt`：基于磁盘 Client 构造的共享 Application 模板。实施位置建议 `.run/Client_Latest.run.xml`（新建，不覆盖 workspace）。保留实际主类、main 模块、args 路径与 fml.modFolders；workingDirectory 改 compat-r17；以 Gradle prepareIdeaClient 替代不明确的 Make，参数限制 2 workers / no parallel。
3. XML 的 Gradle.BeforeRunTask 序列化按常规 IDEA 格式准备，但本机 IDEA 实际导入尚未验证；若没有显示任务，按下列 UI 步骤建立，保存后回读真实 XML。模板存在不是已可运行证据。
4. 模板复用本机实际绝对路径，适用本工程，不是跨机器可携配置；插件重导入可能生成 Client 而非覆盖新的独立名称。两配置必须保持工作目录一致，不误点旧 Client。

静态检查：生成脚本只写本 docs；`git apply --check` 初次失败（原文件 CRLF 与 Python diff LF 差异），`git apply --check --ignore-space-change docs/idea-debug-r22-diagnosis/build.gradle.candidate.patch` **exit 0**。未应用补丁。后续建议按精确替换块/保持 CRLF 的编辑实施，不执行会覆盖其他改动的整文件替换。Groovy 配置解析和 IDEA before-launch 尚未运行。

## 后续实施顺序与用户 IDEA 点击

必须先由编排取得原 integration owner 对 build.gradle/Gradle/classes 输出的正式交接，核 build.gradle hash 尚为 **D90812F697B5FB467158DCF489C49396D1661C51D084FF1A1D34428A87C255B0**；否则重新生成 diff，保留并行源码。

1. 应用限定候选改动、新建共享 run 配置；不要手改 build/moddev 生成参数。
2. IDEA Gradle 工具窗口 → **Reload All Gradle Projects**。不使用 --refresh-dependencies，不重新安装三 jar。
3. Run → Edit Configurations → 选择 **Client - Latest Sources + Curios Jade JEI**。检查 JDK=21；Main=实际 DevLaunch；Use classpath of module=ChemicalElements.main；working directory=$PROJECT_DIR$/run/compat-r17；保留两 @args 及 fml.modFolders。
4. Before launch → 若自动导入未显示：+ → Run Gradle task → 工程本根，Tasks=prepareIdeaClient，Arguments=--max-workers=2 --no-parallel。删除该新配置的 Make，避免双构建。**不得 Build, no error check / 强制继续错误**。
5. 点击该新配置的 Debug。由团队启动窗口前先通知用户；截图另待许可。源码编译错误要显示具体文件/行号并停止，不偷偷退回旧 class/JAR；“未通过 GameTest也可运行”不等于“Java 编译错误也能运行”。
6. 热重载仅普通方法改动可能成功；新增类/注册表/资源请停止重启此配置，确保再次执行 before-launch，不承诺全部热加载。
7. 主菜单 Mods 中确认 chemicalelements + curios + jade + jei 的版本。此轮不打开已有存档；需功能观察时另建经协调的专用世界。

## 交接后验证命令与可观察通过条件（本次均未执行）

在工程根、拿到输出锁之后：

```powershell
# 不起窗口：应包含 compileJava/processResources/classes/prepareClientRun；不含 check/GameTest/jar
.\gradlew.bat prepareIdeaClient --dry-run --max-workers=2 --no-parallel
.\gradlew.bat prepareIdeaClient --max-workers=2 --no-parallel
# 默认命令不可含 -x compileJava/-x processResources，不要求 build
# 运行前先告知用户将弹窗，然后由IDE新Application Debug（优先用于断点验证）
# CLI备用同类路径（不是IDE验收替代）：
.\gradlew.bat runClient --max-workers=2 --no-parallel
```

- 编译前后记录 Admission.java/class 的时间、SHA256，确认 class 新生成。processResources 后 hash META-INF/neoforge.mods.toml 及选定最新资源，与当前源实际生成/展开内容对应。仅时间变新不足以说明内容相同。
- 在 IDEA 项目实际执行的初始化断点 Evaluate：`org.goldgom.chemicalelements.core.VanillaCraftingAdmission.class.getProtectionDomain().getCodeSource().getLocation()`，并取 `.class.getResource("VanillaCraftingAdmission.class")`；CodeSource 可能为 NeoForge secure-jar/union 包装 URL，应追到 build/classes/java/main 对应条目，不要求必须字面 file URL。同时停在最新方法行/检查字节码对应版本，避免仅 Class.forName 人为加载一个从未调用的类即称业务已见。
- 记录真实新日志 GAMEDIR/MODSDIR；modFolders 必须是当前工作区 classes/resources，不能指到 integration 快照或 build/libs 稳定 jar。
- 新日志必须列出四 mod 与版本，且不存在 duplicate mod ID；主菜单 Mods 同时可见才叫调试可见性通过。历史 client15.log 的四 mod 行只说明旧兼容环境曾加载，不替代新验收。
- bare 验证不移动/删除现有 mods：`prepareIdeaClient -PideaWithoutOptionalMods=true`；克隆新配置为 Client - Latest Sources Bare，workingDirectory 改 run/idea-debug-bare-r22、before-launch 同加此 property；核新 bare mods 为空、classpath 无可选 runtimeOnly，不加载三 mod但 chemicalelements 正常启动，缺失集成返回空/不注册。无需进入旧世界。主路径与 bare 路径串行，以免写同一生成 args 和输出。
- server/data 仅做 dry-run 及生成参数/目录检查，不能因隔离测试顺带写 src/generated 或启动服务器；真正 runData 运行会生成资源，应另获分配后执行，再 processResources。
- 对三 jar 复核 hash 不变；index 仍 **82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69**。本任务只读取 index，并未重复15项恢复预检或旧28测试。

## 证据文件索引

- evidence-files.json：实际配置、生成参数、历史 manifest/log、源码和缺失class、index 的 SHA256/mtime/存在性。
- idea-runmanager-excerpt.xml.txt：仅 Client/Data/Server/GameTestServer/Build；不含其他工作区信息。
- run-layout.json：run/mods 空目录、缺少共享配置/生成资源、三 jar 清单 hash。
- historical-load-excerpt.txt：原 client15.log 的行号/目录/四 mod，明确历史证据。
- build.gradle.candidate.patch、Client_Latest.run.xml.candidate.txt、prepare-candidates.py：未应用候选与可复现生成器。
- artifacts-sha256.json：本目录交付物自身哈希，生成后不再改这些文件。

剩余责任：编排交接唯一构建 owner → 实施/重导入/新源码编译 → IDEA真实Debug与bare恢复路径验证 → 新日志与四mod可见证据；本报告不替代这些步骤。
