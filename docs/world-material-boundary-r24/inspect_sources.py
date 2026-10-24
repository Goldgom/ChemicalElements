import zipfile,re,json,hashlib,pathlib
ROOT=pathlib.Path(r'E:/Projects/ChemicalElements')
OUT=ROOT/'docs/world-material-boundary-r24'
z=zipfile.ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.264-sources.jar')
targets={
'net/minecraft/world/level/Level.java':['getBlockState','getBlockEntity','setBlock','removeBlockEntity'],
'net/minecraft/world/level/chunk/LevelChunk.java':['setBlockState','removeBlockEntity','getBlockEntities','getBlockEntity','setBlockEntity','getBlockEntityNbtForSaving','clearAllBlockEntities'],
'net/minecraft/world/level/chunk/ChunkAccess.java':['getSections','getSection'],
'net/minecraft/world/level/chunk/LevelChunkSection.java':['setBlockState','getStates','getBlockState'],
'net/minecraft/world/level/chunk/storage/ChunkStorage.java':['write','flushWorker'],
'net/minecraft/world/level/chunk/storage/IOWorker.java':['store','synchronize','runStore','storePendingChunk'],
'net/minecraft/world/level/chunk/storage/RegionFileStorage.java':['write','flush'],
'net/minecraft/world/level/chunk/storage/RegionFile.java':['flush','write'],
'net/minecraft/world/level/chunk/storage/ChunkSerializer.java':['write','read'],
'net/minecraft/server/level/ChunkMap.java':['save','saveAllChunks','scheduleUnload'],
'net/minecraft/world/level/Explosion.java':['finalizeExplosion']}
result=[]; manifest=[]
for p,methods in targets.items():
 raw=z.read(p); lines=raw.decode().splitlines(); result.append('\nFILE '+p);manifest.append({'entry':p,'sha256':hashlib.sha256(raw).hexdigest()})
 for i,l in enumerate(lines):
  if any(re.search(r'\b'+m+r'\(',l) for m in methods) and re.search(r'(public|private|protected)',l):
   j=i;depth=0;begun=False
   while j<len(lines):
    depth+=lines[j].count('{')-lines[j].count('}');begun=begun or '{' in lines[j];result.append(str(j+1)+': '+lines[j]);j+=1
    if begun and depth==0:break
(OUT/'vanilla-paths.txt').write_text('\n'.join(result),encoding='utf-8')
(OUT/'source-entries.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print('targeted entries',len(manifest),'output lines',len(result))
