# checkpoint 2：实际世界桥签名与剩余接线

此版本仍未编译/运行/注册，不Owner-ready。checkpoint1历史保留。

## content精确实现请求（原BE唯一ledger，不允许world写content）

新增已落盘 `org.goldgom.chemicalelements.world.material.WorldMaterialRootBridge`：

```java
MaterialRootState worldReadRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner, UUID operation, String globalHash);
void worldReplaceRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner, UUID operation, String globalHash, MaterialRootState replacement);
```

原MaterialBlockEntity实现上述接口。方法第一步consume：`permit.consume(owner, operation, globalHash, this, READ_ROOT/REPLACE_ROOT)`；接着确保root非quarantined、owner位置/维度为真实BE、incarnation匹配（仅尚未挂载的新BE可采用目标位置原incarnation）；读返回immutable原root，替换只一次原root赋值，无setChanged/notify/load/removed回调。不得公开无permit getter/setter。最终world控制可见性、map/section与metadata共同发布，B控制durable mint/COMMIT。world当前没有伪造任何mint生产调用。

原BE `isLoaded()`/Access.available不能再经过受guard普通getBlockEntities；要用精确内部identity桥或B定位guard；不能为此宽放普通getter。`observationRoots`需包含真实chunk，使已有section/palette/map别名同步保留。persist只在完整raw encoder与统一write入口完成后转world，不以setChanged顶替。

## 已落盘内部chunk桥

`WorldChunkAccessBridge.worldPeekBlockEntity(permit, owner, op, hash, pos)` / `worldReplaceBlockEntity(permit, owner, op, hash, pos, expected, replacement)`；ChunkAccess Mixin实现，GuardedBlockEntityMap先核pos==owner.blockPos，再consume target=原chunk；替换采用expected物理identity CAS，仅map直接操作，不回调。

## B请求

B需要提供不可伪造的durable PREPARE能力，绑定原OwnerAccess、owner、op/globalHash、before/after，验证真实journal记录与现lease。WorldPrimitivePermit本身只检查现reservation，包内constructor不能当durable授权；只有B验证后桥可生产mint。初始WorldRecoveryGate无公开ready，需B实际journal index通过服务器身份绑定的桥发布。不能自行拿boolean或public interface实现当权威。

## 第二批修正

AtomicRegionPublisher已从追加sector改为完整紧凑重建，保各非目标chunk原record/timestamp、CEM1 immutable引用，无无限追加增长。历史checkpoint1关于此风险的待办由此源码修正取代，运行证据仍缺。

WorldChunkBoundary仍强identity引用，需生命周期清理，且未完成恢复gate/卸载pin/爆炸延迟。原pending CompoundTag可变别名仍需处理。完整空气纳管/迁移、section原子raw桥、严格全chunk编码与write provenance真实入口、Windows目录项耐久/进程kill仍未完成。

JSON没有改动。当前落盘7个Mixin不应单独提前启用为正式世界功能；只有完整批由唯一owner合并和同版验证后才能讨论启用。原12名中的ChunkStorage/ChunkSerializer/Unload/Explosion/RegionFile仍待实现。
