# ChemicalElements 实现接口契约 v1.2（goal rev12）

**发布前重新读取goal已更新为rev8。§8新增契约与以下修订为当前依据，旧rev7未决问题已结束。**

任务：chem-architecture-current；依据 goal intent_7acd32bf6aea4d54992b310d9dab6088 rev7。此文件是实现派工输入，**不是模组完成证据**。全部路径相对 `E:\Projects\ChemicalElements`，不操作作废路径。

## 0. 已核实基线与证据

先读取 completed `chem-project-baseline`（报告 report_1d963d5d56c74c0c9e41c245613da91c、板 board_38fc927c6fa9448182d5ebc6e33e4a85），再读取当前共享 goal rev7。本轮另实读 build.gradle、clientRunProgramArgs.txt 和本地 NeoForm 源码缓存。

- MC **1.21.1** / NeoForge **21.1.264** / Java **21** / Gradle **8.8** / ModDev **2.0.148**；modid `chemicalelements`，基础包 `org.goldgom.chemicalelements`（下称 P）。保留现有项目、官方命名、DeferredRegister 风格。
- 现有 `Chemicalelements.java`、`Config.java` 为示例；一个示例方块、其 BlockItem、食物 example_item、创造标签，无化学/机器/网络实现。现有 DataGen/GameTest 是运行入口不是功能。原13个暂存新增文件不重置、不覆盖用户修改。
- 本轮方法证据来自 `C:\Users\Goldgom\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_3e4c78eef6c73c20ac3503f6985d66397d7eb584_output.jar`；MANIFEST 明确 NeoForm `1.21.1-20240808.144430`、NeoForge `21.1.264`。这是实际匹配版本源码检查，不是运行验证。

## 1. 公共领域接口（实现者 A 独占）

以下为拟新增类名/签名，不冒充已存在 API。P 下独立 `api/`，只暴露不可变记录、接口和错误类型；其余工作包依赖 api，禁止彼此调用内部实现。

| 类/接口 | 最小契约 |
|---|---|
| `api.chem.MolAmount` | `BigInteger quanta`，1 quantum = 10^-18 mol；`parseDecimal(String)`、精确 add/subtract/compare；输入负值/超精度拒绝；不是 double 累加 |
| `api.chem.SpeciesDefinition` | ResourceLocation id、元素原子计数字典、整数 charge、摩尔质量 g/mol、相态、真实性标签/出处；电子为电化学特殊载流子，不算元素 |
| `api.chem.Composition` | 排序不可变 `Map<ResourceLocation,MolAmount>`；零项删去；`totalMol()`、`mass(snapshot)`、`massFractions(snapshot)`、`meanMolarMass(snapshot)`；总 mol 是各组分粒子 mol 之和，不误称原子总 mol |
| `api.instance.InstanceRecord` | `UUID id, Composition remaining, Provenance origin, long revision`；相同物品不同实例不得平均化 |
| `api.instance.StackLedger` | `schemaVersion, revision, ordered List<InstanceRecord>`；稳定态 `instances.size == stack.count`，RLE/组成字典只压缩存储，逐项身份仍可重建 |
| `api.instance.StackChemistry` | `resolve(ItemStack, CreationContext)`、`snapshot(ItemStack)`、`split(source,n)`、`transfer(source,target,n,TransferContext)`、`consume(source,Selection,ConsumptionReason)`、`canMerge(a,b)`；真实更改仅服务器线程 |
| `api.tx.ChemistryTransaction` | `preview -> validate(expected revisions/capacity) -> commit`，失败不产生半次写；返回物质/能量/废物收支与实际数量。simulate 不 dirty、不分配新实例、不推进 RNG |
| `api.world.EnvironmentSample` | dimension/biome/y/geology/邻近水或岩体、worldgen seed派生值；生成一次，移动后不重采样 |
| `api.fluid.SolutionState` | `waterMol, dissolved Composition, suspended Composition, temperatureK, volumeLitres, origin, revision`；体积策略显式，不能用溶质 mol 代替溶剂体积 |
| `api.fluid.SolutionAccess` | `snapshot(Location)`、`previewTransfer(from,to,Volume)`、`commit(plan)`、`dissolve(target,solid,dt)`；统一桶、世界格、管道、机器 |
| `api.reaction.ReactionEngine` | `evaluate(ReactionContext, RegistrySnapshot) -> List<ReactionProposal>`；proposal含方程、extent、能量、速率/平衡依据，`commit`复用事务，不直接改背包 |
| `api.data.ChemistryCatalog` | 原子获取 immutable `RegistrySnapshot(epoch,contentHash,definitions...)`；所有长运行配方保存快照版本 |
| `api.ui.ChemistryViewService` | `page(ViewTarget,expectedRevision,page,size) -> ViewPage`；UI只查询，没有客户端自报 mol setter |

数值约定：持久化/JSON 的 mol 为十进制字符串，网络为有上限长度的整数编码；128位有效量级上限作为输入防护（不是底层 long 溢出）。质量采用 BigDecimal/DECIMAL128 计算、显示才舍入，原子量本身是实测近似。反应整数计量数：extent 下取整到 quantum；分流用确定性最大余数法分配，各组分余数留源端，**禁止每端独立四舍五入制造物质**。低于quantum的速率积分累计在持久化残差（非可提取物质），达到阈值才转移。存量不负，元素和电荷逐事务精确核对；水体积、温度、活度等连续近似与守恒账本分离。

## 2. 数据 schema 与重载

目录：`data/<namespace>/chemicalelements/{elements,species,item_compositions,environment_profiles,solubility,reactions,multiblocks}/<id>.json`；ID来自namespace+路径，不要求第三方写 chemicalelements 命名空间。Schema统一 `schema_version:1`。MC1.21.1标准配方仍在 `data/<namespace>/recipe/`；不要沿用旧版本复数路径。

```json
{"schema_version":1,"atomic_number":26,"symbol":"Fe","molar_mass_g_mol":"55.845","basis":"empirical","source":"source-reference"}
```
```json
{"schema_version":1,"atoms":{"chemicalelements:sodium":1,"chemicalelements:chlorine":1},"charge":0,"phase":"solid","molar_mass_g_mol":"58.44277","basis":"empirical"}
```
```json
{"schema_version":1,"match":{"items":["minecraft:iron_ingot"]},"priority":100,"per_instance":{"chemicalelements:iron":"1.0","chemicalelements:carbon":"0.002"},"environment_profile":"chemicalelements:ore_impurities","basis":"game_convention","notes":"每件对应mol为玩法标定，不是现实铁锭固定质量"}
```
```json
{"schema_version":1,"dimension":"minecraft:overworld","biome_tag":"minecraft:is_ocean","y":{"min":-64,"max":320},"water":{"chemicalelements:sodium_ion":{"mol_per_litre":"0.47"},"chemicalelements:chloride_ion":{"mol_per_litre":"0.47"}},"basis":"game_convention","sampling":"seeded_once"}
```
```json
{"schema_version":1,"solid":"chemicalelements:sodium_chloride","products":{"chemicalelements:sodium_ion":1,"chemicalelements:chloride_ion":1},"model":"concentration_limit","limit_mol_per_litre":"6.0","reference_temperature_k":"298.15","valid_temperature_k":["273.15","373.15"],"basis":"game_convention"}
```
```json
{"schema_version":1,"type":"chemicalelements:neutralization","reactants":{"chemicalelements:hydrogen_ion":1,"chemicalelements:hydroxide_ion":1},"products":{"chemicalelements:water":1},"conditions":{"phase":"aqueous","temperature_k":["273.15","373.15"]},"thermodynamics":{"model":"equilibrium_constant","log10_k":"14.0","reference_temperature_k":"298.15"},"kinetics":{"model":"instant_equilibrium"},"basis":"empirical","source":"reference-needed-before-release"}
```

示例只定义格式；未附真实来源的经验条目不能以科学已验证发布。严格检测118元素 atomic_number=1..118唯一连续；短寿命元素用标注的代表同位素质量，不虚称标准原子量。元素实体/物品在注册阶段注册118项，数据包仅改科学参数/组成，不在 reload 时动态添加注册物品。全部 `minecraft` Item 注册项（含特殊、不可堆叠及技术物品；air显式无物质）必须有明确映射，报告 missing/duplicate/冲突。不得用一个默认含碳条目假装全覆盖；幻想物品显式 game_convention。按rev8重写原版不守恒配方；幻想材料虽标game_convention，合成/烧炼/回收仍须守恒。

`P.data.ChemistryReloadListener` 用 `AddReloadListenerEvent` 注册；prepare 解析和交叉引用/原子电荷校验，apply服务器线程一次交换快照；错误保留旧快照并报文件+JSON路径。ID覆盖服从资源包优先级；match额外priority，同优先级冲突报错。reload不重采样已存在实例。删物质ID不擦除存量：旧快照定义作为存档墓碑保留，显示orphaned，禁止未知反应直到迁移。

组件、chunk附件、机器NBT分别有schema_version；`P.data.MigrationChain` 执行纯函数 n→n+1，加载前备份策略由整合者接入；新版本不识别时隔离并明确错误，不当空值重建。运行中反应保存epoch和输入预留版本，reload时重验证或取消退还，不拼接两个配方版本。

## 3. 实例与异组成堆叠：不可跳过的完整生命周期（B负责）

存储：注册 `DataComponentType<StackLedger>`，持久化 Codec + 网络 StreamCodec（有限长度、不可变值）；放在 ItemStack组件而非旧式根NBT。普通方块实例在 `LevelChunk` 附件 `ChunkChemistry` 按BlockPos稀疏保存，记录已初始化/已耗尽墓碑、来源、残余组分；BE仍调用同一实例服务。附件内部变更必须 `setUnsaved(true)`；不只依赖对象引用没变的检测。可对天然实例懒求值，但生成上下文/规则epoch必须在生成或受控升级时固定，不能看当前玩家周边重掷。旧存档无标记的水是 legacy_unknown，不冒充天然初次生成。

- 生成/掉落表/合成/创造物品各有 CreationContext。首次采样生成独立实例id和混合量；同一物件的放置→破坏→拾取保持剩余量，不按原型补满。多掉落由原实例分配，额外战利品明确游戏源，不能给每份完整原物质。
- 合并：目标尾部追加源端前n个实例，数量仍遵守原版maxStackSize；比较 item 及**所有非化学组件**（名字、附魔、损伤等），不同化学量允许合并；不放宽原版其它限制。
- 拆分/快捷移动/拖拽/漏斗：确定性FIFO切片，原子移除源端与追加目标；逐实例选择UI若提供移动动作必须按id+revision服务器验证；默认查看UI不改变FIFO。交换整堆是转移对象，不重复生成。
- 消耗：从选中实例扣准确物质量；部分溶解仍保留1件和剩余量，可加残余视觉覆盖，不创造每mol一件。剩余为零才减count/去除方块；破坏残余方块只掉残余量。耐久磨损是否消耗材料由显式规则；未知磨损不凭空补充。
- copy是快照，不是所有权转移；copyWithCount不能凭增加count复制真实物质。创造/命令生成显式新来源；预测/模拟副本从不提交。`setCount/grow/shrink` 是底层工具，不能靠它们猜来源实现守恒。所有真实库存边界进行账本数量审计，异常保留证据而不静默按模板填补。
- 合成/熔炼/燃料：输入实例消耗和结果组分分配、容器返还、副产物、排放组成同一事务；按rev8重写不守恒的原版合成/熔炼/回收配方，输入输出、副产物、返还均需配平；不能用game_convention源汇绕过，也不能通过copy旧账本挂新item。死亡/丢弃/拾取/容器保存/卸载加载保留同一账本；死亡非keepInventory时只移交一次。

### 已查证1.21.1钩子矩阵

本地版本源码：`ItemStack.isSameItemSameComponents`(611–616) 比较整个components；`copy`(552–558)复制组件，`copyWithCount`(562–568)随后setCount；`split`(308–311) copyWithCount再shrink。这证明**只忽略NBT/组件比较必然不足**。

推荐对**转移调用点**使用 `StackChemistry.canMerge + transfer`，保留全局 `isSameItemSameComponents`/`matches`/hash 的真实全组件语义，避免同步差异、配方缓存、相等hash契约被破坏。不能全局让账本equals恒真。客户端预测的相关合并判定也使用相同非化学比较，但权威账本仅由服务器提交。

| 已查证真实类/方法 | 必须改接的操作 |
|---|---|
| `AbstractContainerMenu.clicked`、内部点击路径、`moveItemStackTo` | 普通点击、右键、双击收集、QUICK_MOVE、QUICK_CRAFT拖拽、SWAP、THROW、CLONE；原路径存在copyWithCount与grow/setCount，逐分支接事务，不能只修快捷移动 |
| `Slot.safeInsert` / `tryRemove` / `remove` | 相邻shrink/grow替换带来源的转移；避免和外层重复扣账（事务作用域/重入标识） |
| `Inventory.addResource` / `add` / `placeItemBackInInventory` | 拾取、返还、背包自动归并；addResource有copyWithCount(0)模式 |
| `ItemEntity.merge(ItemStack,ItemStack,int)` / `playerTouch` | 掉落堆合并、拾取剩余；merge源码用目标copyWithCount+源shrink，原实现不能保留异组成 |
| `HopperBlockEntity.tryMoveInItem` / `tryTakeInItemFromSlot` / `addItem` | 漏斗和容器间单件/批量转移、失败回滚 |
| NeoForge `ItemStackHandler.insertItem/extractItem`、`ItemHandlerHelper.insertItemStacked` | 模拟与真实路径；extract的两份copyWithCount须切为互补切片而非重复前缀 |
| `ItemStack.split/copy/copyWithCount/setCount` | 定义与记录复制/数量变动边界，但不能以全局grow钩子替代上述有来源上下文的转移 |

以上方法存在已验证；具体Mixin descriptor、注入ordinal、兼容其它Mixin及全部菜单子类还须 B 以该jar编译/游戏测试确认。B还必须核对并覆盖合成ResultSlot、熔炉结果/燃料、容器工具类、桶、活塞移动、爆炸/掉落、死亡和配方自动填充的实际调用链（尚未逐行核实，不能称已适配）。`P.mixin`负责原版/NeoForge通用钩子；第三方自定义IItemHandler不是自动安全，提供 `LedgerItemHandler`适配器和能力测试，已知插件逐项适配。拒绝宣称任意mod兼容。Mixin require校验和启动探针失败须阻止“化学已启用”状态，不能无声丢物质。

## 4. 自然水、溶解、反应数据流（C负责）

`世界生成上下文 → ChunkChemistry初始化记录 → SolutionAccess → 溶解/ReactionProposal → ChemistryTransaction → dirty + 网络差分`。

自然水只在有天然生成证据且未初始化时按群系/环境确定溶质；桶装/机器/命令/无限水转换的来源分别记录。桶和管道转移液体量与每组分mol，冻融/放置/破坏不重新加盐。跨chunk事务只在两边已加载可写时提交，不强制持续加载整片海洋；持久化事务ID和源/目标版本，重放幂等，崩溃恢复不双写。溶液网格调度事件驱动+每tick预算，dirty邻域队列，不扫描世界全部方块。

溶解限量 `min(固体可用量, 饱和余量, 表面积/温度/时间速率限额)`；先求proposal再扣固体、加溶液。电解质拆分为离子并检查电荷；多离子共同离子效应/温度/Ksp模型与简单浓度阈值分开标识。drop ItemEntity 与方块残余用同一个算法，不能掉落一次就整堆删除。无法可靠假设溶解行为的物质保留未建模状态而非全能溶解。

**无限水已核实钩子：** WaterFluid.canConvertToSource(Level)读取 GameRules.RULE_WATER_SOURCE_CONVERSION；FlowingFluid.getNewLiquid(158–193)在相邻源中调用 EventHooks.canCreateFluidSource，足够邻源后返回getSource(false)；NeoForge `CreateFluidSourceEvent.setCanConvert(boolean)`可控制许可。事件位置是参与统计的邻源，**不是最终新源位置**；必须在实际流体状态写入处确认“非源→新源”并归因后，才登记新体积为纯水，不能在事件回调给邻源清盐。

**rev8已明确：严格有限水，无override开关，所有路径禁无限水。** gamerule、CreateFluidSourceEvent、最终状态写入和桶/含水方块/机器端口统一阻断造水。旧“强开新增纯水”分支被新需求取代。水位由真实有限体积导出，重写原版流动/含水逻辑；沿重力、压差、化学物性向邻格扩散。先以一致快照求通量，再按每源可用量统一限流，确定顺序提交，原版tick不得额外造水。两侧融合加总waterMol/溶质/热量，再计算浓度，不能平均浓度。每个方块依材质/性质有吸水容量（不吸水者明确0），孔隙水和吸附溶质转入其组成；解吸、干燥、破坏再释放，不能删除或复制。孔隙/吸附池只计一次，不与本体重复。世界生成水是一次有限初始库存，waterlogged布尔值不得直接兑换无限桶。温度相关溶解度、共离子/多组分相平衡、降温/蒸发后析出均为正式范围，独立单盐阈值不足以完成交付。

反应：`P.reaction.types` 至少中和、沉淀/溶解平衡、氧化还原、电解、燃烧，后续合成/分解/络合/气相/有机类型扩展；`P.reaction.library`数据匹配具体物种和条件；规则先筛条件/相态/能量/电极/催化剂，再用热力学/动力学或明确游戏约定计算extent。配平仅校验原子/电荷，不是预测会发生；未知反应不输出“现实不反应”。竞争反应共享资源限额，不能各自消耗同一mol。原子/电荷守恒测试、能量预算和不支持区间诊断必须随库交付。

## 5. 机器、UI、Jade/JEI（D负责）

具体内容建议（不是最终数量上限）：分析台/光谱仪、称量与取样台、研磨机、溶解槽、搅拌反应釜、过滤器、离心机、蒸发结晶器、蒸馏器、分馏塔、多级电解槽、高温炉、加压反应器、气体洗涤塔、热交换器、储液罐、泵与管道。多方块至少分馏塔/电解槽阵列/高温炉/加压反应器/储罐。材料输入输出、温压、能量、阀口和副产物真实接通，不以装饰块代替机器。

`P.machine.MachineController`调SolutionAccess/ReactionEngine；`MultiblockDefinition`记录相对坐标/tag/尺寸/端口/旋转，结构变动失效时停止并保留或退还预留物质。控制器唯一持有库存；从属块只引用controller，拆一块不复制整罐液体。区块卸载暂停、重载重验证；未加载结构不判永久损坏。

`P.network`通过本版本 `RegisterPayloadHandlersEvent`注册有协议版本的 CustomPacketPayload。查询输入 target(containerId/slot 或 dimension/pos)、ledgerRevision、page；服务器验证当前菜单、距离、维度、可见性、页面界限和频率，排入主线程。返回版本/总实例数/页内实例id+组分/真实性标签。默认16项每页、每包字节上限，超长组分页而非静默截断物质。过期显示“已变化，请刷新”；对远程不可达目标不泄露库存。标准组件同步保证库存一致，自定义分页承担详情展示，不通过“相等比较忽略账本”省同步。

`P.client.screen.StackCompositionScreen`提供逐件序号/实例、组成mol/质量/质量分数、相同组成分组但可展开；按键/背包按钮入口。Jade展示看向方块/机器/水的摘要和服务器提供的当前实例；JEI展示元素物种、反应条件、机器配方和典型组成，不伪装实际堆叠实例值。适配类 `P.compat.jade.ChemistryJadePlugin`、`P.compat.jei.ChemistryJeiPlugin`。当前依赖未装，具体1.21.1 NeoForge API与版本由D验证后向A提交坐标；不要照抄build.gradle中旧forge示例。隔离可选加载，分别测试仅本mod、+Jade、+JEI、两者同时、dedicated server无客户端类加载。

贴图由D自行程序化/像素制作，覆盖118元素/物质容器/全部新增机器和多方块端口/GUI图标；生成脚本放 `tools/assets/`，产物assets内，配套blockstate/model/item model/zh_cn/en_us，检查missing texture；原版物品不需要重新绘制。元素可共享视觉模板但必须可辨识且每个注册项有有效引用，不以缺美术延期。

## 6. 四个可并行工作包与公共文件所有权

先由A提交api编译骨架/测试fixture/registry bootstrap（短前置，不改契约语义），B/C/D即可并行；各包提供 `bootstrap(IEventBus)`，A在既有入口一次装配。依赖接口不是等待全部内核完成。其它包不能自行改公共构建文件。

| 包 | 独占目录/文件 | 依赖与自测验收 |
|---|---|---|
| A 数量/数据/全集内容/整合 | `P/api/**, core/**, data/**, registry/**`；`data/chemicalelements/{elements,species,item_compositions,environment_profiles}/**`；`P/gametest/core/**`；公共 `Chemicalelements.java, Config.java, build.gradle, gradle.properties, src/main/templates/**`，以及DataGen总入口 | 精度加减分配随机测试；118完整原子序数；遍历实际minecraft物品注册表0缺失；重载非法JSON/删除ID/迁移；构建与最终所有包集成；公共依赖/mixin配置变更由其他包提交请求给A |
| B 实例/堆叠/方块生命周期 | `P/instance/**, persistence/**, mixin/**`；`P/gametest/instance/**`；`chemicalelements.mixins.json`唯一归B（A不写） | 依赖A api；与C通过接口交接水chunk数据，不互改目录。2组同item异组成经历点击/分堆/拖拽/快捷/交换/漏斗/掉落合并/死亡/重启逐实例完全保留；合成燃料/模拟提取无重复；组件其他差异仍不合并；故障回滚/迁移与数量审计 |
| C 水/溶解/反应 | `P/fluid/**, world/**, reaction/**`；`data/chemicalelements/{solubility,reactions}/**`；`P/gametest/chemistry/**` | 依赖api及B提供实例端口；天然首次采样仅一次、搬运无加盐；1mol固体遇半饱和容量只溶对应量；所有无限源转换被阻止/旧盐不丢；桶管道跨chunk冻融守恒；五大反应类型正反例、温压越界、竞争反应、重载中的运行任务；实现rev8水位扩散/方块吸水溶质渗入/守恒融合 |
| D 机器/交互/兼容/美术 | `P/machine/**, client/**, network/**, compat/**, datagen/**`；`data/chemicalelements/{multiblocks,recipe,loot_table,tags}/**`；`assets/chemicalelements/**`；`tools/assets/**`；`P/gametest/machine/**` | api先行，接B分页与C反应；实际机器输入→产出/排放/耗能，结构拆卸、满输出、断电、区块卸载不复制；分页过期/越权/高频包拒绝；Jade/JEI四组合；全部新增资源无缺图 |

共享src/generated/resources按同一资源目录归属，禁止两包生成同名文件；D只汇总资源生成，不重写A/C科学数据。文档本文件归架构任务，后续接口变更由A发布版本并通知B/C/D。测试classpath配置由A负责，各包自有测试源。一个整合者串行执行Gradle/游戏验收，避免共同run世界冲突。

## 7. 完整性自检与交付门槛

覆盖映射：118元素/全部原版物品/第三方包→§1–2/A；混合mol/质量计算→§1；环境实例+持久化/合并拆分转移消耗+逐项UI→§3/5/B/D；天然溶质/部分方块掉落物/饱和/严格禁无限水→§4/C；类型引擎+反应库→§4/C；大量机器多方块/自制贴图/JadeJEI→§5/D；重载/迁移/科学与玩法边界→§1–2/4；明确目录责任与自测→§6。

最终验收顺序：各包真实自测 → A同版本Gradle build+runGameTestServer → dedicated server新世界/旧世界迁移/存档重启 → 客户端操作及四种Jade/JEI组合 → 实机完整物质流（环境采样的异组成物品堆叠分页→部分溶解→反应→机器分离→剩余物回收→重启保持），含错误与恢复。构建成功不能替代上述功能结果。当前交付只完成契约与本地源码核对，没有修改业务代码、没有启动游戏、没有声明模组完成。

## 8. 发布前rev8扩展（正式范围，不是可选后续）

发布前重新读取共享goal得到rev8：严格有限水/无override，新增大气、核素、每物品温度、守恒原版配方和所有物质工艺闭环。以本节及§4修订取代旧口径。旧共享建议的10^-12 mol与本契约10^-18 mol均非用户指定；当前实施统一10^-18，世界元数据锁定scale，不能混用。总species mol不普遍守恒（电离改变粒子数）；化学守恒的是元素计数与净电荷。混合物无唯一分子式，同分异构体独立species ID。

### 新公共接口（A所有；B保存；C实现；D展示）
- `api.fluid.WaterCell(volume,solution,level,revision)`、`FiniteWaterTransport.planTick(neighborhood,dt)`实现§4确定性有限扩散。`api.fluid.SorptionModel.capacity(composition,temperature,porosity)`和`planExchange`计算吸附/渗入/解吸。`api.world.WorldChemistryStore`统一访问B的chunk持久化，C不建立第二份事实副本。每个方块有容量规则与来源。
- `api.thermal.ThermalState(energyJ,temperatureK,phaseFractions,revision)`；`ThermalModel.solve(composition,energy,pressure)`、`planHeatExchange(a,b,dt)`：每物品实例/方块有热状态；温度按热容与相变潜热求解，混合按能量而非平均温度。不同温度仍能正常堆叠，逐件保留热量。环境/电力/辐射能量输入输出显式入账。
- `api.atmosphere.AtmosphereCell(gasComposition,thermalState,volume,revision)`；`AtmosphereAccess.emit/collect/deposit/advect`：区域网格+远场储库；逸散只变分布浓度，不删除库存。收集从同库存扣，沉降进入方块/水。区块卸载仍保留远场量，原始空气初始化一次有墓碑，不能重载清空再补气。守恒总账含大气、远场、固液和机器。
- `api.nuclear.NuclideId(Z,A,isomer)`、`NuclideInventory(Map<NuclideId,MolAmount>)`、`NuclearEngine.evaluate(context)`、`DecayRule(halfLife,branches,energy)`。核素是species内部同位素分辨库存，不与元素mol重复计量；普通元素由声明同位素分布展开，核变后重聚合化学组成。核反应独立校验核子数/电荷/能量和发射粒子，不强迫元素种类守恒；beta显式电子/正电子/中微子及逸出能量。半衰期积分用持久化小量残差/确定性采样，分支概率和为1、稳定核素无衰变。衰变链、放射剂量/屏蔽/热量均需功能，不以登记名称替代。
- `api.recipe.ConservativeRecipePlan(inputs,outputs,byproducts,energy,atomBalance)`：A审计全原版配方，B接合成/熔炼/燃料/回收真实调用，C提供校验，D编写实际替换配方与JEI展示。不守恒配方必须替换/调材料/回收副产物，不能只报警后继续复制。每species须有可执行提取或合成路径；核素通过核设施，不要求纯化学创造新元素。

### Schema与存档扩展
目录新增 `data/<namespace>/chemicalelements/{thermal,sorption,atmosphere,nuclides,decays,nuclear_reactions}/`。thermal包含温度范围/热容/潜热/相变和出处；sorption包含方块选择器/容量/分配系数/孔隙/解吸；核素示例结构 `{"schema_version":1,"z":6,"a":14,"isomer":0,"half_life_s":"180825048000","basis":"empirical","source":"reference-required"}`（仅格式示例，常数须在发布前核对真实资料）。衰变规则给daughter、发射粒子、branch_probability、energy_j_per_mol。大气边界交换也有源储库，禁止恒浓度边界暗中造物。全部沿用快照/迁移/失败保旧。数据包决定规则，资源包仅显示；同包分发不能使客户端assets覆盖服务器数量。

`InstanceRecord`扩展 `thermal, nuclides, poreSolution, adsorbed`，默认字段也必须显式持久化或可无歧义恢复。完整实例质量按固相+孔隙+吸附+核素解析后的同一物质视图计算，不叠加两份元素/同位素总量。网络页增加温度/辐射与来源；Jade仍只读，JEI说明适用域，未知反应显示“未建模”。

### 四包独占责任增量
A增加上述api/物性与核素基础定义，以及科学数据 `thermal/nuclides` 和全原版守恒/工艺图覆盖报告。B增加每实例热/核素账本、chunk孔隙/气体持久化接口与迁移；保持现有mixin独占。C新增独占 `P/thermal/**, atmosphere/**, nuclear/**` 和科学数据 `sorption/atmosphere/decays/nuclear_reactions`；实现有限水/吸附/热/气/核/多组分相平衡，所有跨系统事务经api，不直接写B目录。D新增气体收集/压缩、吸附干燥床、冷凝器、量热仪、辐射探测器、屏蔽核反应堆/中子源/同位素分离机的实际工艺与美术（清单非上限）。D仍独占标准recipe目录，A提交约束报告不争写；DataGen总入口归A，providers归D。

### rev8补充验收
有限水多邻格流动总量守恒；所有方块吸水容量覆盖、吸水/解吸闭环；异浓度水mol相加且浓度按总体积计算；热交换+潜热能量守恒；蒸发水进入大气、降温析出；收集/沉降/远场/重载不丢物质；beta链/分支链/稳定核素/核反应的核子电荷能量校验；逐件温度经合并拆分保持；全原版合成熔炼回收循环无净元素收益。遍历所有注册species及核素的工艺图，缺提取/合成可达路径为发布失败，不用JEI文字页替代运行闭环。对未建模科学区域明确标识，不称万能预测。

最终验收增加游戏中可见水位/吸干、气体浓度与收集、温度与相变、衰变与辐射热。硬件性能不作削减完整范围理由，确定性/守恒/防复制优先。

## 9. rev12通用合成与可插拔查看（A增量；非完成声明）
旧Edit已由编排报告reconciled/not-executed（chem-architecture-edit-verify / report_328f81dc9a7541e1a4272ae2f398f1f2）；此处基于现存文件增量更新，不重放旧编辑。

实际API以源码为准：SynthesisRequest(slots,width,height,conditions,registryEpoch)验证网格面积与条件输入相同，species()保留物种身份/相态，elements(snapshot)仅提供严格元素聚合，不据此任意生成物种。C实现SynthesisEngine.solve；evaluate为兼容转发。SynthesisResult明确READY/CONDITIONS_NOT_MET/UNMODELED/NO_SOLUTION/STALE_REGISTRY；失败保留输入，时间与条件由服务端验证。ReactionContext.workbench固定298.15K/101325Pa、零外加能量、无催化剂和机器能力。D提供可配置网格与反馈；B承担预留/产物物化/残渣容器返还/原子提交；A不得用模板copy绕过。

ConservativeRecipePlan包含inputs/outputs/byproducts/returnedContainers/energyJ/durationSeconds/registryEpoch/conditions；validate要求元素与电荷精确相等、同epoch和同输入物种、足够能量/时间/能力。大气排放也是byproducts的明确出口，不能消失。原版固定配方审计只是迁移数据，不替代通用引擎，也不授权工作台执行高温反应。每个旧配方需分类：库存已平衡、需残渣、需补输入/改输出、动态组件语义；所有变体和余数均须真正提交。禁止仅禁用全部旧配方后宣称改写完成。

RecipeDisplayAdapter(viewerId,replaceSnapshot,clear)为独立可插拔查询层，核心不引用JEI/Jade客户端类。Jade只读实例权威快照；JEI展示规则/条件/时间/典型标定而非伪造实际实例量。NEI在目标平台的可用性由D第一手发行证据判定，无版本则明确不接入并保留其它适配端口。

依赖已按正式证据固定JEI 19.69.0.457和Jade eYz2YBGT compileOnly；开发运行通过-PwithJei=true/-PwithJade=true分别选择，默认无二者，需测试四组合与独服。FML4.0.45 VersionSupportMatrix源码明确1.21.1支持声明1.21的mod，解释JEI半开范围，但仍需真实装载验证，不修改第三方jar。

持久化边界：配置readBounded仍128bit输入防护；可信LedgerJson存量使用普通非负十进制完整读回、不应用128bit限制，已新增1025bit回环。网络资源限制需分片/分页，不能截断或把合法大库存清空；B网络与存档路径仍须独立验证。

本节对应A实际API与一次核心GameTest通过，仅证明目录加载/周期表和注册表覆盖，不代表完整物质流、配方改写或客户端四组合验收。

## 10. rev15 原子发布与扩充契约（实施中，非完成证明）
- 发布单位为 data.ChemistryBundle(catalog,reactions,solubility)，单个 AtomicReference。每次操作捕获一次 bundle，长任务提交比较 epoch。先解析所有数据，再 ReactionLibrary.validate 和溶解元素/电荷校验；失败整批保持旧对象。C拥有两库纯parse和读取适配，A拥有发布协调。
- 新增12个显式分子/离子/相态物种，与原子记账ID分离。质量由已有元素权重求和，忽略离子电子质量并标注approximation；公式不代表反应可行。
- MolAmount算术无128bit上限；readBounded只限制配置输入。LedgerJson库存单数值限16384字符以防恶意分配，1025bit合法。thermal/nuclides/poreSolution/adsorbed完整保留。NBT/网络/存档由B统一codec，禁止截断或清零；JSON测试不代表全链通过。
- 创造标签由集中分类注册表驱动，包含元素、物种、材料、机器、零件、容器、工具饰品、试剂溶剂、核素。D提供条目分类及资源，A公共注册表及入口；默认每物品一个主类别，避免混堆。
- Curios为可选依赖：D核实目标jar/API，A接build/元数据。缺失明确不可用，不以背包持有绕过装备授权。服务端按装备等级过滤范围、元素、精度、名义/实例和热气核能力后分页；未装备无详情，客户端不持久缓存。
- 不同尺寸多方块零件默认多格占位共享控制器；方向/碰撞/重叠校验，缺件禁止运行并提示。任一格破坏只结算一次，断电/卸载/爆炸安全暂停保留预约物质。
- 1290配方仍需真实执行改写；545/522/191/32仅审计分类。动态组件/容器/残渣同一B Settlement；仅生成计划或拒绝全部旧配方不算完成。
- 合理默认：已有元素权重保持数据一致；库存编码限额与化学位宽分离；单次操作捕获整批快照避免交叉epoch。保留既有增量和13项索引，不运行生成器覆盖。

## 11. rev16 同版本集成与原创资产契约（实施中）
- 美术与功能均为必要验收：118元素及全部物品专门原创纹理；禁止纯色、空白、同图复用、原版纹理挖改。族/周期配色和金属/非金属/气液质感组成一致视觉体系。D唯一拥有assets及机器/零件/GUI；A不重复生成或覆盖这些资源。
- 六尺寸零件必须有真实几何与正确UV，组合体体现功能区、方向和端口。运行/暂停/缺件视觉必须来自服务端真实状态；未接Settlement不可伪造运行产出。GUI包括网格、储罐、探测页、Jade/JEI展示。
- 每次客户端验收绑定源代码/资源清单SHA256、构建jar SHA256、实际启动参数、日志及游戏截图。processResources之后才重启或重载；源文件存在或静态拼图不替代游戏截图。旧client-1/integration-4不覆盖新协议和新资产。模型WARN清零只证明装载，仍须逐项检查紫黑、UV、布局与状态。
- 探测分页protocol 3按session/request/emission三重顺序拒绝旧包。服务器持续重验装备权限；客户端目标或装备变化清页，未装备不保留详情。实际Curios装备/卸装/降级、数据重载撤权与重连均需同版验证；纯codec测试不能替代真实授权矩阵。
- CreativeCategories中六PARTS、三TOOLS已由MachineBootstrap登记，公共入口不可重复登记。九分类需实际内容验证，不按标签文件数量验收。
- B仍唯一实例库存、NBT/network/save和Settlement实现者。A机械ConservativeCrafting仅是守恒分配算法，不代表1290条配方真实改写；32动态配方必须保留组件、容器与余数并经真实事务结算。无端口时推进其它集成，不创建第二库存、不绕过提交。
- 回归基线新增sizedPartWorldLifecycle、strictStructureStopsMissing、productionSorptionConserves；后者生产数据/算法验证不冒称世界Owner接线。构建、算法、GameTest、客户端分别记录实际范围。

### 11.1 protocol 4 原子机器状态增量（待同版实机验证）
- atomic-status-11新增MachineStatus单包UTF状态，替代独立字符ContainerData槽；客户端解码完整后整体替换，禁止代理对拆分、短文本残尾和半帧可见。保留原6个整数进度槽。
- 包仅适用于当前打开的服务端菜单，验证containerId与BlockPos；跨菜单不缓存。ID复用/重开/延迟的代次安全必须另做验证，不能仅凭这两字段宣称所有旧包均安全。
- GUI须可读显示服务端原因并可滚动访问全文；Jade限宽换行，可摘要但明确指向完整诊断。不能通过隐藏错误修复溢出。
- protocol 3探测页session/request/emission语义仍保留，但网络registrar已提升为4；升级需服务端/客户端同版构建重启，F3+T不能替代Java或协议切换。
- 新B persistence/LedgerPayload出现并不代表服务已安装。合法大库存超出单包资源限额须分片/分页或明确拒绝且保留原数据；不得截断、清零或以编码存在代表NBT/network/save全链通过。
