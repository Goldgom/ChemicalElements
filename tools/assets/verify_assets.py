from pathlib import Path
import json,re,struct,zlib,subprocess,hashlib
root=Path(__file__).resolve().parents[2];assets=root/'src/main/resources/assets/chemicalelements';data=root/'src/main/resources/data/chemicalelements';fail=[];checks=0
def check(value,message):
 global checks
 checks+=1
 if not value:fail.append(message)
for p in assets.rglob('*.json'):
 try:j=json.loads(p.read_text(encoding='utf-8-sig'))
 except Exception as e:fail.append(str(p)+': '+str(e));continue
 for texture in j.get('textures',{}).values():
  if texture.startswith('chemicalelements:'):
   target=assets/'textures'/str(texture.split(':',1)[1]+'.png');check(target.exists(),str(target))
 for model in ([j['parent']] if 'parent' in j else []):
  if model.startswith('chemicalelements:'):check((assets/'models'/str(model.split(':',1)[1]+'.json')).exists(),model)
for p in assets.rglob('*.png'):
 b=p.read_bytes();check(b[:8]==b'\x89PNG\r\n\x1a\n',str(p));check(struct.unpack('>II',b[16:24])==(16,16),str(p)+' dimensions')
source=(root/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text();names=re.findall(r'"([a-z]+)"',source.split('NAMES=List.of(',1)[1].split(');',1)[0])
check(len(names)==118,'element count')
for name in names:check((assets/'models/item'/f'element_{name}.json').exists(),name)
for p in (data/'chemicalelements/multiblocks').glob('*.json'):
 j=json.loads(p.read_text());coords=[tuple(e['offset']) for e in j['parts']];check(len(coords)==len(set(coords)),p.name+' duplicate parts');check((0,0,0) not in coords,p.name+' controller duplicated')
for lang in ('en_us','zh_cn'):
 j=json.loads((assets/'lang'/f'{lang}.json').read_text(encoding='utf-8-sig'))
 for name in names:check('item.chemicalelements.element_'+name in j,lang+name)
git=subprocess.run(['git','diff','--cached','--name-status'],cwd=root,capture_output=True,text=True)
report={'checks':checks,'failures':fail,'staged':git.stdout,'files':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'src/main/java/org/goldgom/chemicalelements/machine').glob('*.java'))}}
out=root/'build/d-evidence';out.mkdir(parents=True,exist_ok=True);(out/'resource-test.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(f'{checks} checks; {len(fail)} failures; staged entries {len(git.stdout.splitlines())}');print('\n'.join(fail));raise SystemExit(bool(fail))
