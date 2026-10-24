# TokenBird 日志续作记录（2026-10-25）

## 恢复依据

只读读取本机 `C:/Users/Goldgom/.tokenbird/workspaces/my-workspace/super-agent/state.json`、`events.jsonl` 和相关 `sessions/*/session.jsonl`。没有改写 TokenBird 状态或重启旧代理。

重点核对：

- `261024-strong-shore` / `chem-content-world-bridge-r25`：宿主 commit.json rename EPERM 后失败；原节点明确未写入工程。原根桥是实际未完成的实施任务。
- `261024-crisp-heron` / world material r24：原根、区块观察屏障、空气位置身份、同步保存和恢复的实际合同，见 `world-material-implementation-r24/CONTRACT-2.md`。
- `261024-vital-beaver`：玩家附件单次严格编码、玩家真实适配器、启动前 journal 索引的剩余边界。附件错误传播已在上一轮接入，其余不能据此视为完成。
- `261024-early-surge`、`261024-smart-coral`、`261024-strong-pond`：最新代码全量编译、同版 GameTest、IDE 和可选模组客户端验证欠账。
- 最新 intent `intent_7f603313daf143feab6ddcc18f3c04c8` 明确解除此前客户端/截图限制，并要求真实执行留证。旧代理的独占文件窗口属于当时的调度状态，没有作为当前工程停工理由。

历史工人元数据中的 `E:/AIProjects/cm` 未使用。本次工程仅为 `E:/Projects/ChemicalElements`。原有 13 条暂存记录及索引 SHA-256 `82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69` 保留，没有 git add/reset/commit。

## 已续作的原根桥

`MaterialBlockEntity` 实现 `WorldMaterialRootBridge`，原 BE 的一个不可变 `MaterialRootState` 仍是唯一材料账本。

- 读/替换先消费准确绑定的单次叶许可，核对原物理根、目标、位置、维度、操作号、全局摘要、原语、线程和同一隔离锁。
- 许可绑定具体锁的寿命。释放后重新以相同操作号/摘要预约，不会复活旧许可。
- 增加仅返回布尔值的区块原 BE 身份检查，不返回原始 map/NBT/材料，不触发加载或 pending BE 实例化。材料和有限热定位器在隔离期间可检查真实身份，普通 getter 继续拒绝。
- `observationRoots()` 包含原 BE 和真实已加载区块，既有 Section/Palette/map 别名随区块被隔离。
- 世界替换沿用 RESERVED → PROJECTED → complete 或 RESERVED → cancel 状态机，不能跳过预约、取消 AFTER 或清空 AFTER lineage。
- 新的、尚未安装/加载的候选 BE 可在同一 chunk/BE 隔离锁下继承位置 incarnation 一次；已加载根不能重新收养身份。候选桥不创建空气 BE，不安装地图条目，不通知邻居，不调用 setChanged/onLoad。
- 载入隔离、原始 unit_stack 的全部组件、FIFO、完整前后摘要及严格 BEFORE/AFTER 判断保持有效。

新增 `MaterialWorldBridgeGameTests` 的四项真实 BE 测试覆盖完整根发布、错许可/线程、取消及 AFTER lineage、未发布候选身份继承、quarantine 和替换 ABA。许可由测试反射创建，仅验证叶原语，不代表 durable PREPARE。新增 `WorldPrimitivePermitTest` 三项 JUnit 测试覆盖许可寿命、单次消费与跨线程拒绝。既有热定位器测试增加区块隔离期间的身份检查。

## 验证证据

最终代码验证命令：

```powershell
.\gradlew.bat integrationVerify build prepareIdeaClient --console=plain --continue --max-workers=2
```

- 退出码 0，`BUILD SUCCESSFUL`。完整日志：`../build/tokenbird-continue-final.log`。
- 同版 GameTest **106/106** 必需用例通过（上一轮为 102）。所有维度区块保存完成，无区块保存失败。
- JUnit **13/13** 通过（上一轮为 10），无跳过；28 项独立模块验证及 core/instance/machineTransactions 验证通过。
- JAR：`../build/libs/chemicalelements-1.0-SNAPSHOT.jar`，SHA-256 `93153f254d5977197075c4bc04ee81971336b55a14d6824d01e1d7e14bad8450`。
- 已核对 JAR 中 MaterialBlockEntity、WorldPrimitivePermit、新增 GameTest 的字节与当前 classes 完全相同；静态语言资源源文件与输出哈希相同。
- 源码、关键 class、JAR、可选模组与索引指纹：`../build/tokenbird-continuation/manifest.json`。客户端运行前后关键输入/产物哈希未变。`git diff --check` 通过。

带依赖客户端实际运行：

```powershell
# 仅在本次命令进程设置 JAVA_TOOL_OPTIONS，结束后恢复。
# -XX:ActiveProcessorCount=2 -Xmx2G -Xlog:class+load=info:file=client-loaded-%p.log
.\gradlew.bat runClient --console=plain --max-workers=2 --no-parallel
```

- 专用目录 `run/compat-r17`，返回 0。三个第三方 JAR 哈希未改变，没有安装到正式 mods 目录。
- 在模组列表中实际看到 ChemicalElements 1.0-SNAPSHOT、Curios 9.5.1+1.21.1、Jade 15.10.6+neoforge、Just Enough Items 19.69.0.457。
- `optional-loaded-classes.log` 记录 Chemicalelements 和 MaterialBlockEntity 从 `union:/E:/Projects/ChemicalElements/build/classes/java/main/...` 加载。实际界面打开后，MachineScreen 也从本次 classes 加载。
- 当前运行 `optional-latest.log` 无 ERROR 行、无缺模型/缺贴图告警；实际查看的创造栏、机器/零件场景及 GUI 未见紫黑贴图。存在原版 shader Sampler2 和 Curios 开发环境 refmap 警告，不将其写成“所有 WARN 清零”。
- 新建专用平坦世界 `TokenBird-Continuation-20261025`，场景生成函数仅位于该世界的 datapacks 中。命令生成的材料根为空账本、机器为空闲状态；这是外观场景，不是物质放置或生产验收。没有设置伪运行状态。
- 真正打开分析台 5×5 GUI，点击开始后得到真实的 `Structure: INVALID: Missing ...` 诊断。完整物品栏、输出槽、按钮和储罐条可见；未宣称 1..9 全部布局已经实机验收。
- Jade 世界信息层与 JEI 物品侧栏实际显示；没有把这些存在性结果扩大为 Curios 装备授权、Jade/JEI 全流程或机器产出验收。
- 退出时记录测试世界所有维度区块保存成功，客户端正常退出。

可核对截图（均为真实游戏窗口裁图）：

| 内容 | 证据路径（相对工程根） |
| --- | --- |
| Curios/Jade 列表 | `build/tokenbird-continuation/client/05-mods-confirmed.png` |
| ChemicalElements/Jade/JEI 与版本 | `build/tokenbird-continuation/client/28-mods-all.png` |
| 多方块零件分类 | `build/tokenbird-continuation/client/18-creative.png` |
| 元素分类 | `build/tokenbird-continuation/client/19-elements.png` |
| 材料分类 | `build/tokenbird-continuation/client/20-materials.png` |
| 机器分类 | `build/tokenbird-continuation/client/21-machines.png` |
| 25 台空闲机器、六类不同尺寸零件、朝向与材料样式 | `build/tokenbird-continuation/client/24-gallery-overview.png` |
| 分析台真实 GUI | `build/tokenbird-continuation/client/25-machine-gui.png` |
| 启动后的真实缺件诊断 | `build/tokenbird-continuation/client/26-structure-diagnostic.png` |
| Bare 实际主菜单 | `build/tokenbird-continuation/client/33-bare-main-menu.png` |
| Bare 实际模组列表（仅 Minecraft/NeoForge/ChemicalElements） | `build/tokenbird-continuation/client/34-bare-mod-list.png` |

Bare 客户端实际运行：

```powershell
.\gradlew.bat runClient -PideaWithoutOptionalMods=true --console=plain --max-workers=2 --no-parallel
```

- 专用目录 `run/idea-debug-bare-r22` 的 mods JAR 数量为 0，没有移动或删除正常目录的三个依赖。
- 首次启动到原版无障碍引导，第一次截图未算作主菜单验收；随后重新启动，实际点击 Continue 后进入主菜单、打开模组列表核对只有 Minecraft、NeoForge、ChemicalElements。
- 本次完整日志 `../build/tokenbird-client-bare-final.log`，退出码 0；复制的游戏日志 `../build/tokenbird-continuation/client/bare-latest.log` 无 ERROR、无可选 API 链接异常。原版声音事件、开发环境资源 URL、shader 的警告不属于缺模型告警。
- 关键源码/classes/JAR、原三个可选模组和索引哈希在两种客户端检查后保持不变；manifest 另外记录 16 个已确认的客户端证据文件的 SHA-256。引导过程截图没有混入最终验收图。

历史 `integration-r22-20261024-0555` 未作为本轮产物使用。以上 CLI 验证没有冒充 IDEA 点击 Debug、断点或 Evaluate 验证；共享配置的 IDE 导入和调试行为仍需独立实测。

## 仍需继续的生产实现

1. 世界根桥的后续调用者：真实耐久 PREPARE 能力、空气位置元数据的原子发布、完整 Section/BE map/位置状态共同切换，以及严格单次 chunk 编码和统一写入顺序。
2. 玩家真实 MaterialOwnerAccess、玩家/区块第一次公开观察前的权威 journal 索引与逐项恢复、服务端认证的 place/pickup/phase 入口。
3. Windows 真实目录/文件发布耐久策略；现有目录 force 失败仍明确失败，不能用 setChanged、无操作回调或“文件看起来写入”代替耐久成功。
4. 在以上前提成立后，才接自然有限水、默认浸润、跨区块联合 CAS、热/环境 Owner 及机器真实生产。水和机器生产门保持关闭。
5. 客户端画面和可选模组存在性验证不等于 25 台机器生产、1290 条配方真实结算、装备授权、JEI/Jade 全流程或断电恢复已经验收。

原根桥已实现不等于整条世界事务已可用于生产。`MaterialOwnerAccess.isolationGap()` 和 `persist()` 仍报告实际缺口，未把接口接通冒充持久提交。
