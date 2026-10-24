from pathlib import Path
import zipfile,json,re,hashlib
root=Path(__file__).resolve().parents[2]; artifacts=root/'build/moddev/artifacts';out=root/'build/a-audit';out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(artifacts/'neoforge-21.1.264-sources.jar') as z:
 name=next(n for n in z.namelist() if n.endswith('net/minecraft/world/item/Items.java')); source=z.read(name).decode(); names=sorted(set(re.findall(r'(?:registerItem|registerBlock)\(\s*"([a-z0-9_]+)"',source)))
 (out/'Items-source.java.txt').write_text(source)
 (out/'vanilla-source-item-names.json').write_text(json.dumps(names,indent=2))
recipes={}
for jar in artifacts.glob('*.jar'):
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.startswith('data/minecraft/recipe/') and n.endswith('.json'):recipes[n]=json.loads(z.read(n))
rows=[]
for path,d in sorted(recipes.items()):
 rows.append(dict(id='minecraft:'+path.removeprefix('data/minecraft/recipe/').removesuffix('.json'),type=d.get('type'),sha256=hashlib.sha256(json.dumps(d,sort_keys=True).encode()).hexdigest(),recipe=d,status='pending_quantitative_rewrite',reason='Requires actual item composition, ingredient-tag expansion, result count and remainder accounting; not assumed conservative.'))
(out/'vanilla-recipe-audit.json').write_text(json.dumps(rows,indent=2))
print('source explicit item names',len(names),'recipe documents',len(rows));print('types',sorted(set(r['type'] for r in rows)))
