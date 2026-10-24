# A rev22 首批源码（未完成业务交付）

只写原工程 core 与 gametest/core 新增3文件；没有改B/D/C、公共三类型、schema1/2/hash、既有LedgerCodecGameTests、注册或资源数据。没有Gradle/javac/client/截图，没有修改integration-r22固定快照或classes。index SHA仍82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69。

## 实质实现
- core.VanillaCraftingAdmission.inspect(ServerLevel,RecipeHolder<CraftingRecipe>,CraftingInput,long)：真实RecipeManager对象身份、目录epoch和server thread校验；只读实际StackLedgers、不resolve/normalize/mint。
- 每个非空槽验证count/ledger一致，完整栈UUID重复检查，仅取本次实际FIFO head；保留InstanceRecord的remaining/thermal/nuclides/poreSolution/adsorbed。非298.15K输入明确拒绝，不修改活输入。
- matches/assemble/getRemainingItems分别接独立深拷贝，检测配方修改传入副本，保留真实输出所有非化学组件与逐槽完整动态余量；新输出预览剥去可能被配方copy的化学UUID，决不生成替代库存。
- 两条iron packing仅MECHANICAL_CANDIDATE，绝不是成功提交许可；其余真实crafting会保留形状/余量但PROCESS_MODEL_REQUIRED，不能把元素向量相同误作常温反应可行。
- Admission getters再次深拷贝；revalidate检查目录重载、配方对象替换、输入所有组件和数量变化、输出/余量变化。cancel仅取消此预览，无预约、不返还、不写journal；重复校验不分配UUID。

## 调用路径与缺口（不能包装成完整路径）
当前真实调用方：新增GameTest -> 服务端RecipeManager真实原版配方 -> Admission -> StackLedgers现有组件读取。生产mixin尚未接入。
B已明确当前没有成功reserve/commit/cancel接口。旧AbstractContainerMenuMixin clicked WrapOperation和Crafter dispenseFrom仅vanilla后置Settlement.Router.CRAFT，不能冒称原子结算。本类须由B在真实提取/每次消耗前调用，同时覆盖QUICK_MOVE循环、直接quickMoveStack和结果槽；单clicked或onTake末尾不足。B需保管真实Container/slot、owner generation/revision及同journal UUID，完整产物/残渣/返还/气/热/核余量联合事务，生命周期取消/重载/重复提交仍未完成。

## 测试源码（全部未编译、未运行）
VanillaCraftingAdmissionGameTests新增3方法：
1. realPackingAdmissionLifecycle：真实iron_block recipe、9 FIFO记录、深拷贝、重复预览、错误epoch、输入变化及重复取消保输入。
2. realCakeRemaindersPreservedAndRejected：真实cake assemble、9槽余量及3桶，条件未建模拒绝保输入、missing ledger不采样。
3. dynamicRepairUsesRealComponents：32动态之一repair_item，真实damage assemble与2槽余量、拒绝保输入。
LargeInventoryLifecycleGameTests.multiSlotLargeInventoryRecovery：27槽中3个16实例栈、8193bit matter及核素/孔隙/吸附、非零热状态；丢弃decoded cache后经ContainerHelper save/load和实际STREAM_CODEC；损坏压缩payload原字节隔离不影响邻槽；重复保存恢复。不是磁盘服务器重启/迁移链通过证据。

静态检查仅本版Minecraft源码签名核对、括号配对、manifest/hash回读。待唯一集成者下一批编译/运行；不以静态检查代替javac/GameTest。

## 全范围覆盖矩阵
coverage.json逐项保留1290 recipe ID/type/原审计状态/当前入场范围/生产hook与CAS缺口。
当前真实审计文件为545平衡+522残渣+191改写+32动态=1290。类型为887 shaped/shapeless+14特殊crafting+250 stonecutting+112热加工+27 smithing；32动态=14特殊crafting+18trim。纠正旧摘要不算实现。
- 901 crafting：只读真实形状/组件/余量通用算法已写；仅2个cold packing候选，其余需工艺模型。无成功事务、无生产接线。
- 250 stonecutting、112热加工、27 smithing：本批未适配入口。
- 32动态：通用crafting分支可读取其中14种，新增真实测试仅repair_item；18trim未适配；所有动态材料分配/容器返还/CAS仍待完成。
- 545/522/191全部仍须真实完整守恒事务或科学改写；不能按审计类别视作运行通过。
- 大库存：新增容器/压缩/隔离恢复源码；旧schema迁移、实际磁盘存档重启、实际网络发包/分页极限、完整生命周期仍未验。

下一步：唯一集成者下一批编译与GameTest；B正式接线与预约/CAS完成后A针对真实入口再增加取消、重载与重复提交UUID用例。本批保持水和机器生产关闭。完整1290/32/迁移均未完成。
