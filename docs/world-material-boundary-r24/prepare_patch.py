import pathlib,hashlib,json,subprocess,zipfile
R=pathlib.Path(r'E:/Projects/ChemicalElements'); O=R/'docs/world-material-boundary-r24'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
patch=[]
for p in sorted((O/'candidate').rglob('*.java')):
 rel=p.relative_to(O/'candidate').as_posix();lines=p.read_text().splitlines()
 patch += [f'diff --git a/{rel} b/{rel}','new file mode 100644','--- /dev/null',f'+++ b/{rel}',f'@@ -0,0 +1,{len(lines)} @@']+['+'+l for l in lines]
(O/'candidate.patch').write_text('\n'.join(patch)+'\n',encoding='utf-8')
z=zipfile.ZipFile(R/'build/moddev/artifacts/neoforge-21.1.264-sources.jar')
ps={'net/minecraft/world/level/chunk/storage/RegionFile.java':(288,405),'net/minecraft/world/level/chunk/PalettedContainer.java':(105,300),'net/minecraft/server/level/ServerChunkCache.java':(45,58)}
(O/'supplemental-paths.txt').write_text('\n'.join('\nFILE '+p+'\n'+'\n'.join(str(i+1)+': '+l for i,l in enumerate(z.read(p).decode().splitlines()) if a<=i+1<=b) for p,(a,b) in ps.items()),encoding='utf-8')
check=subprocess.run(['git','apply','--check',str(O/'candidate.patch')],cwd=R,capture_output=True,text=True)
index=sha(R/'.git/index'); staged=subprocess.run(['git','diff','--cached','--name-only'],cwd=R,capture_output=True,text=True)
inputs=list((R/'src/main/java/org/goldgom/chemicalelements/instance/materialtx').glob('*.java'))+list((R/'src/main/java/org/goldgom/chemicalelements/content/material').glob('*.java'))+list((R/'src/main/java/org/goldgom/chemicalelements/content/material/tx').glob('*.java'))+[R/'src/main/resources/chemicalelements.mixins.json',R/'src/main/java/org/goldgom/chemicalelements/persistence/SinkLedger.java']
m={'version':'world-material-boundary-r24-preparation-v1','status':'NOT_IMPLEMENTED_NOT_COMPILED_NOT_RUN','readTimeSemantics':'hashes at manifest creation; content owner not frozen','inputs':[{'path':p.relative_to(R).as_posix(),'sha256':sha(p)} for p in inputs],'artifacts':[{'path':p.relative_to(R).as_posix(),'sha256':sha(p)} for p in O.rglob('*') if p.is_file() and p.name!='manifest.json'],'gitApplyCheck':{'exit':check.returncode,'stdout':check.stdout,'stderr':check.stderr},'indexBefore':'82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69','indexAfter':index,'stagedPaths':staged.stdout.splitlines(),'indexLock':(R/'.git/index.lock').exists(),'remaining':['world guards and projection','air metadata lifecycle implementation','detached serializer and stale save fence','region/external chunk crash-safe write','B authorized entry and startup pre-isolate recovery','real entry tests']}
(O/'manifest.json').write_text(json.dumps(m,indent=2),encoding='utf-8');print(json.dumps({'gitApplyCheck':m['gitApplyCheck'],'indexMatch':m['indexBefore']==index,'stagedCount':len(m['stagedPaths']),'indexLock':m['indexLock'],'artifactCount':len(m['artifacts'])}))
