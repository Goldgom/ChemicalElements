# World material boundary r24 — 有界实现前置调查

日期基准：2026-10-24 08:06 EDT。MC 1.21.1 / NeoForge 21.1.264。
状态：**准备交付，不是修复完成、Owner-ready、编译或运行证明。水生产保持关闭，完整跨 chunk 事务范围不缩减。**

## 1. 已核实事实

取证来源是工程现有 `build/moddev/artifacts/neoforge-21.1.264-sources.jar`，没有下载、缓存全扫描、Gradle/javac/client/截图。`vanilla-paths.txt` 保留文件名与原始行号，`source-entries.json` 保留逐 entry 哈希。取证脚本大括号计数在字符串含括号处可能提前结束，RegionFile.write 后半段已另行定点读取；以下结论包含该补读，不将摘录当完整源码。

- Level.java:216 `boolean setBlock(BlockPos,BlockState,int,int)` 调用 chunk.setBlockState 后才 markAndNotifyBlock；captureBlockSnapshots 不消除 chunk 内 onRemove。378 `getBlockState(BlockPos)` 会取 chunk；771 `getBlockEntity(BlockPos)` 使用 IMMEDIATE、会创建 BE。不能拿两者作 loaded-only resolver。
- LevelChunk.java:239 `BlockState setBlockState(BlockPos,BlockState,boolean)` 第249行先修改 section，再更新 heightmap/light，274 onRemove，283 onPlace，再改 BE map/ticker。flags=0、64、capture snapshots 均不是无回调原子发布。
- LevelChunk.java:329 `getBlockEntity(BlockPos,EntityCreationType)` 即使 CHECK 也会移除 removed BE、promote pending NBT。当前内容 Access.isLoaded 已改用 `chunk.getBlockEntities().get(pos)`，比旧板描述更新；仍需受控 raw peek 以免与新map屏障递归冲突。
- LevelChunk.java:528 getBlockEntities() 返回原 map；ChunkAccess.java:160 getSections() 返回原数组；LevelChunkSection.java:140 getStates() 返回可写 PalettedContainer；仅拦截 Level 方法不闭合。
- LevelChunk.removeBlockEntity 439 修改map、setRemoved、game event与ticker；clearAllBlockEntities 616先 onChunkUnloaded/setRemoved 后 clear。ChunkMap.scheduleUnload 505在save前 setLoaded(false)、发Unload事件；必须在此之前保留事务chunk，不能等到clear时才拒绝。
- Explosion.finalizeExplosion 351逐位置 getBlockState().onExplosionHit，随后派发掉落。边界要在回调前筛掉/延期受保护位置；不能等setBlock抛错后已发掉落。
- ChunkSerializer.write(ServerLevel,ChunkAccess) 287：section NBT 333，BE NBT 359，attachments 399。**BE保存异常被LevelChunk.getBlockEntityNbtForSaving捕获并返回null；attachment异常被serializer捕获只记日志**。因此返回CompoundTag不足以证明完整根被保存，必须验证预期根/metadata在输出中逐项且唯一相等；不允许忽略丢失项。
- ChunkSerializer.read 214读取attachment，248发Load事件。隔离bootstrap必须早于可观察Load事件/BE onLoad，不是晚注册tick恢复。
- ChunkMap.save(ChunkAccess)是private boolean：747先clear unsaved；763 serialize，764 Save事件，765异步write并exceptionally记录失败，770返回true。它不是同步成功屏障。
- ChunkStorage公开签名：`CompletableFuture<Void> write(ChunkPos,CompoundTag)`；`void flushWorker()`。ServerChunkCache.chunkMap是public final。IOWorker.store会按chunk合并pending image；runStore写RegionFileStorage后complete或completeExceptionally；synchronize(true)等待当时pending并flush。
- RegionFileStorage.flush调用region.flush；RegionFile.flush执行FileChannel.force(true)。**必须持有本次write的future并join，再flushWorker**；只flush可能看不到已移出pending的失败。
- RegionFile.write 311更新offset/header，314执行external commit。writeToExternalFile 328临时文件write后close，336 replace move，没force临时文件；region force不force `.mcc`。region header写也不是原子替换整region。不能把本方案宣称任意时刻进程终止/掉电均保持原版chunk完整，必须正式补底层写协议或限制耐久承诺并保隔离。

## 2. 当前B/content真实契约

实读 instance/materialtx 的 MaterialOwnerAccess / MaterialObservationBarrier / MaterialCommitCoordinator / MaterialJournal / MaterialRecoveryBatch，内容MaterialBlockEntity、MaterialProjection、tx/MaterialRootState、MaterialAdmission。内容owner仍可继续改动，manifest只是取证时点，不冻结。

`MaterialOwnerAccess`现有签名：owner():OwnerKey，isLoaded():boolean，isolationGap():Optional<String>，snapshot():MaterialOwnerSnapshot，observationRoots():Collection<?>，classify(UUID,String,before,after,boolean):Position，isolate(MaterialPendingMarker)，project(MaterialOwnerSnapshot,MaterialPendingMarker)，persist()，complete(UUID,String)，cancelIsolation(UUID,String)，notifyCommitted(UUID,String)。

现BE有一个immutable MaterialRootState，字段 incarnation/image/pending/last；image包含完整ledger、revision、block_state及finite_heat证据。业务canonical不包含pending/last，避免hash自引用。isolate保持before；project一次root引用赋值并带PROJECTED。saveAdditional一个material_root字段保存全体。当前project要求state不变；persist明确抛SYNC_CHUNK_ROOT_DURABILITY_NOT_INTEGRATED，isolationGap非空。MaterialAdmission.mayPlace始终false。

B coordinator及Held、prepare/forward/cancelAllBefore/attachRecovery均package-private。**无可直接供content/C调用的生产鉴权入口，本候选不扩大它们可见性、不在外部调用、不虚构入口。** B仍负责玩家/菜单身份、权限、bundle/reference、守恒、同UUID journal与恢复dispatch。

B现在顺序：reserve所有根→requireAll BEFORE→journal PREPARE同步→再验BEFORE→ACTIVE/lease.beforeProjection→逐owner isolate+persist→forward逐owner project+persist→验AFTER→journal COMMIT同步→逐owner complete+persist→release→notify。存在待B修正：PREPARE已耐久但首个isolate尚未保存就崩溃，重载generation不同且无marker的真实BEFORE被当前classify拒绝；恢复加载门须从同journal先建立reservation或增加严格原before证据恢复原语，不能泛化recovery=true绕过generation。

## 3. 建议单权威根与空气身份（方案，未实现）

选择 **position-lifetime incarnation**：一个已纳管位置从空气→材料→空气始终同incarnation，revision严格单调。卸载仅变liveGeneration；正常替换也不能删位置墓碑/重置revision。这样不必在一事务中更换OwnerKey，符合B change.before/after同owner。这里incarnation指位置所有权世代，不是每个Java BE对象。旧BE handle由generation与raw map identity拒绝。

首次只在已加载目标位置纳管，生成UUID、revision=0的空身份并同步保存后才能出具snapshot；未纳管空气无owner，不扫描世界、不假造空气BE。纳管是零物料身份元数据，不自然水初始化、不铸造ledger实例。

新增chunk位置metadata只保存 pos→{incarnation,revision,occupancy kind,root hash,pending/last,retired}。**不得复制BE ledger、热余额或水量**。空气是空物料的真实根：canonical由实际air BlockState+revision+空ledger确定；metadata是它的唯一持久身份与pending根。材料存在时业务根仍仅在真实MaterialBlockEntity，metadata只有身份/定位/hash（pending以BE为准，不建立第二决策日志）。BE→air只有完整ledger被同B事务移往其他owner或合法sink后才允许。

迁移在受保护chunk内完成：构造新immutable business image及metadata；BE↔空气从旧承载者移动唯一权威，不复制活跃库存。以所有观察入口隔离实现“不可观察的临界区”，不是声称多Java字段有硬件原子性。材料root引用、BE map、section投影、metadata修改后统一序列化在同一chunk image。空气没有BE；新材料BE接收原位置incarnation，不接受constructor随机UUID成为新owner；旧材料BE失效不能再解引用ledger。

墓碑不自动GC，尤其不能在空chunk清理时丢弃。若未来GC，须同唯一journal的durable checkpoint证明无pending/历史恢复依赖，并永久保generation epoch防ABA；本版不做GC与incarnation轮换。元数据解码失败、BE/metadata不一致、缺BE但metadata称occupied、重复位置均隔离，不补空余额。

## 4. 世界隔离与受控原语

最小实现边界不是两个Level mixin：
1. Level/LevelChunk普通读写、getFluidState、BE读取/添加/移除，爆炸回调入口、卸载入口、网络chunk序列化都检查位置/区块reservation；恢复pending的chunk在发Load之前挂上隔离。
2. ChunkAccess.getSections始终返回数组clone，阻断通过数组元素替换内部section；getSection需直接内部索引避免无意义clone。section对象绑定原chunk/section索引；读写方法guard。早期逃逸的section对象在事务开始后也受guard。
3. PalettedContainer的get/getAll/count/maybeHas/copy/pack/write及set/getAndSet/getAndSetUnchecked/read/onResize都需guard绑定的原palette；只保护getStates返回不够。旧palette alias保持隔离或变成不可写的detached旧视图，不能清绑定后继续回写新根。客户端/世界生成未纳管容器不受服务端事务逻辑污染。
4. 原blockEntities/pendingBlockEntities map在构造期换成守卫实现（或杜绝原map逃逸）。覆盖get/put/remove/clear/compute/merge/replace/entry.setValue、iterator.remove、keySet/values/entrySet、forEach及其保留view；不能只返回Collections.unmodifiableMap而允许既有原map alias。metadata attachment不得暴露可变map；getData返回immutable数据，setData/removeData同样纳管。
5. 原BE直接持有别名仍须所有business读写/serialize/updatePacket/heatEvidence受guard；内容当前heatEvidence虽检查loaded但不ordinary，必须区分受验证内部证据读取和普通观察。
6. chunk卸载由ticket/保留机制在scheduleUnload开始前阻止；失败保留isolated与恢复登记。服务端停止可保存pending但不能以关闭事件清pending。不能用抛异常随机终止服务器来代替生命周期协议。

原子投影不调用Level.setBlock或LevelChunk.setBlockState。新增world内部桥只执行明确的无回调原语：swap root，更新section计数/palette，换BE map并失效旧handle，更新heightmap/sky light的缓存；game event/ticker/neighbor/client通知在全局commit+persist+release后幂等重建。需要审计light API是否调回世界，必要时先标记待重建而非临界区调用。禁止借captureBlockSnapshots绕过onRemove。

受控能力绑定 exact owner、operation/globalHash、目标对象identity、原语enum、server thread及一次消费。候选WorldPrimitivePermit实现绑定与一次消费；它**不是B授权令牌**，世界桥只有验证B durable reservation后才可mint。token显式传入内部接口，不设ThreadLocal、不接收Runnable/Supplier回调、不临时关闭普通guard。跨包mixin bridge即使public也须验证token身份，不能暴露无token raw setter。

拟新增具体内部签名（均为方案而非已存在接口）：
```java
Optional<MaterialOwnerAccess> WorldMaterialRoots.resolveLoaded(ServerLevel level, BlockPos pos);
MaterialOwnerAccess WorldMaterialRoots.enrollLoadedAir(ServerLevel level, BlockPos pos); // 同步身份持久后才返回
void WorldMaterialRoots.project(MaterialOwnerAccess access, MaterialOwnerSnapshot after, MaterialPendingMarker marker);
void WorldMaterialRoots.persist(MaterialOwnerAccess access);
void WorldChunkBoundary.requireObservable(LevelChunk chunk, BlockPos pos);
CompoundTag DetachedChunkImage.encode(ServerLevel level, LevelChunk chunk, WorldPrimitivePermit permit);
void ChunkWriteFence.writeAndForce(ServerLevel level, ChunkPos pos, CompoundTag detached);
```
world resolver只返回原physical access，热alias不得创建第二access。生产caller只能由B最终发布且验证的真实入口触发；上述函数不是玩家提交入口。

## 5. 持久顺序、自动保存错序与恢复

必须保存**完整原chunk image**，不是另一个物质库存文件。位置metadata可选NeoForge attachment（实际有AttachmentType.builder(...).serialize(IAttachmentSerializer).build），但建议专用ChunkSerializer注入字段以fail-closed；原serializer吞attachment错误要求额外严格输出验证。不会新增第二journal，重做业务image来源只能是B现SinkLedger的完整before/after payload。

每chunk一个串行write fence和generation：阻止旧image在新image之后提交；同一ChunkStorage.write处接入所有普通save和事务save，普通write不得绕过。入锁前drain旧queue；受保护期间不得跑Save事件任意回调后继续相信旧编码。持久用detached不可变NBT，在编码前后核所有root/marker/metadata、section state一致，验证没丢BE。保留dirty直到本future与force成功；失败置dirty并保留隔离，重试同UUID而非new交易。

候选ChunkWriteFence实际调用公开chunkMap/write/flushWorker，仅实现**已正确隔离且已验证detached image前提下**的IO等待；本候选不实现上述前提，不能直接替换BE.persist。

完整顺序：
A. 纳管空身份先同步保存；所有owner snapshot/validation、root lease与chunk unload pin；旧save drain。
B. 唯一journal PREPARE force成功；失败不投影。此时启动gate必须从journal建立位置隔离，即便chunk没有pending。
C. 所有owner RESERVED根保存并future成功+force；不可在逐owner隔离期间暴露其他owner。
D. 无回调投影after+PROJECTED与原版section/cache，逐chunk完整image fence；任何异常不退款、不release，留forward recovery。
E. 核所有AFTER；journal COMMIT force；再逐owner清pending保last并持久；所有成功才release/pin、幂等重建观察缓存通知。
F. retry AFTER也要重新确认IO，不能用内存last代替落盘。

崩溃点：
- A前或身份write失败：无业务snapshot；重启按真实metadata有/无处理，不产生余额。
- PREPARE前：只有身份，可安全无业务变化。
- PREPARE后、任一RESERVED前：从journal index在chunk可观察前隔离；B验证owner/incarnation+完整before+原op，不允许因为generation变化丢单，也不允许任意旧journal覆盖后续revision。
- RESERVED/PROJECTED部分chunk：before/after逐根分类，所有owner已加载才forward；未加载WAIT，已加载端仍隔离，禁止强制全图加载。
- COMMIT后清pending部分：last+完整after确认、重flush，不能重复发产物。
- CANCELLED：仅全BEFORE才取消；取消清marker后重启应由durable CANCELLED加原before进行幂等确认，不能要求已被清除的marker仍存在。
- stale autosave：fence处拒绝低generation，drain或重编码，绝不在PREPARE/COMMIT后允许旧原版image覆盖新根。
- corrupt/torn chunk：不能“看不到owner就重新纳管空气”；需load失败隔离并由现journal验证业务恢复。现原版region写不保证整个chunk可读，不能靠业务journal恢复非业务世界数据。正式底层需copy-on-write同region文件的临时完整image+force+atomic replace并串行切换channel（临时文件不是第二物质库存或第二journal），同时处理external .mcc的data force/发布顺序及失败保留；或明确以fail-closed损坏区块为未恢复阻碍。**这部分尚未给出可投入生产的实现，不能宣称崩溃恢复闭合。** Windows目录fsync/掉电保证与B现SinkLedger注释一致，不能许诺硬件全掉电。

## 6. 最小正式写权清单与归属

world新owner：`world/material/{WorldMaterialRoots,WorldPositionMetadata,WorldChunkBoundary,WorldPrimitivePermit,DetachedChunkImage,ChunkWriteFence}.java`；候选仅实现后两类中的fence及permit原语，不伪造其他类。
world新mixin：`WorldMaterialLevelMixin`、`WorldMaterialChunkMixin`、`WorldMaterialChunkAccessMixin`、`WorldMaterialSectionMixin`、`WorldMaterialPaletteMixin`、`WorldMaterialChunkStorageMixin`、`WorldMaterialChunkSerializerMixin`、`WorldMaterialUnloadMixin`、`WorldMaterialExplosionMixin`。需要region强崩溃恢复另含`WorldMaterialRegionFileMixin`与同路径写协议，不能遗漏此依赖。
content原owner：MaterialBlockEntity（唯一根内部桥、air承载迁移、guard、persist接线）、MaterialProjection、tx/MaterialRootState、MaterialBlock/MaterialBlockItem/MaterialAdmission（最终调用B鉴权入口）。不冻结、不竞争写。
B原owner：MaterialObservationBarrier（受控内部能力契约）、MaterialCommitCoordinator/RecoveryBatch（首isolate前重启、cancel无marker幂等）、实际生产鉴权service、SinkLedger startup pending索引与确切持久顺序。世界owner不改它们。
C原owner：热alias归一、loaded generation恢复guard、水chunk owner及完整跨chunk事务；不把本方案当水就绪。
现`chemicalelements.mixins.json`没有上述world mixin；最终由当前配置owner集中注册、defaultRequire=1，不能覆盖已有B mixin列表。所有新增文件需正式释放后才能写src；本轮只docs。

## 7. 真实入口验收矩阵（未执行）

1. 使用最终B真实授权放置入口：已加载真空气→材料，inventory减与BE增同UUID；重启空气identity/同incarnation单调revision，旧handle拒绝。
2. 拾取材料→真空气→再放置，空位置无BE，metadata持久，累计量不变，ABA旧revision拒绝；随机UUID constructor不成为新owner。
3. 玩家破坏、爆炸、活塞、命令setBlock、直接chunk.setBlockState/removeBlockEntity、保留map view/entry/iterator及section/palette别名在reservation期间均不能读/改半投影；爆炸不提前掉落。
4. BE load/save异常、attachment codec异常、遗漏/重复BE、dirty先清、write future异常、force异常、磁盘满均不得成功receipt；retry exact op不重复物料。
5. 双chunk水迁移：A PROJECTED保存/B BEFORE后进程终止；加载A单侧先隔离，B未加载不扫描/不投影；两者加载后只forward同journal；原水墓碑同事务不重复初始化。
6. 分别在PREPARE后首isolate前、每个RESERVED/PROJECTED、COMMIT与clear之间杀进程；含旧save故意延迟到新save后，检测write fence拒旧。
7. 强制.mcc超大chunk、region header写中断、external move失败和重启损坏：必须证明真实恢复或隔离，不用mock成功覆盖。
8. 卸载/停止/load事件/网络发送/光照读/邻居回调重入；未release前不得观察new section配old BE或反之。
9. 重复COMMITTED/CANCELLED及reload generation变化，完整哈希+last分类；无二次掉落、无第二热/水库存。

## 8. 候选补丁范围及剩余工作

candidate.patch是可应用的新文件diff，仅含WorldPrimitivePermit和ChunkWriteFence，两者是可执行Java原语候选，不是完整世界接线；未编译、未应用src。prepare_patch.py生成diff/manifest并只做`git apply --check`（不改索引/工作树）。这避免把不存在的授权接口写成调用，也避免用stub假完成。

前置调查已指出可实施路径及无法由单纯flush闭合的region风险。完整正式实现仍需上述owner释放、无回调发布/守卫map+palette、detached编码、全write串行与region崩溃协议，以及B真实入口接线。报告165/164本轮reports API返回items=[]，故事实采用当前实际源码，不冒称已取到这两个报告正文。
