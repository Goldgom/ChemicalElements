# ChemicalElements

面向 Minecraft 1.21.1 / NeoForge 的化学与材料模组，包含元素与材料数据、反应和相变模型、材料账本、多方块机器及客户端界面。当前为开发快照，完整的世界持久化事务和机器生产仍在实现中。

## 开发环境

- Minecraft **1.21.1**，NeoForge **21.1.264**。
- Java **21**；使用仓库自带的 Gradle **8.8** Wrapper，无需安装全局 Gradle。
- 守护进程、编译、测试和 JavaExec 使用 Java 21。首次构建需要联网下载 Gradle、工具链及依赖。

## 构建与验证

在仓库根目录执行（PowerShell）：

```powershell
.\gradlew.bat build --console=plain --max-workers=2
.\gradlew.bat integrationVerify --console=plain --max-workers=2
```

Linux/macOS 使用 `./gradlew`。构建产物位于 `build/libs/chemicalelements-1.0-SNAPSHOT.jar`。

`build` 包含编译、资源打包、JUnit、核心/实例/机器事务验证以及 28 项独立模块验证；`integrationVerify` 额外启动 NeoForge GameTest，测试世界仅位于 `run/idea-gametest-r22`。2026-10-25 的完整验证为 **106/106 GameTest、13/13 JUnit** 通过，详见[集成记录](docs/current-code-integration.md)与[续作记录](docs/tokenbird-continuation-20261025.md)。

## 运行开发客户端

```powershell
# 不安装可选模组的客户端
.\gradlew.bat runClient -PideaWithoutOptionalMods=true --console=plain --max-workers=2 --no-parallel

# 安装固定版本的可选模组，仅写入工程内的测试目录（需要 Python 3）
python tools/install-compat-r17.py
.\gradlew.bat runClient --console=plain --max-workers=2 --no-parallel
```

安装脚本从 Modrinth 下载 Curios **9.5.1+1.21.1**、Jade **15.10.6+neoforge**、JEI **19.69.0.457**，校验 SHA-512，保留已有且校验通过的 JAR。默认客户端目录为 `run/compat-r17`，无可选模组的目录为 `run/idea-debug-bare-r22`。不要同时传入 `withCurios`、`withJade` 或 `withJei`，以免重复加载。第三方 JAR、存档和运行日志不入库。

IDEA 可导入 Gradle 工程后选择 `.run/` 中的共享启动配置；启动前的 `prepareIdeaClient` 只准备当前源码、资源和运行参数，不执行完整测试。IDEA 本身的断点和 Evaluate 行为仍需独立验收。

## 当前功能边界

已接入实例查询与纯预览、玩家附件编码审计、世界观察屏障、真实材料方块实体原根桥、机器缺件诊断及模块验证入口；元素、材料、机器和多方块零件资源已纳入源码。

以下链路尚未完成，**自然有限水与机器生产仍保持禁用**：

- 耐久 PREPARE 授权，以及空气位置、Section、方块实体映射和元数据的原子发布。
- 严格单次区块编码、统一持久化写入及启动前 journal 索引与恢复。
- 玩家真实 MaterialOwnerAccess、经过服务端认证的放置/拾取/相变入口。
- Windows 文件及目录发布的耐久策略。

现有测试、客户端画面与可选模组加载验证不代表完整生产结算或断电恢复已验收。后续实现须遵守[架构合同](docs/architecture-contract-v1.md)及[世界材料合同](docs/world-material-implementation-r24/CONTRACT-2.md)。

## 目录

- `src/main/`：正式源码、资源和模组元数据模板。
- `src/test/`：JUnit 与独立模块验证；真实世界 GameTest 位于 `src/main/java/.../gametest/`。
- `docs/`：当前集成说明、设计合同与历史实施记录，见[文档导航](docs/README.md)。
- `tools/`：可选依赖安装、数据生成与历史辅助脚本，见[工具说明](tools/README.md)。
- `run/`、`build/`、`logs/`：本地生成内容，由 Git 忽略。
