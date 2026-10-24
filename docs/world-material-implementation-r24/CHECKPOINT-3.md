# checkpoint 3 — 尚未完成，不准启用

在checkpoint2后：
- LevelChunk Mixin新增getBlockEntityNbtForSaving HEAD：直接原BE.saveWithFullMetadata，异常不再被原catch转null；pending原NBT复制，不lazy创建BE。
- 新WorldMaterialChunkSerializerMixin仅write RETURN严格附件重新编码对等/BE集合恰好一次检查。还没有read HEAD gate或隔离raw编码；附件重复调用存在codec副作用风险，需改成唯一实际调用结果捕获和catch外失败标记，不能宣称全路径闭合。
- 新ChunkWriteFence登记真实chunk→lane及image identity，防伪自报NBT。当前没有生产调用，ChunkStorage Mixin尚未创建，不得说已拒旧autosave。failed编码保持closed，需要显式reconcile；不能自动丢未知副作用。
- 当前8个Mixin均未注册，不能单独提前交JSON启用。仍缺ChunkStorage/Unload/Explosion/RegionFile四个。
- 工程尚有完整空气迁移/原section发布/严格raw encoder/恢复与卸载生命周期等独立实施工作，下一续轮继续，不以外部B接线需求为停止全部工作理由。

原BE桥合同在CONTRACT-2.md。22文件manifest为上批历史，当前新版本另见manifest-checkpoint-3.json。未编译/测试，无运行证据。所有已发写操作返回成功，无本人后台写或unknown。水生产门未改。
