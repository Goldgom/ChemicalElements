# 118 elemental BlockItem 默认组成 r23 / task r24

任务：chem-elemental-block-compositions-r24；用户权威时间：2026-10-24 07:36 EDT。工程唯一 E:\Projects\ChemicalElements。

## 已实施与证据层级

新增118个 `src/main/resources/data/chemicalelements/chemicalelements/item_compositions/elemental_<name>.json`。这些是 ElementMaterials 的118个新 BlockItem ID，不是旧 `element_<name>` 的重复映射。schema_version=1、match.items、priority=100、per_instance、basis=game_convention，沿实际 CatalogParser / InstanceSampler 入口。

已完整读取 `chem-a-elemental-compositions-release-r23` rev1（工具定点返回完整content），核对旧组成报告、旧脚本/测试与 InstanceSampler。开发技能激活调用因宿主 `Resource activation cannot resume this turn` 失败；随后已直接读取内置 software-development/SKILL.md 并按其工程/API/测试要求实施。未因此虚报激活成功。

内容manifest严格SHA-256：`52514e22cf2cde25afb07c3cd760aa2a5b2bc631f640215cd65332915cf22a6f`；生成器核对该manifest与其ElementMaterials.java条目hash，确认NAMES循环真实注册elemental_*与MaterialBlockItem构造路径。

**源码/JSON：已落盘。Python生成与静态验证：已执行PASS，exit 0。Java编译/独立main/GameTest/客户端/截图：全部未执行。** 遵守本任务禁止Gradle、javac、client、截图及竞争固定2693/classes/resources的限制。没有把静态资源存在当成运行加载证据。

## 明确计量

- 每件元素块物品=1 atom-mol，是透明游戏标定。116个既有原子记账species各1.000000000000000000 mol。
- H2/O2复用现有hydrogen_gas/oxygen_gas，各0.500000000000000000 molecule-mol ×2=1 atom-mol。不改物种、不造N2等species；固定18位可精确表示。
- 块几何1m³不代表1m³密实纯元素，不由此推导密度/真实物态/稳定性/同素异形体。没有新增热物性数值。
- 四种mixture_carrier_solid/liquid/gas/unresolved不加默认物质，未知组成保持显式unmodeled。未启水或机器生产。goal rev3水初始化浸润不改变上述标定；完整水与材料事务仍未完成。

逐原子序118项ID/species/计量对照见 calibration-118.json。

## 生成与拒覆盖

`python tools/generate_elemental_block_compositions_r23.py` 首次exit0，118新映射通过；其余1451映射只读（1333既有映射+118旧element_*）。目标预检全不存在、精确ID无冲突；逐文件x模式独占创建，存在时exit2拒绝覆盖；竞争/中断不删除、不自动回滚、不覆盖。

`python tools/generate_elemental_block_compositions_r23.py --check` 只读逐项复核118完整内容、注册名/元素集合/原子序1..118/species atoms/电荷/H-O精确计量/旧element_*标定与全部匹配无冲突/generic无默认物质。

`python docs/elemental-block-compositions-r23/verify_static.py` 已执行exit0，专属证据独占创建。实际重跑生成器exit2并核118目标hash不变。静态验证报告不作Java语义运行替代。

## Java测试源码交唯一下一批

唯一接收：`chem-integration-execute-r22-next` 的下一明确版本；不要混固定2693快照。以本manifest所有hash及内容manifest版本一并冻结后，由该owner编译运行并保存发现数/完整方法名/日志/退出码。

1. `org.goldgom.chemicalelements.data.ElementalBlockCompositionTest`：按项目现有独立main惯例，无新增依赖，不改Gradle。工程根+test runtime classpath执行；磁盘真实elements/species/item_compositions经CatalogParser.parse，逐118检查ID、schema、game_convention、实际Composition、计量/电荷/质量及64堆总原子量；四generic无默认映射；未知species、坏schema、超精度、负量和同优先级冲突应拒绝。
2. `ElementalBlockCompositionGameTests.allRegisteredElementalBlocksMintExactAtomMol`：两层真实注册校验（BuiltInRegistries.ITEM、ElementMaterials.BLOCK_ITEMS/CONTENT且为BlockItem），live AtomicChemistryCatalog快照，真实ItemStack 1/2/64→InstanceSampler.mint(ItemStack,count,ctx) 内部取live catalog；共7906实例核精确量/1 atom-mol/中性/UUID唯一/epoch/非unmodeled/堆总量/stack与catalog不变。
3. `ElementalBlockCompositionGameTests.unmappedCarrierRemainsExplicitlyUnmodeled`：四真实注册generic BlockItem→同Sampler路径；2件EMPTY且unmodeled、原子空，0件不造量，原stack/catalog不变。

GameTest使用既有@GameTestHolder、@PrefixGameTestTemplate(false)、core_empty模板。context simulation=false，不注入fixture snapshot、不替换live catalog、不调用StackLedgers写入；只在测试局部持有mint返回的ledger，不伪造库存，不插入玩家/世界。mint使用正常identity序列，不声称是放置/拾取事务。

## 保护、冻结与未完成

写前baseline.json记录1707既有JSON逐文件SHA（1451组成+118元素+138species）和13条暂存清单。写后逐项1707/1707 MATCH；索引SHA保持 `82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69`，13条清单一致，见preservation.json。manifest包含全部本任务交付相对路径与hash；manifest自身hash由正式checkpoint/report另记。无git add/reset/commit，不访问禁用cm，不碰正式mods/存档，无外发。

仅改授权新增118映射、两个专属测试、专属脚本与本docs目录。未改旧element_*及其f77测试脚本、registry/content/reload/species、生产拒绝放置路径、其他资源或构建输出。MaterialBlockItem仍拒绝真实放置；默认采样不证明真实放置/拾取/相变可用。B持久跨Owner事务、热量与核素校验、完整水初始化浸润与运行证据仍不在本次映射交付中完成。水生产仍不开。

本轮所有工具调用均同步返回；无本人后台生成、编译、未知未决写。交付版本冻结，后续若需求改变由编排明确新owner；旧A仍遵守其释放只读声明。
