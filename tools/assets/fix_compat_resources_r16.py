from pathlib import Path
import json,hashlib
R=Path(__file__).resolve().parents[2]
changes=[]
for lang,labels in {'en_us':{'config.jade.plugin_chemicalelements.machine_status':'Machine status','config.jade.plugin_chemicalelements.machine_data':'Machine data'},'zh_cn':{'config.jade.plugin_chemicalelements.machine_status':'机器状态','config.jade.plugin_chemicalelements.machine_data':'机器数据'}}.items():
 p=R/f'src/main/resources/assets/chemicalelements/lang/{lang}.json';d=json.loads(p.read_text(encoding='utf-8'));d.update(labels);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');changes.append(p)
for kind,name in [('entities','chemicalelements_player'),('slots','charm')]:
 src=R/f'src/main/resources/data/curios/{kind}/{name}.json'
 dst=R/f'src/main/resources/data/chemicalelements/curios/{kind}/{name}.json'
 assert not dst.exists() or dst.read_bytes()==src.read_bytes(),'Preserve unknown resource'
 dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(src.read_bytes());changes.append(dst)
for manifest in ['r16-art-manifest.json','manifest.json']:
 p=R/'tools/assets'/manifest;d=json.loads(p.read_text())
 for f in changes:
  rel=f.relative_to(R).as_posix()
  if rel in d or manifest=='r16-art-manifest.json':d[rel]=hashlib.sha256(f.read_bytes()).hexdigest()
 p.write_text(json.dumps(d,indent=2)+'\n')
print('Patched',*[str(p.relative_to(R)) for p in changes],sep='\n')
