# 世界材料实施 checkpoint 1

用户日期基准：2026-10-24 08:50 EDT。任务 chem-world-material-implementation-r24，实施尚未完成。

## 已完整消费

- chem-remaining-0821-r24 rev2 正文及附录 A–F；chem-b-five-mixin-release-r24 rev3 正文。通过工具长响应文件前21行完整读到两块正文，不声称读取附带 coordination 全部内容。
- docs/world-material-boundary-r24/REPORT.md、ADDENDUM-v2.md、MIXIN-HANDOFF-v2.md。
- 原B MaterialObservationBarrier/MaterialPendingMarker/MaterialOwnerSnapshot、原content MaterialBlockEntity；源jar ChunkAccess/RegionFileStorage/RegionFileVersion/ChunkSerializer与packet精确相关段。
- 开始写前逐项 Test-Path 证实 world/material、gametest/worldmaterial、src/test/.../world/material、此新docs目录不存在，WorldMaterial*Mixin不存在。根目录和src无AGENTS/CLAUDE匹配。
- git index SHA256 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69，13条暂存。未git add/reset/commit。

## 首批已落盘（仅源码）

1. WorldPositionMetadata：位置生命周期UUID/revision、AIR/MATERIAL、hash。occupied不复制BE pending/last，更不复制ledger；严格NBT字段类型/重编码一致性/重复坐标与跨chunk检测。
2. GuardedBlockEntityMap：原chunk为reservation root，所有保留views/iterator/entry逐次检查，包括Map默认compute/merge经由get/put/remove。尚需明确原pending CompoundTag别名与raw内部桥。
3. WorldChunkBoundary：原对象identity绑定，多chunk共享section保留全部原root屏障。当前强identity索引，生命周期清理待实现，不能声称无内存保留风险。
4. ChunkAccess/Chunk/Section/Palette/Level/ChunkPacket Mixin源码：构造期换map、数组clone、原section/palette别名检查、原入口检查。未注册、未验证Mixin应用，不当完整替换/爆炸/卸载生命周期已就绪。
5. ChunkWriteSequencer：编码generation+tag identity provenance，改动tag/迟到旧图拒绝；exact future链+原cause粘滞错误；本future join后flush，失败不推进durableGeneration。尚未接ChunkStorage入口/恢复reconcile。
6. DetachedChunkImage：对独立预捕获完整BE集合与附件、position metadata/root一致性验证。只是一份严格验证对象，尚未接raw全chunk编码器/section state一致验证/ChunkSerializer异常提升，不可充当persist。
7. AtomicRegionPublisher/ExternalChunkPublication/WorldRegionStorage：完整region COW并文件force+ATOMIC_MOVE；不可变SHA256 external payload和region内CEM1引用。reader支持原版gzip/zlib/none/lz4与旧fixed external。external→external不覆盖旧payload。发布后读回hash；rename后故障记uncertain，禁止盲重试。无自动删除、无第二物质journal。
8. RegionStorage Mixin对原read/write/scan统一redirect，不再走其private getRegionFile缓存；flush/close传播未知publication状态。尚未注册。外部直接RegionFile对象路径与存储协议迁移验收仍待，不得当前注册即认为全闭合。
9. WorldRecoveryGate初始CLOSED，无公开markReady，package-only安装/完成原语未提供production caller。尚未接B实际journal索引/ChunkSerializer read HEAD/future完成发布，不能说已阻止公开Load。

## 真实签名及owner接线请求

- 已有B `MaterialObservationBarrier.isReservedFor(Object root, UUID op, String hash)`仅证明内存reservation，不是durable PREPARE证明。世界能力mint必须另由B真实耐久授权验证；不以reserve后直接mint充数。
- 新 `public final WorldPrimitivePermit`，枚举 `READ_ROOT, REPLACE_ROOT, REPLACE_SECTION, REPLACE_BE_MAP, ENCODE_ROOT, WRITE_CHUNK`。构造器package-private，绑定 `OwnerKey.WorldBlock, UUID operation, String hash, Object originalRoot, Object target, Primitive`。公开 `consume(OwnerKey.WorldBlock, UUID, String, Object exactTarget, Primitive)` 一次性+当前thread+原reservation再验。当前没有production mint路径。
- content需要原BE root读/替换受控桥（显式permit）、observationRoots纳入真实chunk、loaded内部raw identity避免guard递归、persist与air迁移保持位置incarnation。当前未改content；编排已收到精确需求。原恢复分类任务的写权不足以完成此接线。
- B需要真实启动索引权威发布桥及验证过PREPARE的原root/op/hash能力桥。未使包私有coordinator public，不伪造Owner ready。

## 测试源码分层（全部未运行）

- AtomicRegionPublisherTest：文件夹夹具，inline→external→external→inline、每个fault probe点旧/新完整图、删除邻chunk、external损坏拒绝。这是抛异常故障模拟，不是kill进程、断电或真实世界。
- WorldBoundaryPrimitivesTest：保留map alias、旧编码、tag篡改、exact future失败、flush失败。
- WorldMaterialBoundaryGameTests：真实已加载LevelChunk/section/palette/map入口reservation拒读写；数组替换不改原section。用reservation fixture，不是B真实玩家鉴权交易，更不是空气↔材料完整往返。

## 必须继续完成

- 空气纳管同步身份持久、原BE唯一根迁移、无中间回调原子section/root/map发布与缓存延迟重建。
- 原save严格完整编码（含BE/attachment被吞异常），自动保存共同provenance入口、IO错误恢复和reconcile。
- 卸载pin+future publication、爆炸回调前延迟、tick/Load事件恢复gate、旧别名生命周期清理。
- matching RegionFile直接路径和存储协议版本兼容；当前region COW追加造成膨胀，需安全紧凑重建；故障后显式同op重读分类/恢复。
- Windows目录项force/replace语义及进程崩溃实测由后续真实运行提供。当前 Publication.directoryDurabilityConfirmed=false：文件force绝不冒充目录force或断电耐久。此缺口不能被persist成功包装。
- 真实B/content接线、剩余Mixin源码、精确JSON合并清单、真实空气往返/替换/爆炸/卸载/保存/各持久点/external入口测试。

未运行Gradle/javac/GameTest/client/截图；没有占固定2693或写classes/resources。没有更改JSON、B/content/API、水热文件、正式存档/mods或第三方jar；水机器仍关闭。所有源码未编译，现阶段不是Owner-ready或交付完成。
