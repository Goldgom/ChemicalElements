from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).resolve().parents[2]
out=root/'build/d-evidence/r17-jei-sync';out.mkdir(parents=True,exist_ok=True)
args=[line.strip().strip('"') for line in (root/'build/d-evidence/r17-session/javac.args').read_text().splitlines()]
cp=str(root/'build/d-evidence/classes')+';'+args[args.index('-classpath')+1]+';'+str(root/'build/compat-api-evidence/jei-1.21.1-common-api-19.69.0.457.jar')+';'+str(root/'build/compat-api-evidence/jei-1.21.1-neoforge-api-19.69.0.457.jar')
sources=['compat/ReactionDisplay.java','compat/RecipeViews.java','compat/jei/client/ChemistryJeiPlugin.java','compat/jei/client/ReactionCategory.java','network/ReactionCatalogSync.java','network/ChemistryNetwork.java','network/ReactionCatalogGameTests.java']
paths=[root/'src/main/java/org/goldgom/chemicalelements'/s for s in sources]+[root/'src/test/java/org/goldgom/chemicalelements/gametest/machine/ReactionViewSyncTest.java']
flags=['-proc:none','--release','21','-sourcepath',str(out/'empty'),'-d',str(out/'classes'),'-classpath',cp]+[str(p) for p in paths]
af=out/'javac.args';af.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in flags))
r=subprocess.run([r'D:\Java\jdk-21\bin\javac.exe','@'+str(af)],cwd=root,capture_output=True,text=True,timeout=300)
(out/'javac.log').write_text(r.stdout+r.stderr);print(r.stdout+r.stderr);print('javac',r.returncode)
result={'javac':r.returncode,'sha256':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
if r.returncode==0:
 flags=['-cp',str(out/'classes')+';'+cp,'org.goldgom.chemicalelements.gametest.machine.ReactionViewSyncTest'];af=out/'java.args';af.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in flags))
 try:
  t=subprocess.run([r'D:\Java\jdk-21\bin\java.exe','@'+str(af)],cwd=root,capture_output=True,text=True,timeout=30);(out/'test.log').write_text(t.stdout+t.stderr);result['test']=t.returncode;print(t.stdout+t.stderr)
 except subprocess.TimeoutExpired:result['test']='TIMEOUT';print('test TIMEOUT')
(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
