"""Explicitly applied after A's frozen client capture. Original layered assembly layouts.
Only seven known team-generated iron-shell multiblocks are migrated; all data backed up.
"""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[2];D=R/'src/main/resources/data/chemicalelements/chemicalelements/multiblocks';E=R/'build/d-evidence'
SIZES={'port':(1,1,1),'column_short':(1,2,1),'column_tall':(1,3,1),'core':(2,2,2),'assembly':(3,3,3),'tower':(3,6,3)}
# A controller stays accessible at origin. Bays occupy separate nonoverlapping volumes.
LAYOUTS={
 'fractionation_tower':[('tower',(2,0,1)),('column_tall',(-2,0,1)),('core',(-4,0,4)),('port',(1,0,0))],
 'electrolyzer':[('core',(-3,0,1)),('core',(2,0,1)),('column_short',(-1,0,4)),('port',(1,0,0))],
 'high_temperature_furnace':[('assembly',(2,0,1)),('column_tall',(-2,0,1)),('column_tall',(-2,0,3)),('port',(1,0,0))],
 'reactor':[('assembly',(2,0,1)),('core',(-3,0,1)),('column_short',(-1,0,4)),('port',(1,0,0))],
 'storage_tank':[('tower',(2,0,1)),('column_short',(-2,0,1)),('port',(1,0,0))],
 'research_controller':[('assembly',(2,0,1)),('core',(-3,0,1)),('column_tall',(-1,0,4)),('port',(1,0,0))],
 'shielded_reactor':[('assembly',(2,0,1)),('assembly',(-4,0,1)),('tower',(2,0,5)),('core',(-3,0,5)),('port',(1,0,0))]}
def build(name,bays):
 occupied={(0,0,0)};parts=[{'offset':[0,0,0],'block':'chemicalelements:'+name}];anchors=[]
 for kind,origin in bays:
  sx,sy,sz=SIZES[kind];cells={(origin[0]+x,origin[1]+y,origin[2]+z) for x in range(sx) for y in range(sy) for z in range(sz)}
  assert not occupied&cells,(name,kind,'overlap');occupied|=cells
  parts.append({'offset':list(origin),'block':'chemicalelements:part_'+kind,'facing':'north'})
  anchors.append({'kind':kind,'origin':list(origin),'size':[sx,sy,sz]})
 return {'schema_version':1,'controller':'chemicalelements:'+name,'parts':parts,'ports':[{'offset':[1,0,0],'role':'material'}],'unloaded':'pause','visual_assembly':{'style':'industrial chemistry r16','bays':anchors}},occupied
if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args();report={}
 for name,bays in LAYOUTS.items():
  data,cells=build(name,bays);p=D/(name+'.json');old=json.loads(p.read_text());blocks={v['block'] for v in old['parts']}
  assert blocks<={'minecraft:iron_block','chemicalelements:'+name} or old.get('visual_assembly',{}).get('style')=='industrial chemistry r16',f'Unknown modified definition {name}; preserve'
  report[name]={'cells':len(cells),'anchors':len(data['parts']),'bounds':[[min(c[i] for c in cells),max(c[i] for c in cells)] for i in range(3)],'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
  if args.apply:
   backup=E/'r16-before-structure'/p.name
   if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
   p.write_text(json.dumps(data,indent=2)+'\n')
 E.mkdir(exist_ok=True,parents=True);(E/'r16-proposed-structures.json').write_text(json.dumps({'applied':args.apply,'layouts':report},indent=2));print(json.dumps({'applied':args.apply,'layouts':report},indent=2))
