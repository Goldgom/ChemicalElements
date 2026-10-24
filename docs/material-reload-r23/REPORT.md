# material-reload-r23-v1

任务 chem-material-reload-r23；用户时间 2026-10-24 07:05 EDT。唯一工程 E:\Projects\ChemicalElements。已完整读取 chem-a-material-reload-release-r23 rev1（工具完整单项预览）、software-development 技能及 thermal-phase-implementation-r23/REPORT.md 和实际 PhaseLibrary/API/test 源码。根目录及 src/docs 未找到 AGENTS.md/CLAUDE.md；不重做118映射或物性调查。

## 本批实现

- ChemistryBundle 新增 phases/materials；强制同一 RegistrySnapshot 对象与同一 PhaseLibrary 对象，保留旧三参数/四参数构造（新库为空、绑定传入catalog）。
- AtomicChemistryCatalog 保留旧 reload(files) 与 reload(files,sources)，新增 reload(files,sources,originalDocuments)。严格校验原文和JsonElement相等、资源/来源键集一致；catalog、reactions、solubility、phase、material全部准备成功后才更新tombstone并一次current.set。失败不消耗epoch、不换任何库。
- 旧程序化JsonElement调用只能验证现存树：其toString是明确标注的兼容适配，并不声称恢复或验证已经丢失的原始重复键。真实ResourceManager路径从不走此适配。
- ChemistryReloadListener 保留无参构造，新增隔离目标构造供真实生命周期测试。覆盖prepare返回空待处理值，apply对同ResourceManager.listResources选中的同一个Resource读取原文和sourcePackId。这样避免SimpleJson默认扫描器把坏JSON仅记录后跳过，导致部分发布。apply严格检查全部JSON（含重复键、尾随值、深度和大小），再传递原文至PhaseLibrary；无json.toString伪造资源原文。
- MaterialBehaviorDefinition/Library：版本1、不可变、严格字段/引用/重复键/重复ID/选择器校验；PURE引用模型须精确匹配subject，禁止混合物泛化纯物态模型。规则按all_species包含匹配和ANY/PURE/MIXTURE分类，命中规则tags取并集，无优先级覆盖歧义。
- 三条实际资源：pure、mixture、iron_model。纯/混合分类不枚举配比块；iron仅引用已有iron_unknown演示实际库间引用，不创造数值或假称覆盖118行为规则。

## 查询契约

服务端调用方一次捕获 AtomicChemistryCatalog.INSTANCE.bundle()，用 bundle.materials().query(authoritativeComposition)。结果含epoch、catalogHash、tags、matchedRules、modelStatus；tags是本库语义ResourceLocation，不是写入Minecraft静态tags/item或tags/block。新增规则无需注册新方块。

UNKNOWN保持UNKNOWN；混合物UNMODELED；完整模型即便存在也仅MODEL_AVAILABLE，不返回READY。还必须走EnthalpyPlanner真实温压、reference、有限热量与Owner库存/事务检查。静态分类不存储或替代Composition/温度/压力/库存，不授予生产许可。配方、机器、探测器可用同一只读查询；未修改其未释放包来冒充已接入。

## 测试源码（未执行）

MaterialReloadGameTests 两项GameTest，通过公开 listener.reload(barrier,manager,profilers,executors) 走真实prepare/apply；服务器ResourceManager基础资源，覆盖资源代理只替换选中Resource原文且沿用同pack来源，不伪造库存：
1. 正常同catalog/epoch/库身份、来源ID；坏phase语法（不得被prepare跳过）、坏行为、phase/behavior重复key、坏phase/行为引用；elements/species/reactions/solubility失败全bundle保旧；修复重载仅epoch+1；不触碰全局singleton。
2. UNKNOWN不升级、混合物不外推、动态标签并集、查询epoch/hash、旧reload及三/四参数bundle构造兼容。
MaterialBehaviorLibraryTest main：磁盘全资源、严格raw/tree不匹配和重复键拒绝、来源缺失、尾随/嵌套重复key、失败保旧与恢复。由唯一集成执行者用test runtime classpath运行，不新增Gradle配置。

## 静态验收与版本

运行 python -B docs/material-reload-r23/verify_static.py：检查本schema所用结构规则与species/model引用、测试替换字面命中、索引hash/13暂存、git diff --check及精确SHA256清单。脚本不是通用JSON Schema标准实现，静态检查不是Java执行。manifest.json包含本批7个Java、3资源、schema/脚本/报告；static-results.json保存验收结果及manifest hash。所有新增文件保持本任务所有权，旧三文件只在释放范围修改。

**Java未编译；独立main未运行；GameTest未运行；无Gradle/javac/client/截图；未改classes/resources或固定2693快照。** 旧28/28不覆盖本版本。唯一 chem-integration-execute-r22-next 的下一明确版本需固定本manifest及thermal依赖，再编译并运行两GameTest/main与原CatalogGameTests；若NeoForge API签名/测试发现/运行有错须反馈本owner修复，不能把源码检查当通过。

## 范围与安全

没有修改thermal/phase_models、静态tags、Admission/API/registry/B/machine/world；无setBlock/shrink、新journal或第二库存；水与机器生产仍关闭。无git add/reset/commit、不删用户数据、不外发、不动正式mods/存档。git index基线82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69，13暂存，验收时重查。

完整118实体块/标签消费接线、已核热物性、有限热源、持久相态、真正相变多Owner事务和客户端可视化仍未完成；本批仅完成分配的原子数据重载/行为库源码和静态交接，不是完整用户目标验收。
