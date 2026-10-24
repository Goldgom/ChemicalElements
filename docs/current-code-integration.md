# 当前代码集成记录

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

- 机器生产仍缺完整持久化事务执行与恢复接线；现有启动路径明确暂停，产物预览不代表事务提交。
- 自然有限水、溶解、吸附、大气、核反应和热交换的世界运行端口仍缺真实持久化 Owner。旧 WaterStorePort 的 inhabitedTime 推断与分开写入 tombstone 不满足现有合同，不能直接作为新水体生产实现。
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
