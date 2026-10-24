# r23 热物态有界实现

任务 chem-thermal-phase-source-r23；用户时间 2026-10-24 06:45 EDT。唯一工程 E:\Projects\ChemicalElements。完整读取C释放板rev1、material-phase-r23 REPORT/SOURCES/CANDIDATES/schema和五个旧thermal源码。开发技能激活并读取；本批复用已核来源，不重新网络采集，不上传工程。

## 实际交付与证据分级

新增 PhaseModel、PhaseLibrary、EnthalpyPlanner、ThermalPhaseGameTests、专属 PhaseLibraryTest；122个phase_models数据定义；本目录schema、生成/静态脚本、静态结果及manifest。**源码/数据已落盘，Python静态验收exit0；Java未编译、独立main未执行、GameTest未运行、游戏reload未接入，不能称游戏已启用。** 无Gradle/javac/client/截图、无构建输出写入、无2693快照改动。

旧 DiluteWaterPhase、InstanceHeatExchange、PhaseProcess、ThermalExchange、WaterPhaseModels不编辑。既有稀水浓度/压力/温域不扩大。适配器仍调用原WaterPhaseModels及PhaseProcess；不抄一套潜热，不改旧basis字符串行为。新入口额外明确pure-only，不以来源字符串决定混合物准入。

资源含118 UNKNOWN（引用原子记账species，不声称它们是真实晶型）、4 LEGACY_WATER_LATENT。**完整生产焓模型为0**：已核Fe/Hg/N2/As/Og来源不足以提供完整、许可已清且身份/压力明确的Cp/潜热/基准，不能靠熔沸点补造。原118覆盖矩阵保持待补；H2/O2默认0.5mol映射不成为相态证据。旧水描述仅existing-code-only/UNVERIFIED，不伪称本批重新测量或解决原来源许可。

## 加载与数据契约

`PhaseLibrary.load(RegistrySnapshot, Map<ResourceLocation,String>)` 是实际纯解析入口。键为模型ID（如 chemicalelements:iron_unknown），值为原始JSON。每库绑定不可变catalog，所有文档成功才构造新不可变库；不设置全局状态、不修改旧库。A负责将其与species等同一bundle原子发布，当前不接reload。

严格拒绝：重复JSON key、尾部JSON、过深/过大文档、未知/缺字段、错误版本、数字代替decimal string、非有限/指数/过精度数、缺来源字段、非绝对URI、非法访问日期、错误basis/license、非SI压力单位、未知species、错误域、缺Cp/潜热/基准、负Cp/潜热、相变两端分子式/电荷/质量不等、反向高低温相顺序、重叠物种模型无优先规则。未知模型不可夹带数值；旧水适配不可覆盖旧常量。完整焓数据必须来源CLEARED且非UNVERIFIED。这里校验可追溯元数据，并不能自动证明网页/论文真实性；正式数据仍须人工来源核对。

schema放本docs目录，不混入模型资源扫描。schema为结构规范，Java增加语义约束；Python检查的是本schema实际使用的有限子集，不冒称完整标准validator。

完整模型只支持单一化学身份、固液/液气/固气一对相、恒压常Cp、有界平衡模型；allotrope必填但不实现晶型转化，相同相枚举的同素异形体拒绝。无EOS/体积容量/超临界/等离子/反应/挥发混合物/超冷滞后。不泛化水和稀水。

## 规划与热核前置

`EnthalpyPlanner.plan(InstanceRecord, Request, PhaseLibrary)`，Request包含modelId、catalogEpoch、energyReference、pressurePa、targetK、heatingBudgetJ、coolingBudgetJ。输出Plan包含完整expected实例、catalog hash/epoch及库对象身份、模型/参考/域、逐species Composition allocation、temperature、materialEnergy、heatSourceDebit与heatSinkCredit。无UUID生成、无新账本、无setBlock/shrink/耗电。

调用先检查epoch、显式Pa、预算和目标；未知/缺完整模型返回UNKNOWN/UNMODELED并原样保输入。两相之外任何组分、孔隙/吸附存在均拒绝。旧AMBIENT不能自动迁入焓基准：参考标识不一致或储热不等于该模型计算焓即拒绝。参考标识由未来A/B的权威迁移/持久元数据提供；目前不能凭客户端字符串获得交易授权。

常Cp显热精确到目标或相边界；不足整段显热预算则保输入拒绝（不做无限精度反解温度）。到相边界按10^-18mol向下取整分配潜热，预算余量保留热源/冷源；相变完成后若足够才支付下一整段显热。部分相变温度留在平台。相变量、元素/电荷、质量、材料能量差与显式源扣/汇收均在返回READY前精确比较，不重复付潜热。温度相同不猜潜热方向，须指定跨边界目标。此保守策略牺牲部分小预算显热进度，不伪造热量。

核素按Z汇总，不能超过该实例相应元素原子quanta，未知Z拒绝；EMPTY表示未解析核素，不补编同位素。**全部核素留在同一实例**，allocation只是它的相内分配，不生成多个库存实例、不把全部同位素塞第一输出。未来若分割为不同Owner载体，B必须另实现按元素/相分配的核素规则；本Plan不授权那种拆分。

`Plan.matches(authoritative,currentLibrary,reference)`校验整实例+epoch/hash+同库对象+基准，用于提交前陈旧拒绝；仍非B耐久CAS或安全许可。B须读取真正Owner、验证热源/冷源有限可用量、容量及全字段，联合同journal提交；不能直接采用调用方预算扣FE。恒压焓包含适用恒压热交换语义，不作为定容内能或独立PV功模型。

`EnthalpyPlanner.legacyWater(...)`单独返回旧PhaseProcess.Plan；它是潜热-only，不可冒作完整EnthalpyPlanner READY。原DiluteWaterPhase仍可由C在已有域单独使用，未新增混合相图。

## 测试源码与已运行静态检查

1. `ThermalPhaseGameTests.realCatalogMintUnknownRetainsInput`：真实服务器ResourceManager读取122数据，真实AtomicChemistryCatalog，118真ElementItems ItemStack→InstanceSampler.mint→规划UNKNOWN；逐项保实例/组成/零热/堆数量；实际旧水适配与原PhaseProcess结果全record相等；不替换catalog、不写库存。
2. `fictionalEnthalpyFixtureBoundaries`：使用真实catalog的ice/water身份，但**Cp=2/4、latent=10、边界300K等全部明确虚构数值夹具**，只用于数学契约，不放资源。半相平台、闭合反向潜热循环、0/不足一量子/恰一量子/充分预算、热差等于支出、mol守恒、换库失效、legacy热拒绝、多一核素quantum拒绝、混合物拒绝、坏单位/许可/来源/重复key/零Cp拒绝。
3. `PhaseLibraryTest.main`：真实磁盘CatalogParser+122原始资源→PhaseLibrary.load；核生产完整焓模型0；复用虚构算术测试。未新增Gradle task。
4. Python `verify_static.py` exit0：122 JSON结构、来源单位/必填、额外/缺字段拒绝、species引用、118未知/4适配、精确Decimal量子预算参考检查；index SHA256仍82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69、13暂存。Python算术不是Java实现执行。

生成首试因旧coverage UTF8 BOM失败且未创建资源；修正utf-8-sig后执行`python -B .../create_resources.py` exit0。宿主曾对相同命令返回旧completed错误回执，已get核对，没有盲目重放。所有命令同步，没有本人后台构建或未决写。

## 精确接线交接与剩余范围

- A：在真实reload中读取phase_models原文，调用load并与同catalog/bundle原子发布；失败保旧；持久能量reference迁移另定，不改schema1/2。现无接线，库可加载不等于游戏已启用。
- C：真实温压来源、有限热源Owner和调度；只调用此纯规划入口，保留旧稀水；不把目标温度当无限热源。
- D/A：有限通用载体接受动态Composition/phase plan；静态tag只表示注册分类；体积/额定压力无已核数据则拒绝几何转换。不枚举混合配比方块。
- B/D：完整expected+库epoch/reference及源汇账项、核素/孔隙/吸附、容量、真实多OwnerCAS/恢复；本库非交易授权。水与机器生产保持关闭。
- 唯一 `chem-integration-execute-r22-next` **下一批**：先按新manifest固定版本，再低并发编译、运行PhaseLibraryTest及上述2项GameTest，记录发现、日志、退出码。不得混首批2693快照，不以旧integration15通过替代。

完整118单质可放置块、动态tag行为复用、完整热源/事务形态切换、全部118热物性/许可、物态持久化和客户端可视化仍未完成；本批是正式有界实现，不是完整用户目标最终交付。
