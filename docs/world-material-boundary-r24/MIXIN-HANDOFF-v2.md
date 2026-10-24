# 精确 mixin 接管表 v2

目标descriptor来自工程merged.jar class方法表，不是猜测；注入方案尚未编译/运行验证。所有文件位于 src/main/java/org/goldgom/chemicalelements/mixin/。constructor仅选择下表列出的真实重载，包前缀均为net/minecraft/。

## WorldMaterialLevelMixin.java
目标 `net/minecraft/world/level/Level`
注入点：HEAD: position gate before reads, writes and drop callbacks

```text
setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z
setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z
destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z
getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;
getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;
getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;
setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V
removeBlockEntity(Lnet/minecraft/core/BlockPos;)V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialChunkMixin.java
目标 `net/minecraft/world/level/chunk/LevelChunk`
注入点：HEAD: block ordinary access; controlled bridge never calls guarded methods; load/ticker registration deferred

```text
getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;
getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;
getFluidState(III)Lnet/minecraft/world/level/material/FluidState;
setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;
getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;
getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;
addAndRegisterBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V
setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V
removeBlockEntity(Lnet/minecraft/core/BlockPos;)V
runPostLoad()V
registerTickContainerInLevel(Lnet/minecraft/server/level/ServerLevel;)V
clearAllBlockEntities()V
registerAllBlockEntitiesAfterLevelLoad()V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialChunkAccessMixin.java
目标 `net/minecraft/world/level/chunk/ChunkAccess`
注入点：constructor RETURN: install guard maps before escape; getSections RETURN clone; remaining HEAD guards; NBT returns detached copy

```text
<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V
getBlockEntitiesPos()Ljava/util/Set;
getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;
getSection(I)Lnet/minecraft/world/level/chunk/LevelChunkSection;
setBlockEntityNbt(Lnet/minecraft/nbt/CompoundTag;)V
getBlockEntityNbt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/nbt/CompoundTag;
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialSectionMixin.java
目标 `net/minecraft/world/level/chunk/LevelChunkSection`
注入点：HEAD guard bound original section; getStates RETURN register palette identity before escape

```text
getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;
getFluidState(III)Lnet/minecraft/world/level/material/FluidState;
setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;
setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;
hasOnlyAir()Z
isRandomlyTicking()Z
isRandomlyTickingBlocks()Z
isRandomlyTickingFluids()Z
recalcBlockCounts()V
getStates()Lnet/minecraft/world/level/chunk/PalettedContainer;
read(Lnet/minecraft/network/FriendlyByteBuf;)V
write(Lnet/minecraft/network/FriendlyByteBuf;)V
getSerializedSize()I
maybeHas(Ljava/util/function/Predicate;)Z
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialPaletteMixin.java
目标 `net/minecraft/world/level/chunk/PalettedContainer`
注入点：HEAD guard all listed overloads, including erased Object descriptors; no ambient bypass

```text
onResize(ILjava/lang/Object;)I
getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;
getAndSetUnchecked(IIILjava/lang/Object;)Ljava/lang/Object;
getAndSet(ILjava/lang/Object;)Ljava/lang/Object;
set(IIILjava/lang/Object;)V
set(ILjava/lang/Object;)V
get(III)Ljava/lang/Object;
get(I)Ljava/lang/Object;
getAll(Ljava/util/function/Consumer;)V
read(Lnet/minecraft/network/FriendlyByteBuf;)V
write(Lnet/minecraft/network/FriendlyByteBuf;)V
pack(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)Lnet/minecraft/world/level/chunk/PalettedContainerRO$PackedData;
getSerializedSize()I
maybeHas(Ljava/util/function/Predicate;)Z
copy()Lnet/minecraft/world/level/chunk/PalettedContainer;
count(Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;)V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialChunkStorageMixin.java
目标 `net/minecraft/world/level/chunk/storage/ChunkStorage`
注入点：HEAD cancellable: route every write through per-storage/per-chunk sequencer; return exact failing future, defensive NBT copy and generation provenance validation

```text
write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialChunkSerializerMixin.java
目标 `net/minecraft/world/level/chunk/storage/ChunkSerializer`
注入点：read HEAD pre-register recovery isolation from raw NBT + B journal index BEFORE construction/events; write RETURN strict complete-image validation outside vanilla catches; read RETURN attach metadata to resulting chunk; dedicated raw encoder bridge for isolated roots

```text
read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;
write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialUnloadMixin.java
目标 `net/minecraft/server/level/ChunkMap`
注入点：HEAD: defer unload before queue; save HEAD owned-chunk replacement path with strict encoder/future tracking; load gates prevent corrupt managed chunk regeneration; accessible/ticking/sending completion must stay gated, not merely check request time

```text
prepareEntityTickingChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;
scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V
scheduleChunkLoad(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;
handleChunkLoadFailure(Ljava/lang/Throwable;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;
createEmptyChunk(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;
prepareTickingChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;
onChunkReadyToSend(Lnet/minecraft/world/level/chunk/LevelChunk;)V
prepareAccessibleChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;
save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z
getChunkToSend(J)Lnet/minecraft/world/level/chunk/LevelChunk;
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialExplosionMixin.java
目标 `net/minecraft/world/level/Explosion`
注入点：HEAD: partition protected toBlow positions before any onExplosionHit/drop; retain deferred intent, do not retry damage to entities

```text
finalizeExplosion(Z)V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialRegionFileMixin.java
目标 `net/minecraft/world/level/chunk/storage/RegionFile`
注入点：HEAD: route managed region updates/deletion through atomic full-region-image publishing; flush includes tracked external publication failures; do not just add force after vanilla write

```text
flush()V
clear(Lnet/minecraft/world/level/ChunkPos;)V
write(Lnet/minecraft/world/level/ChunkPos;Ljava/nio/ByteBuffer;)V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialRegionStorageMixin.java
目标 `net/minecraft/world/level/chunk/storage/RegionFileStorage`
注入点：HEAD/whole-method wrappers: same region lock for readers/writers/channel-cache invalidation; close old channel before Windows replacement and reopen only committed image

```text
getRegionFile(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/storage/RegionFile;
read(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/nbt/CompoundTag;
scanChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/StreamTagVisitor;)V
write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V
close()V
flush()V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## WorldMaterialChunkPacketMixin.java
目标 `net/minecraft/network/protocol/game/ClientboundLevelChunkPacketData`
注入点：constructor(LevelChunk) before first chunk-data access (after Object.<init>), other methods HEAD; refuse/defer partial chunk packets, network send scheduling gates in ChunkMap

```text
<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V
calculateChunkSize(Lnet/minecraft/world/level/chunk/LevelChunk;)I
extractChunkData(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V
```
现有项目mixin：未发现相同目标class注入（依据本轮mixin目录定点搜索）；不等于第三方转换器无冲突。

## 语义交互与合并要求
WorldMixins$Drops拦BlockStateBase.getDrops；WorldMixins$Place拦BlockItem.place；MovementMixins$Piston/Falling拦活塞解析与falling setBlock。与上表不是同一注入点，但必须先世界gate再旧Settlement逻辑，不能通过调整priority默认放行。保留原owner与原注册。受guard内部编码不能回调原版getter形成递归；必须使用明确能力的detached桥。
上述12个mixin仅为最小接管基线；完整tick隔离还需调度层hold已证明不存在的公开访问，而非用异常崩服。实施时若增加ServerLevel tick调度hook必须先列精确descriptor再申请释放，不准宣称本表已覆盖所有第三方反射/Unsafe。
