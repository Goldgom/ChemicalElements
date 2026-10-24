# r24 精确接管补充 v2

用户时间：2026-10-24 08:20 EDT。补充只写docs，业务只读；当前宿主轮为message，旧任务已completed/submitted，所以不对旧任务登记stage/wait或冒称再次正式final。由共享板交付新增附件，下一实施任务接管。

## v2输入已实读

MaterialProjection真实五参为 `(BlockState state,long revision,StackLedger ledger,Optional<FiniteHeatEvidence> finiteHeat,Optional<String> unitStack)`，保留四参兼容构造；JSON schema仍1，v2是交付版本不是NBT schema2。unit_stack保原单件完整组件，validateUnit(HolderLookup.Provider)校验codec canonical/count=1/原ledger一致。BE load与project均调用validateUnit；非空project拒绝缺unitStack。world编码/迁移必须保持该原字符串与完整FIFO，不创建默认BlockItem，不重编码丢name/damage/custom_data。空位置无unitStack。BE/MaterialRootState/MaterialProjection仍content独占，B鉴权/barrier/coordinator仍B独占。

## 精确新增world/material文件（下一任务申请，非已存在）

路径前缀 `src/main/java/org/goldgom/chemicalelements/world/material/`：
- WorldMaterialRoots.java：loaded resolver、空位置纳管、原physical access选择；不含玩家鉴权入口。
- WorldPositionMetadata.java：immutable位置身份/墓碑；不存第二ledger/heat/water库存。
- WorldChunkBoundary.java：position/chunk生命周期与公开可见性gate。
- WorldPrimitivePermit.java：已有docs候选，owner/op/hash/target/primitive一次消费。
- GuardedBlockEntityMap.java：真实map及entry/view/iterator完整guard。
- DetachedChunkImage.java：从真实根经受控编码产生完整chunk，严格验证material_root、metadata、section一致及unit_stack。
- ChunkWriteFence.java：已有docs候选IO原语，不可单独当完整persist。
- ChunkWriteSequencer.java：所有同ChunkStorage写的generation/provenance排队与错误粘滞。
- WorldRecoveryGate.java：B提供的唯一journal索引在加载前建立位置隔离；不另建持久journal。
- AtomicRegionPublisher.java：同region锁、完整region临时image发布、channel/cache替换、故障保留。临时文件只是同权威文件待发布image，不可当另一个库存读取。
- ExternalChunkPublication.java：.mcc数据force/rename/失败状态及外部引用一致性；不能只force region。

12个拟新增mixin精确文件、目标descriptor与注入点在MIXIN-HANDOFF-v2.md；集中注册增量在mixins-additions-v2.json，交现有mixins.json owner合并，不覆盖现列表。没有发现本项目现mixin与新目标class直接重叠；与WorldMixins$Place/$Drops、MovementMixins$Piston/$Falling有语义顺序交互。未检查运行变换后的第三方冲突，不能声称运行无冲突。

## catch漏存：必须将错误提升到保存失败

不能用“saveAdditional没抛错”证明完整性。严格路径先在同snapshot generation捕获预期位置集合与每个原root的完整NBT/hash（含pending/last及unit_stack），然后编码所有sections与BE。编码后的最终CompoundTag必须满足：预期BE恰好一次，原root逐字/结构相等；空气metadata恰好一次且无BE；坐标、state、revision、incarnation一致；不得存在未知重复material_root。错误包括BE save catch返回null、attachment catch丢metadata、codec返回partial、Save事件篡改。

具体注入策略：ChunkSerializer.write RETURN检验位于原catch之外，检验失败抛明确MaterialChunkEncodingException。但ChunkMap.save自身又catch，所以事务persist不能以ChunkMap.save返回boolean为成功；由受控DetachedChunkImage调用并捕获失败向B传播，普通save失败则保持dirty+粘滞失败记录。隔离中不调用普通serializer的受guard getter；专用detached桥必须复制非业务chunk数据并直接编码原root，而非ThreadLocal暂时放行。若兼容Save事件，事件执行后再次完整校验，且事件不能访问隔离根；不接受事件后的原始tag直接写盘。

## 本次future与旧异步写的排序

所有ChunkStorage.write入口（含普通save）进同一sequencer，不仅事务调用者加锁。编码开始记录chunk-generation，编码完成登记tag identity→{chunk,generation,root hashes}的内存provenance，交IO前copy。不信任NBT自报revision或无provenance外部tag。已经编码的旧image即使稍后才调用write，也必须因generation<current拒绝或由服务端重新编码；单靠flush旧队列不能阻挡“尚未入队”的旧任务。

临界顺序：停止该chunk新普通编码/卸载→drain所有已接受旧future→确认无未决失败→分配递增generation→编码/验证→提交本次write→保留并join该future→flushWorker→回读/校验必要的已发布generation→解除本次IO fence。持久lease直到B完成全局commit。enqueue统一单线程串行，IOWorker按位置coalesce只能发生在相同已授权generation或完全被下一完整图像覆盖且所有等待者语义明确的场景；不能用共享pending future错认某个被覆盖image已单独持久。

异常传播：submitTask异常、RegionFileStorage.write异常、write future exceptional、flush exceptional均保留原cause并抛到persist/B；不exceptionally(e->null)变成功。普通save可日志但不可清粘滞错误或推进成功generation。失败结果可能已写入，因此不能盲目回滚/新UUID，原op重读分类、retry AFTER仍force。取消/超时不证明IO未执行；未决future不允许释放写锁继续冲突操作。

## .mcc / rename / force 的准确边界

既证原版顺序：external临时payload写/close（无force）→region header改为external stub→Files.move(REPLACE_EXISTING)到固定c.x.z.mcc→稍后region.force。以下不能简化为在尾部补一条force：
- external旧→external新：旧region引用固定同名.mcc，先replace .mcc会让旧region看到新payload；后replace则新header可能暂时看到旧payload。
- inline→external：先.mcc force+发布再原子region发布可安全让旧inline仍可读；外部文件先发布本身不改变旧inline。
- external→inline：先原子region发布再延后删.mcc，旧外部文件是待回收数据，不能先删。
- 对原版固定路径 external→external，没有跨两文件原子rename。**不能声称完整region COW本身修好外部多文件崩溃一致性。**

推荐实施分两层但不缩目标：普通inline region用完整region COW+文件force+atomic rename，发布前同region锁，关闭Windows旧channel，发布后重新打开并重建cache；rename失败维持隔离并重开确定的旧权威文件，临时文件不自动选为新世界。external修改必须增加可验证的恢复协议：优先利用现唯一B journal完整before/after作为业务恢复证据，在启动gate下检测旧/新.mcc并重编码原chunk，但该journal不覆盖非业务chunk数据，故不能据此自动修复任意损坏原版数据。若要求任意.mcc整chunk可恢复，正式实现需选择“版本化不可变external payload+region中原子发布的引用，并扩展同一region reader/writer”或经授权的同一存储发布元数据协议；不是第二物质库存，但有原版兼容性迁移成本。未经这种协议实现/测试，.mcc相关事务必须报告ISOLATED/unsupported，不能成功receipt；此限制是未完成实现的fail-closed，不是永久删除跨chunk目标。

force与目录：payload写循环直到ByteBuffer耗尽→payload FileChannel.force(true)→同目录ATOMIC_MOVE（无silent fallback）→必要时对已发布文件force→目录项同步。Windows通用Java不保证可打开目录force；文件force不等于rename目录项持久。process crash（OS继续运行）与断电/内核崩溃分别记录：前者可依赖已完成系统调用的原子文件发布但仍须故障注入验证；后者需平台原生目录/卷刷新与真实文件系统保证，当前没有证据，明确不承诺。多文件/mcc期间进程崩溃也非自动安全，不能把所有问题归到断电。

## 重启隔离必须先于公开观察

onLoad后补为时已晚。B启动时先从原SinkLedger构建只读不可变pending/committed未清理索引，world gate初始CLOSED；索引未ready时所有纳管chunk维持不可公开状态，不能在ChunkSerializer.read线程调用会递归加载overworld的SinkLedger.get。B鉴权/索引发布接口由B定义，不在此虚构现有接口。

ChunkSerializer.read HEAD按dimension/ChunkPos/raw metadata与既有B索引预登记隔离，早于LevelChunk构造、attachment decode及ChunkDataEvent.Load。构造期guard maps/section绑定与load privilege只能执行精确原语，不能全线程放行。read RETURN只是附加最终对象，不是第一次隔离。metadata损坏/缺失但journal引用该位置，仍隔离，不能createEmptyChunk重新纳管。ChunkMap.handleChunkLoadFailure/createEmptyChunk对已纳管或journal引用位置拒绝自动再生。

仅在服务器受控恢复阶段，原loaded roots及B lineage分类完毕、所有必要owner就绪并durable commit后，才完成public accessible/ticking/sending future；恢复resolver使用内部已解码对象，不要求其已公开，避免“等待公开才能恢复/恢复才能公开”死锁。已有公开chunk发生新事务则同步建立gate与停tick reservation。异步prepareAccessible/Ticking/EntityTickingChunk方法HEAD检查不足：必须包装其返回future的完成发布点，等待gate完成；不能提前将内部chunk放入普通查询cache。第三方Load事件可能读世界，必须延后该事件或保持拒绝，不能让其读到半恢复根。

## 保证与未完成项

本补充提供精确接管表与可执行IO/能力候选原语，不代表完整mixin已写。实际content v2读取完成；候选未编译运行；世界隔离/原子迁移/region协议/B生产caller仍待正式实施。原报告“最小清单”的6 world类和10 mixin被本补充11 world类/12 mixin接管基线细化。权限仍仅docs；不等待B整任务，不越权更改B或content。
