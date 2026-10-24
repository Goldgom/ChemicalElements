# material-content-r23-v1

正式任务 chem-material-content-r23，用户时间2026-10-24 07:23 EDT。A/D释放板rev1已完整读取，开发技能已激活；三依赖报告和实际接口已读。仅E:\Projects\ChemicalElements，无Gradle/javac/client/截图，无classes/resources或固定2693改写。

## 实现

- 唯一旧Java改动为ElementItems.bootstrap中加入ElementMaterials.bootstrap(bus)。旧118 NAMES顺序、element_*物品类型及MATERIAL_SAMPLE不动。新ElementMaterials独立注册118 elemental_*候选块/BlockItem及4 mixture_carrier_solid/liquid/gas/unresolved通用载体；复用CreativeCategories.register(MATERIALS)，根入口/CreativeCategories保持只读。
- 这些是游戏形态容器/候选身份，不是118种元素室温固态声明。没有新增热力学数值或配比枚举。单质旧物品仍为权威已有标定入口；新块物品尚无默认组成，绝不由ID造物。
- 122个原创框架模型，每个7部件，244张独立32×32 RGB纹理：内嵌表面、边框、铆接、顶部原子序编码；4通用载体有固体纹理/液位窗/气泡窗/未定斜纹。颜色及框架均游戏视觉约定，不是物性数据；不读取/改绘旧元素物品图或原版贴图。所有blockstate/item model/loot完整配套，14个static block/item子tag仅内容分类。
- en_us/zh_cn各新增135键，追加保所有原工作区键值（生成器写前校验、并发字节检查）；不覆盖旧键。模型与纹理未实机验收。

## 真实行为入口与安全

`Chemicalelements -> ElementItems.bootstrap -> ElementMaterials.bootstrap` 在既有CreativeCategories.bootstrap前注册。`MaterialBlockItem.place(BlockPlaceContext) -> MaterialAdmission.inspect(ServerLevel,ItemStack,target)`，只从StackLedgers.get读取既有账本，不调用resolve/mint/normalize，不修改输入/世界。单次捕获AtomicChemistryCatalog.INSTANCE.bundle，对每个原InstanceRecord保留UUID、revision、Composition、ThermalState、核素、孔隙液、吸附；同bundle materials.query及phases.models查询模型身份。不把species.phase或carrier名当实时物相。

检查服务端线程、目标、quarantine、缺账本、完整count、epoch、空物质、元素原子身份/电荷、所有实例行为及模型。多组分/吸附/孔隙保UNMODELED，无完整模型保UNKNOWN。Inspection包含完整expected ledger和原bundle，matches检查当前账本及bundle对象，但不是Owner证明或耐久CAS。当前生产118均UNKNOWN，不能用已有solid记账字段升级。即便将来MODEL_AVAILABLE也仅TRANSACTION_REQUIRED；所有结果mayPlace=false，place返回FAIL，绝不super.place/setBlock/shrink。

未接世界Owner前候选块为不可挖/高爆抗游戏壳，loot pools为空，避免名义掉落造物。这不授权命令强置块为有库存；没有BE/第二库存或fake journal。正常玩家放置、拾取、相变均未完成，不将拒绝冒充可放置验收。静态模型不包含实物流体或气体模拟。

## 必须由现owner补齐（已向编排精确请求）

1. B：玩家真实slot→world block Owner放置、拾取与相变的同journal reserve/commit/cancel/recovery；完整FIFO/UUID/revision、pos/state、catalog epoch/hash/reference、有限热源、核素/吸附/孔隙/容量、权限/碰撞和跨OwnerCAS；没有端口故本批不提供虚假成功回调。接点为MaterialBlockItem.place与MaterialAdmission.Inspection，未来须在公共B端口正式释放后接线。
2. item_compositions现owner：评估新增118 `chemicalelements:elemental_<name>`的显式游戏标定和与旧element_*的单位关系；目前无新增映射。4通用混合载体不可默认赋予名义Composition。material_behaviors当前3规则可只读消费，无需本批越权修改。
3. thermal/环境owner：已核相态数据、压力/reference/有限热源/密度容量及真实形态模型；无完整数据拒绝，不凭记忆补Cp/潜热。完整118可放置、热源、形态事务继续保留为未完验收。
4. 唯一chem-integration-execute-r22-next：冻结此manifest及material-reload-r23/thermal-phase-implementation-r23/element-compositions-r23依赖，下一明确同版编译、GameTest、独立main；不得混入固定2693。水/机器生产门槛未改变。

## 验证分层

- MaterialRegistrationGameTests：真实注册118+4、旧118身份、loaded tags/创造分类/服务端loot资源。
- MaterialAdmissionGameTests：3个GameTest源码：真实sampler本地测试账本→全部118 UNKNOWN及完整输入保留；真实新BlockItem.place无账本拒绝保堆/世界；真实bundle混合查询UNMODELED和有限载体。测试mint仅测试局部数据，不伪造生产Owner。
- MaterialAssetsTest：独立main源码，通过打包classpath检查两语言、122模型/parent/blockstate/无名义掉落。服务端ResourceManager不包含客户端assets，因此不冒充服务端测试已检查客户端语言模型。
- `python -B src/main/java/org/goldgom/chemicalelements/content/material/verify_static_r23.py`：严格JSON重复键、引用、122/244/14计数、PNG CRC/解码/非纯色且244唯一hash、原ElementItems完整字节还原hash、语言旧键、索引hash/13暂存、diff-check及manifest。生成器首次在内存非纯色细节门槛失败，未写任何资源；增加表面细节后独占创建746资源成功。一次宿主重复命令返回旧completed失败，已get核原回执后用修复后的新调用执行，并非重放未知写。

Java未编译，4 GameTest及独立main未运行，客户端未运行，美术未截图；静态证据不证明API编译正确或运行可放置。不git add/reset/commit、不碰正式mods/存档、不删数据不外发。所有动作同步，无本人后台生成/构建。
