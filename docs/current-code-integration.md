# 当前代码集成记录

最新海岸修复：自然水扫描跳过不含流体的整段区块，稳定满格海水不再因上下接触而入队；挖方块/桶操作及其后续阶段保留优先级，邻居通知继续合并。两格重力任务有独立有限预算，跨边界补水完成后恢复原区域流动；已有后台上游任务同样提升优先级。保留 10 tick、10 格半径及有限库存规则。51 项 JUnit、132 项必需 GameTest 全部通过，构建成功，日志 `build/fluid-coast-test.log`；未对用户客户端重启、写入存档或作截图现场验收。以下为此前记录。

最新流体打磨：重力只阻塞相关格子的横向求解，连续水柱自下向上补位；截断水柱不再充当真实水面，边界上方液体逐级向下补水。50 L 阈值按同高度实际相邻差值触发，对角不连通水面不再互相抬高。保留默认 10 tick 阶段、10 格传播半径和体积/溶质/热量守恒。51 项 JUnit、129 项必需 GameTest 全部通过，构建成功；日志 `build/fluid-polish-test.log`。详情见 [有限水进度](finite-water-progress.md)，客户端画面与自然世界 TPS 尚未实测。以下条目为此前阶段记录。

浮空与阶段更新修复：可替换空碰撞植物不再支撑水；计算区域边界安排逐格重力续作。fluidFlowIntervalTicks 默认 10，每区域独立计时；每阶段先落一格或扩展一层前沿，水平扩散半径仍为 10。45 项 JUnit、126 项 GameTest 通过。

流体最新求解：默认触发半径 10（fluidUpdateRadius，可配置 1–16），范围内同类三维连通水体共享水位，持续接纳可达干格直至无法扩散；每 tick 单区域、稳定后休眠。十级水量通过共享顶点显示连续水面，不绘制内部湿边竖墙。43 项 JUnit、125 项 GameTest 通过；自然世界 TPS 与画面仍待实机复核。

旧存档加载内存修复：流体历史 post-image 以压缩形式驻留，账本 schema 2 支持读取并迁移 schema 1。现场快照及 WAL 副本恢复 15,636 笔事务，再次保存加载通过；38 项 JUnit、124 项 GameTest 通过。

最新流体形态：按实际体积分为十级，邻格与上方水不再拉高水面；协议版本 7。36 项 JUnit、123 项必需 GameTest 通过，客户端初始化成功；受桌面原生管道不可用影响，画面尚未目视验收。详见 [有限水进度](finite-water-progress.md)。

## 接入与修复

- 修复 MachineProjection、OwnerTransferValidation 对 JsonOps 的错误包名引用；统一使用 Mojang 序列化 API。
- 修复 ElementMaterials 注册表泛型，保留实际 MaterialBlock 类型。
- Gradle 守护进程、JavaExec 和 JUnit 测试均使用 Java 21，避免系统默认 Java 25 导致 Gradle 8.8 脚本解析失败。
- 安装真实实例查询、合成和产物预览服务，使高级检测器可以读取实际账本；查询不创建名义实例。
- 注册区块、Section、Palette、Level 和区块网络编码的观察屏障。区块别名采用弱身份引用，避免全局索引永久保留卸载区块。
- 接入玩家完整存档的附件编码审计：审计实际编码调用，重新抛出 NeoForge 捕获并跳过的附件序列化错误，失败后清理准确的序列化能力，不重编码附件、不开放普通库存读取。
- 修复多目标 CraftingMenuBarrierMixin 的 Shadow 映射配置。
- 机器启动先报告实际缺失的多方块零件，再检查生产事务可用性；保留库存不变。
- 普通区块保存保留原版对 removed 方块实体的 pending-NBT 回退，避免卸载阶段误报整块保存失败。
- 将 28 项独立模块验证接入 modulesVerify/check，配置 JUnit Jupiter；依赖实际注册表的两项材质验证转入 GameTest。
- 修正机器日志测试使用空 Lore 的无效夹具：空值被 ItemStack 与默认组件合并，现在用实际保留的非空负载验证拒绝行为。

## 验证入口

```powershell
.\gradlew.bat build --console=plain --max-workers=2
.\gradlew.bat integrationVerify --console=plain --max-workers=2
```

`build` 包含 Java 编译、资源打包、JUnit、核心/实例/机器事务类型验证及 28 项独立模块验证。
`integrationVerify` 额外运行注册的 NeoForge GameTest。测试世界位于 `run/idea-gametest-r22`。

## 仍缺少实现的生产路径

以下部分不是补充注册调用就能完成的功能，本轮没有把它们标为已启用：

有限流体最新性能修复：每 tick 更新，流动决定通过带校验的增量 WAL 追加并落盘，常规保存写兼容快照；区块加载采用流体事务索引恢复。旧快照、WAL 冷恢复与幂等合并已有游戏测试。未满桶与守恒规则保留，详见下方有限水进度链接。

- 机器生产仍缺完整持久化事务执行与恢复接线；现有启动路径明确暂停，产物预览不代表事务提交。
- 有限流体流动和邻块吸收已继续接线：重力优先、每 tick 调度、默认 50 L 水平停流差、0.01 L 微量移出审计、十级水量形态（每 100 L 一级，独立平面渲染与射线形状），支持水、岩浆与注册的 FlowingFluid/LiquidBlock 流体。吸水容量和速度按材质重新分档；满格或部分满格倒桶支持原子溢流，流体类型随库存持久化，不同流体暂不混合或触发原版接触固化。新增创造模式手持化学查看器，支持瞄准摘要和右键分页详情；详见 [有限水进度](finite-water-progress.md)。原有 inhabitedTime 推断和分开写入 tombstone 已移除；来源证明、完整自然水体初始化及更完整的事务边界仍未闭合。溶解、大气、核反应和热交换仍缺对应世界运行端口。
- 世界材质放置/拾取、空气位置所有权、恢复索引、严格单次区块编码与 ChunkStorage 写入顺序尚未闭合。WorldMaterialChunkSerializerMixin 会重复编码附件；WorldMaterialRegionStorageMixin 涉及新的区域发布协议，因此两者没有通过简单注册强制启用。
- 本次注册的世界观察屏障只证明被测试入口的隔离行为，不代表完整的区块事务、断电恢复或材质放置功能已完成。
- 客户端渲染、Curios/Jade/JEI 实机交互没有在无界面服务器测试中验收。

## 首轮验收（后续版本见下方续作记录）

- 命令：`.\gradlew.bat integrationVerify build --console=plain --continue --max-workers=2`。
- 结果：`BUILD SUCCESSFUL`，退出码 0；日志 `build/integration-final-acceptance.log`。
- GameTest：102/102 必需用例通过，包括新增实例服务、附件错误传播、removed BE 保存回退以及原有世界隔离和机器日志投影用例。
- JUnit：10/10 通过（区域发布 4 项、世界边界原语 6 项）。
- 28 项模块独立验证及既有 coreVerify、instanceVerify、machineTransactionsVerify 全部通过。
- 覆盖审计：1333 项原版物品，missing=0；118 元素、138 物种。
- 关服日志确认所有维度区块保存完毕，无 `Failed to save chunk`、`REMOVED_BE_STILL_IN_CHUNK` 或 FATAL。
- 已核对 JAR 中 33 个 Mixin 与新增 GameTest 类，确认 removed BE 修复已进入字节码。
- 产物：`build/libs/chemicalelements-1.0-SNAPSHOT.jar`。
- SHA-256：`2722195E03608691E3BD1B1C63168022B6133D58B2EB26557394F35436C7650E`。

负向测试会主动输出无效数据包、附件编码等错误日志；这些不是被忽略的失败，以各测试断言、最终汇总和进程退出码判断。上述通过不扩大“仍缺少实现的生产路径”一节列出的功能边界。

## TokenBird 日志续作

已根据本机 TokenBird 原任务继续实现 MaterialBlockEntity 世界原根桥、准确区块身份检查、同锁寿命绑定及不可跳过的根状态迁移。新增四项真实 BE GameTest 与三项许可 JUnit 用例，有限热定位器也改用隔离期间可用的准确身份检查。

最新构建与 GameTest 为 106/106、JUnit 为 13/13；带 Curios/Jade/JEI 客户端已实际运行并留有分类创造栏、25 台空闲机器/不同尺寸零件和 GUI 截图。Bare 客户端也已通过实际主菜单/仅三个基础模组列表核对，两种运行均退出 0。最新产物与仍缺少的生产链路见 [TokenBird 日志续作记录](tokenbird-continuation-20261025.md)。材料原根叶桥完成不代表完整空气位置、同步区块持久化与启动恢复完成。
