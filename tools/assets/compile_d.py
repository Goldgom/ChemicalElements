from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/d-evidence';out.mkdir(parents=True,exist_ok=True)
cache=Path.home()/'.gradle/caches'
jars=list((cache/'modules-2/files-2.1').rglob('*.jar'))
jars=[p for p in jars if not any(k in p.name for k in ['-sources','-javadoc'])]
# Minecraft codec bootstrap requires modern Guava; cache contains old build-tool copies.
guava=[p for p in jars if p.name.startswith('guava-')]
if guava:
 newest=max(guava,key=lambda p:tuple(int(v) for v in p.name.split('-')[1].split('.') if v.isdigit()))
 jars=[newest]+[p for p in jars if p not in guava]
jars+=list((cache/'neoformruntime/intermediate_results').glob('sourcesAndCompiledWithNeoForge*_output.jar'))
jars+=list((root/'build/compat-api-evidence').glob('*19.69.0.457.jar'))+[root/'build/compat-api-evidence/Jade-1.21.1-NeoForge-15.10.6.jar']
jars+=list((root/'build/compat-api-evidence').glob('curios-neoforge-9.5.1+1.21.1.jar'))
base=root/'src/main/java/org/goldgom/chemicalelements'
sources=[]
for package in ['machine','client','network','compat','api']:
 sources+=list((base/package).rglob('*.java'))
sources+=[base/'registry/ElementItems.java',base/'registry/CreativeCategories.java']
sources+=list((root/'src/test/java/org/goldgom/chemicalelements/gametest/machine').glob('*.java'))
# Use A's actual compiled catalog implementation; never synthesize a test substitute.
compile_dependencies=[root/'build/classes/java/main']+jars
args=['-proc:none','--release','21','-d',str(out/'classes'),'-classpath',';'.join(str(p) for p in compile_dependencies)]+[str(p) for p in sources]
argfile=out/'javac.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
result=subprocess.run([r'D:\Java\jdk-21\bin\javac.exe','@'+str(argfile)],cwd=root,capture_output=True,text=True)
(out/'javac.log').write_text(result.stdout+result.stderr,encoding='utf-8');print(result.stdout+result.stderr);print('exit',result.returncode)
if result.returncode:raise SystemExit(result.returncode)
classpath=str(out/'classes')+';'+';'.join(str(p) for p in compile_dependencies)
for name in ['QueryPolicyTest','MachineStateTest','DetectorPartTest','DisplayCodecTest','TransportTopologyTest','MaterialStateBalanceTest']:
 runargs=out/'java.args'
 runargs.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in ['-cp',classpath,'org.goldgom.chemicalelements.gametest.machine.'+name]),encoding='utf-8')
 test=subprocess.run([r'D:\Java\jdk-21\bin\java.exe','@'+str(runargs)],cwd=root,capture_output=True,text=True)
 (out/(name+'.log')).write_text(test.stdout+test.stderr,encoding='utf-8');print(test.stdout+test.stderr)
 if test.returncode:raise SystemExit(test.returncode)
