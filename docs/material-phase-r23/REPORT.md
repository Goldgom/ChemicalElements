# r23 单质、混合物与物态：有界实施准备

## 结论与证据等级

本报告是实际源码/数据检查和公开物性研究，**不是整体实现、独立复核、编译或运行验收**。仅新建本目录文件；未改业务、build、资产、IDE配置，未执行Gradle/javac/client/GameTest/截图；未git add/reset/commit，不占IDE窗口。完整计划chem-material-phase-r23已在首次任务轮读取，技能已激活并读取。项目根未见AGENTS/CLAUDE，src限定查找未发现。未重复旧全预检，也未声称本轮重新核验13索引hash。

核心结论：现有118元素物品注册应保留；当前118同名species是原子记账固态游戏约定，不是真实118单质物态。最小增量是**静态tag + 既有服务端实例账本 + 有域相态模型 + 有限载体**，绝不是每种配比注册一块。成功CAS仍未实现，形态投影必须交B唯一事务，不能以setBlock/shrink模拟成功。

## 1. 实际覆盖矩阵

完整118行见coverage-118.csv/json，按原子序数1..118，列出真实元素/species路径、注册ID、缺失项和来源采集状态。生成时实际读取118元素JSON、138 species和1333 item_compositions，不是复制旧摘要。

|范围|实查结论|真实路径（相对工程根）|
|---|---|---|
|118元素物品|118名称逐个匹配，循环registerSimpleItem；已有material_sample|src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java|
|118元素定义|元素原子量含empirical/approximation，不能当热物性|src/main/resources/data/chemicalelements/chemicalelements/elements/*.json|
|118同名species|全部phase=solid、basis=game_convention；notes明确atomic-component bookkeeping，不含真实分子/晶型|同上species/<element>.json|
|额外中性单元素species|仅hydrogen_gas(H2)、oxygen_gas(O2)，合计120中性单元素species；未见N2/F2/Cl2/Br2/I2、石墨/金刚石等独立定义|species/hydrogen_gas.json、oxygen_gas.json；其余18为化合物/离子等|
|默认元素物品组成|1333映射逐项匹配element_<name>，118均未匹配；material_sample同样无默认物质|.../item_compositions/*.json；instance/InstanceSampler.java: mint从snapshot.itemCompositions取nominal，null即EMPTY并标unmodeled|
|元素方块|未找到元素块注册。现有createBlocks仅入口example_block和MachineBootstrap机器/零件|Chemicalelements.java、machine/MachineBootstrap.java|
|温度/储热|ThermalState有energyJ、temperatureK、phaseFractions、revision；空fractions不是可靠SOLID，缺pressure/逐species跨相分配/模型状态|api/thermal/ThermalState.java|
|组成精度|MolAmount固定BigInteger 10^-18 mol；Composition.inventory按原子系数计算，质量按species.molarMass|api/chem/MolAmount.java、Composition.java|
|实例与编码|InstanceRecord包含UUID、remaining、origin、revision、thermal、nuclides、poreSolution、adsorbed；LedgerJson存十进制字符串；组件persistent+network codec|api/instance/InstanceRecord.java、data/LedgerJson.java、persistence/LedgerPayload.java、ChemistryComponents.java|
|块实例|ChunkChemistry.BlockInstance及WorldChemistryStore；Hooks.placed/assignDrop和BlockMoves搬运已有记录，不应新增第二库存|persistence/ChunkChemistry.java、WorldChemistryStore.java、instance/Hooks.java、BlockMoves.java|
|原子reload|一次解析catalog/reactions/solubility，校验后current.set(ChemistryBundle)；失败保旧；sourcePackId有来源|data/AtomicChemistryCatalog.java、ChemistryBundle.java、ChemistryReloadListener.java|
|PhaseEquilibrium|理想稀溶液浓度积/Ksp、温度表内插、禁表外；它是溶解/析出，不是通用温度-固液气模型|reaction/PhaseEquilibrium.java、SolubilityLibrary.java、DissolutionTransaction.java|
|水相变|PhaseProcess是纯计划；WaterPhaseModels硬编码4窄域水相变，101000..101650Pa、潜热6010/40650 J/mol；显热另付|thermal/PhaseProcess.java、WaterPhaseModels.java|
|混合相变已有内容|DiluteWaterPhase：非挥发aqueous溶质，≤0.1mol/kg，近1atm、窄温域，冻结/蒸发后浓缩不能超域；不能重建或称完全没有混合模型|thermal/DiluteWaterPhase.java；src/test/java/org/goldgom/chemicalelements/reaction/MixedPhaseTest.java|
|显热交换|InstanceHeatExchange有限双热库、恒热容有效域、精确能量转移但温度近似；显式声明无隐含相变。ThermalRuntime.Port需owner绑定|thermal/InstanceHeatExchange.java、world/ThermalRuntime.java；src中未检出ThermalRuntime.bind调用|
|相变数据资源|chemicalelements资源子目录实际为decay/detectors/elements/item_compositions/multiblocks/parts/reactions/solubility/sorption/species，未见phase模型目录|src/main/resources/data/chemicalelements/chemicalelements/|
|静态tags|chemicalelements/tags下25个机器pickaxe子tag，未见材料分类体系。现路径是chemicalelements:mineable/pickaxe/<machine>，不能当minecraft:mineable/pickaxe已挂钩|src/main/resources/data/chemicalelements/tags/block/mineable/pickaxe/*.json|
|容量|MaterialTank当前capacity是mol而非L/m³，不支持凭它保证液气物理体积|machine/MaterialTank.java|
|产物载体|ProductSettlement.carrier将任何中性单元素species映射element_<name>，不按物态；其temperature mol加权和各组均分heat也不是通用焓平衡|instance/ProductSettlement.java|
|热核拒绝|MaterialStateBalance.requireUnchanged保持当前化学物化契约；MachineBlockEntity先调用它再power.spend、后续清槽|machine/MaterialStateBalance.java、MachineBlockEntity.java:117..120|
|B真实事务门槛|MenuOwnerTransactions.commit最终rejectDurably；snapshotPlayer并非成功CAS，机器完整Owner仍UNSUPPORTED；cancel不退款不预约|instance/MenuOwnerTransactions.java、MultiOwnerRequest.java、MultiOwnerJournal.java、persistence/SinkLedger.java|

上述是观察时源码，不保证并行owner随后没有更新；manifest记录定点证据版本。1333是原版映射，不能用“零缺失”推断模组118元素映射齐全。由ProductSettlement给元素样品设置了ledger的特殊路径可带物质，不等于创造栏新样品已默认有组成。

## 2. 最小兼容物态表示

### 静态tags（D资产、A定义读取契约）

使用1.21.1单数目录tags/item、tags/block：chemicalelements:material_carriers、elemental_samples、sealed_containers、solid_projections等有限分类。需要挖掘工具时另接minecraft:mineable/pickaxe。tag仅静态注册ID集合，不存每堆配比、温度、压强或相分数。冷凝中的同一容器不动态改registry tag。

行为配置建议chemicalelements/material_behaviors/<id>.json，引用静态carrier tag、容量策略及model family；危险性/溶解性等动态判定读取实际species与模型，不信客户端或只凭tag。一个合金/溶液/矿石使用通用载体+服务端Composition，不枚举配比block。

### 单质物种与数量

- 保留已有element_<name> ID。全部118先有可读样品语义；未知物性样品仍可携带记账数据但标UNKNOWN，不宣称能形成宏观稳定块。
- 旧species/<name>不得直接改atoms或把H改成H2破坏旧存档。新增明确的phase/allotrope species；H2/O2复用现有。原子记账迁移到双原子分子时 **2 atom-mol→1 molecule-mol**，最小quantum奇数余量保留旧载体/拒绝精确转化，不能四舍五入丢原子；这不是普通相变，需独立化学身份及能量迁移规则。
- 真正相变同一分子式的species mol守恒；同素异形体转变也保元素/电荷/核素，焓差与有效域另建。不要求所有元素三态都有已测数据。
- 118矩阵的统一carrier建议为条件分派，不是118行相态已核：有已核固相模型才投影对应单质固体块；有液相模型用密封液体容器/有限液体cell；有气相模型用密封气体容器/有限气域；UNKNOWN仍用封存样品，不猜固液气。Hg液体、N2气体、Fe固体已有代表来源核对但生产数据尚未完整。
- D负责固体块和有限通用载体类/模型；A负责唯一注册入口和元素/物种映射，双方先约定新增ElementMaterials注册类的归属，避免两处注册同ID。可放置密封容器不等于元素本体方块；后续完整118验收应分别记录真实固体块、容器、未知未支持，不能用容器掩盖固体块未做。

### 容量与形态投影

摩尔容量继续作为既有上限，新增几何体积L或m³、温压域及密封额定压力为额外约束：已核密度才可算固液体积；稀薄气体理想气体近似必须声明域与R单位，缺EOS/密度/压力则VOLUME_UNMODELED拒绝转移，不能默许无限容量或默认1mol=1L。气化不能直接删除物品向“环境”放空；必须有加载且获授权的气域/罐Owner与显式出账。

放置：读取权威槽及整个InstanceRecord→校验载体、余量、空间、热压、块位与epoch→同journal联合预约→原子扣该实例并写块投影/记录。拾取/破坏反向；同一完整实例只改投影保UUID，分裂生成operation派生子ID并记父谱系、精确分配全部字段，重试复用ID。poreSolution/adsorbed不能丢；核素按对应元素分配，不得像通用预览简单全塞第一组。组件/BE只存镜像/引用，账本唯一权威，不再建独立库存。

## 3. 热量、压力与模型域

建议统一有来源的焓参考；现AMBIENT能量0J是各创建环境参考，不能直接当绝对可释放热量。先保留旧thermal语义并标LEGACY_REFERENCE_UNMODELED，不通过reload将旧0J补成真实热库存。A/B/C共同定义版本化参考标识和迁移；旧schema1/2与hash不变，schema3仍同journal/UUID空间。

恒压窄域纯相的建议预算：显热=n∫Cp(T)dT；潜热=相变mol×ΔH；两相共存时温度停在相边界，热量改变相份额，剩余预算再付下一段显热。来源使用焓还是内能必须一致；封闭定容气体、压缩膨胀及PV功不能偷套恒压模型。当前最小候选仅恒压受限模型，其他情况UNMODELED，后续扩展EOS而不是凭空丢PV功。

收支：物料储能+热源储能+冷源储能+显式外部功/热/输出账项闭合；潜热若已经包含在物料焓中不能再次扣除。FE到J转换需显式游戏约定和损耗去向，不以温度setpoint充当热源预算。热源、冷源及气体去向必须同一真实事务参与者；核素和物质总量前置校验。相变本身不改变核素总量。

默认采用平衡、无成核动力学近似，明确“不模拟过冷/过热滞后”；不能通过魔法阈值强制降温/补热。模型外、稀溶液超域、共晶/非理想/挥发混合物无数据均UNKNOWN/UNMODELED并保留输入，含义是**模型不支持，不是现实不会相变**。压力缺失不默认环境常压，边界压力可由显式游戏环境策略提供并标记来源。

不把Phase.PLASMA枚举当作等离子实现。超临界、离化、超高压等列UNSUPPORTED_DOMAIN，不要求本次准备额外实现，但纯模型不得超域返回普通气态。

## 4. 文件级owner交接与建议签名（均未实施）

Java根J=src/main/java/org/goldgom/chemicalelements；数据根R=src/main/resources/data/chemicalelements/chemicalelements。以下新路径是候选，非已存在文件。

|owner|现有文件接入/候选新文件|具体增量|
|---|---|---|
|A|J/registry/ElementItems.java；R/item_compositions/element_*.json（新）；J/data/CatalogParser.java|保留118ID；显式出生样品量是游戏约定，未知不强设真实分子；生成注册ID→样品definition校验；不批改旧species。|
|A|J/data/ChemistryBundle.java、AtomicChemistryCatalog.java、ChemistryReloadListener.java；J/api/thermal/PhaseModel.java（新）|bundle扩展不可变phaseModels/materialBehaviors；所有parse、物种/原子/电荷、单位、来源、域、容量交叉校验通过后一次set；失败旧epoch/所有库不变。|
|C|J/thermal/PhaseProcess.java、WaterPhaseModels.java、DiluteWaterPhase.java、InstanceHeatExchange.java；J/thermal/PhaseLibrary.java、EnthalpyPlanner.java（新）|水常量迁数据不改变旧行为基线；pureOnly不能再靠basis字符串判断；组合显热/潜热/容量，部分相变；保留已有稀水模型和Ksp，拒绝任意混合物外推。|
|C|J/world/ThermalRuntime.java、SimulationBootstrap.java、InstanceEnvironmentView.java|捕获同bundle epoch，有限预算有界已加载接触调度，热源真实Owner；未加载暂停，不追补无限热；不独立bind假port。|
|D|J/machine/MachineBlockEntity.java、MachineTransactions.java、MaterialTank.java、MaterialStateBalance.java、MachineServices.java；J/machine/MaterialCarrierBlock.java（新）|全镜像包含槽/tank/thermal/control，容量增加几何约束；预检先耗电清槽；现requireUnchanged不能删掉，增加显式atomicHeatExchange契约；有限通用载体及固体单质投影。|
|D|J/network/CompositionPages.java、MachineStatus.java、ReactionCatalogSync.java；J/compat/RecipeViews.java、ReactionDisplay.java、jade/client/JadeClient.java；J/client/screen/StackCompositionScreen.java|同MaterialView读实际组成、T/P、模型状态与来源，不将tag或species.phase直接显示为当前相；未知可见。分页/epoch保护、可选mod缺席保持降级。|
|D|src/main/resources/data/chemicalelements/tags/{item,block}/；assets/chemicalelements/（后续实施，本任务不写）|分类tag、固体块/密封载体资源与原创纹理；只有限载体，不每配比block；原注册协调A。|
|B|J/instance/MenuOwnerTransactions.java、MultiOwnerRequest.java、MultiOwnerJournal.java、OwnerTransferValidation.java、ProductSettlement.java；J/persistence/SinkLedger.java、WorldChemistryStore.java、ChunkChemistry.java|把计划接真实reserve/commit/cancel与全OwnerCAS/ABA；禁止直接setBlock/shrink。ProductSettlement不再按单元素无视相态选载体、不mol加权温度伪装焓解算；核素/孔隙/吸附精确分配。|
|B|J/instance/Hooks.java、BlockMoves.java、StackLedgers.java；J/persistence/ChemistryComponents.java、LedgerPayload.java|沿用组件/块记录，放置拾取与异常恢复同journal；不复制现有legacy putBlock路径当耐久联合CAS。若需新增模型引用元数据，协调A codec版本，不改schema1/2/hash。|

建议契约（设计类型，尚不存在，不声称可调用）：
```java
// A负责公共不可变定义；C负责实现。只读，无UUID分配、无RNG推进。
PhaseAssessment assess(InstanceRecord input, PressureContext pressure, ChemistryBundle bundle);
PhasePlan plan(InstanceRecord input, ThermalReservoirs reservoirs,
               CarrierCapacity target, PressureContext pressure, ChemistryBundle bundle);
// PhasePlan: status, epoch, modelId/hash, expected full states,
// output partitions, enthalpy deltas, work/heat ledger, capacity checks, reasons.
MaterialView view(InstanceRecord record, ChemistryBundle bundle, PressureContext pressure);
// D产生投影意图；B最终从权威owner读取，不接受任意客户端状态。
ProjectionIntent project(PhasePlan plan, CarrierTarget target);
// B接到现有MachineTransactions reserve/commit/cancel，而非另造交易接口。
// reserve(ServerLevel, Request), commit(ServerLevel, OperationKey), cancel(...)
```

建议phase-by-species分配置于版本化计划/视图，逐species份额之和回到原Composition；现ThermalState.phaseFractions只有聚合相份额，不足以存多组分分相。最小兼容阶段可由Composition中明确相态species+模型域推导视图，不在旧InstanceRecord里偷偷塞未校验JSON；持久分相扩展由A/B正式确定迁移，不能临时改旧schema。

重载：phase库与species/reaction/Ksp一次发布；同一操作捕获bundle一次，计划提交epoch不同即拒绝重新plan。客户端显示快照可更新但不得授权交易；数据包删除species沿既有tombstone保留旧实例、不静默替换。动态物性变化不自动改已存热能。

## 5. 热核前置与守恒测试清单（仅设计，均未执行）

1. 118注册ID/映射/块carrier逐行；真实创造入口新元素不再意外EMPTY；故意无映射应UNKNOWN并保留，不生成物质。
2. H原子记账2mol→H2 1mol、奇量子余数；同分子相变species mol守恒，元素/电荷/核素精确相等；禁止错误H→H2一比一。
3. 固/液/气分区、零预算、恰够一个量子/不足一个量子、潜热平台、反向冷凝释热、循环热量闭合；禁止无冷源删除热。
4. 固体Fe、液体Hg、气体N2、As升华、Og未知的来源/物种/压力检查；当前代表数据缺Cp/潜热的模型仍拒绝READY，不强行通过测试。
5. 两相部分转化、显热与潜热不可重复计费、超临界/压力缺失/模型域外、低温He压力特例未建模；过冷近似有明确说明。
6. 复用MixedPhaseTest稀水夹具；盐水超0.1mol/kg、挥发混合物/合金无相图保输入；无模型不是“现实不相变”。更换basis字符串不能绕过pureOnly。
7. 温度相同但焓参考不同拒绝热交换；旧AMBIENT 0J不得凭空补成可支出热；明确有域Cp/T/P/密度，未知容量拒绝。
8. 体积溢出、气化无接收Owner、压力超额、未加载邻块均无副作用；源槽/块/罐/热源任何一方ABA都拒绝，BE.revision不能替代库存CAS。
9. 热核预检注入差1e-18mol核素、差最小热量、pore/adsorbed遗漏：必须在power.spend、清槽、世界投影之前拒绝；全部镜像/FE/计数不变。
10. 放置→拾取→分裂→堆叠→活塞/下落→破坏/爆炸：同一物质唯一位置，实例ID或谱系正确，热核/孔隙/吸附全部往返。取消/死亡/卸载不双退料。
11. 同operation重复、UUID载荷冲突、预约后reload、PREPARED/投影/COMMITTED各崩溃边界、启动恢复：同SinkLedger可重放幂等且不复制；schema1/2及历史hash不变。
12. 模型加载含未知species、反向焓符号错误、重复域、非有限数字、压力/单位错误、无来源/许可状态、坏JSON：整bundle不换epoch。合法更新一次换全库。
13. 多组分跨相核素依元素分配，不全丢首组；热量严格收支与温度计算误差分离；输出模型有效域检查。
14. 服务器权威分页/epoch、组件存储网络往返、未装JEI/Jade/Curios降级；执行者自检后交唯一集成同版Gradle/GameTest，客户端/截图仍另循现有许可。

## 6. 后续责任及范围

A/B现有正式或waiting任务继续，C/D queued绑定阻碍由原编排恢复，不能另派本节点竞争写。数据采集/候选schema/测试源码准备不需要等成功CAS；生产形态切换必须等真实事务接通并验证。IDE调试独立，不改其配置、不要求等本研究完成。

本轮可交付完成条件是118缺口矩阵+来源+文件级接线+测试设计，不是118热物性已采全。生产数据全部ready=false，超重未知不填数。后续由既有owners落实完整用户范围并验证；本报告不打开水/机器生产安全门。
