# r23 118元素物品默认组成：有界实施

任务 chem-element-compositions-r23；用户时间 2026-10-24 06:36 EDT。唯一工程 E:\Projects\ChemicalElements。

## 交付与状态

- 新增118个 `item_compositions/element_<registered-name>.json`，schema_version=1，priority=100，basis=game_convention，match.items 精确匹配现有118注册ID。既有1333条原版映射、118元素和138 species不修改。
- 新增 `tools/generate_element_item_compositions_r23.py`，读取ElementItems.NAMES及准备矩阵，生成前核全118目标不存在与既有匹配无冲突；独占创建(x模式)，不存在覆盖模式。部分外部竞争/中断时保留文件并停止，不自动删除、恢复或覆盖。再次运行拒绝已有目标；`--check`只读检查完整规范内容及计量。只写118目标；无任意输出目录或species生成。
- 新增独立main断言测试 `src/test/java/org/goldgom/chemicalelements/data/ElementItemCompositionTest.java`，沿用CoreVerification无JUnit惯例。实际磁盘资源经CatalogParser.parse；118逐项组成、单件与64件原子总量、电荷、正质量及未知species/坏schema/过精度/负量/同优先级冲突拒绝。此main未接新Gradle task（build文件非本owner），由集成owner使用test runtime classpath运行。
- 新增生产源集真实GameTest `gametest/core/ElementItemCompositionGameTests.java`，使用现有@GameTestHolder/前缀/`core_empty`注册惯例；两项测试等待正式下一批发现并运行。

**证据严格分层：源码和JSON已写；Python静态检查已运行（详见static-results.json）；Java编译、独立main、GameTest、实机均未运行。静态检查不是运行时加载证明。** 本任务禁止Gradle/编译，未写classes/resources构建输出，未触碰首批固定2693快照。

## 计量决策及适用域

统一1件=1 mol元素原子，是显式游戏标定，不是测得物品质量。H/O复用既有hydrogen_gas/oxygen_gas：0.500000000000000000 molecule-mol × 2 = 1.000000000000000000 atom-mol。没有旧实例转换，没有把1mol H原子改为1mol H2；118旧species atoms不变。

其余116用同名现有原子记账species，1 species-mol × 1 = 1 atom-mol。没有N2/F2/Cl2/Br2/I2等定义时不擅造，也不把真实分子量套到原子species；未实现同素异形体选择。已有solid/game_convention字段只是记账约定，不是样品真实相态；H2/O2的gas字段也不证明任意T/P样品当前为气态。本批无熔沸点、Cp、潜热、压强、密度或热物性新数值。

逐元素核算与理由见calibration-118.csv/json（按原子序1..118）。固定精度沿MolAmount.SCALE=18；0.5/1均可精确表示，无舍入。质量由原有species.molarMass计算，不另造权重。

## 实际采样路径与覆盖

真实资源管理器 -> ChemistryReloadListener/AtomicChemistryCatalog已发布快照 -> 真注册ElementItems.ELEMENTS / BuiltInRegistries.ITEM -> `new ItemStack(item,count)` -> `InstanceSampler.mint(ItemStack,count,CreationContext)` -> 内部catalog()取live AtomicChemistryCatalog.INSTANCE.snapshot() -> nominal per_instance -> 每件InstanceRecord -> remaining.inventory(snapshot)按species atoms算原子quanta。

`allRegisteredElementsMintExactAtomMol`：所有118元素，各count=1/2/64，累计7906个实例，检查每件非EMPTY、精确species/mol、只有对应元素1mol、电荷0、epoch/无unmodeled、全实例UUID唯一、堆总量1/2/64 atom-mol、输入ItemStack不变、live catalog不替换。使用simulation=false真实mint，消耗正常identity source序列，仅在测试局部产生ledger，不写库存/账本或伪装生产fixture。不替换catalog、不注入服务、不启用生产。

`unmappedCarrierRemainsExplicitlyUnmodeled`：真注册material_sample当前无默认映射；mint 2件应EMPTY且composition=unmodeled，库存原子空，count=0无实例，不改变原ItemStack/catalog。该边界表达未知默认，不表示未知物质现实不存在。若未来正式为generic carrier增加映射须更新该边界约定，不能偷偷保持测试通过。

## 集成交接与未完项

只交 `chem-integration-execute-r22-next` 的正式**下一批**，不得混入05:55固定2693输入首批。请按manifest逐文件hash冻结后编译，运行上述独立main及两项GameTest，保存真实发现数量、测试名、日志和退出码。此报告不能用于宣称已可在游戏中加载。读取目标task返回tasks=[]，故由编排确认正式owner再投递，不凭空猜node。

水系统/机器生产门槛与开关未改未开；本批不依赖B成功CAS补默认资源，也不实现CAS。完整目标的118可放置单质块、混合物tag、温压物态模型、热量与相变守恒及事务投影仍未完成，不以本批默认组成缩减完整目标。

## 保护与复现

生成：`python tools/generate_element_item_compositions_r23.py` 首次exit0，118通过，原版1333。
只读检查：`python tools/generate_element_item_compositions_r23.py --check`。
专属验收：`python docs/element-compositions-r23/verify_static.py` 一次性创建证据、拒绝覆盖测试和manifest；证据文件已存在时拒绝覆盖，后续直接核manifest或运行--check。

读取准备报告和完整交接板后只实施本owner新增文件；未git add/reset/commit，不触碰正式mods/存档，不外发。静态验收对准备manifest中的118元素+138species共256项核hash，对索引核82DB786A…CF1B69及13条暂存。既有1333映射只读并记录当前hash；未采本任务写前1333逐文件hash，故不将当前清单冒充前后对照。所有命令均同步返回，无本人后台/编译/未知在途。
