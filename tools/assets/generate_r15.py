from pathlib import Path
import json
r=Path(__file__).resolve().parents[2]/'src/main/resources'
def put(p,data):
 p=r/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
parts={'port':[1,1,1],'column_short':[1,2,1],'column_tall':[1,3,1],'core':[2,2,2],'assembly':[3,3,3],'tower':[3,6,3]}
# Reuse an existing item pixel texture as a valid block atlas sprite, no regeneration of old assets.
for name,size in parts.items():
 put(Path('data/chemicalelements/chemicalelements/parts')/(name+'.json'),{'schema_version':1,'size':size,'category':'interface' if name=='port' else 'structural'})
 put(Path('assets/chemicalelements/blockstates')/('part_'+name+'.json'),{'variants':{'':{'model':'chemicalelements:block/part_'+name}}})
 put(Path('assets/chemicalelements/models/block')/('part_'+name+'.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'chemicalelements:item/element_iron'}})
 put(Path('assets/chemicalelements/models/item')/('part_'+name+'.json'),{'parent':'chemicalelements:block/part_'+name})
tiers={'basic':(1,3,8,False,False,False,False,False,['chemicalelements:hydrogen','chemicalelements:carbon','chemicalelements:oxygen']),'advanced':(2,8,35,True,True,True,True,False,[]),'research':(3,18,40,True,True,True,True,True,[])}
fields=['priority','precision','max_inventory_slot','instances','liquid','gas','temperature','nuclides','elements']
for tier,values in tiers.items():
 put(Path('data/chemicalelements/chemicalelements/detectors')/(tier+'.json'),dict(schema_version=1,**dict(zip(fields,values))))
 put(Path('assets/chemicalelements/models/item')/('detector_'+tier+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'chemicalelements:item/element_silicon'}})
put(Path('data/curios/tags/item/charm.json'),{'replace':False,'values':['chemicalelements:detector_'+t for t in tiers]})
put(Path('data/curios/slots/charm.json'),{'size':1,'operation':'ADD'})
put(Path('data/curios/entities/chemicalelements_player.json'),{'entities':['minecraft:player'],'slots':['charm']})
kinds=['analyzer','sampler','grinder','dissolver','filter','centrifuge','evaporator','crystallizer','distiller','reactor','electrolyzer','heat_exchanger','condenser','gas_collector','storage_tank','pump','pipe','dryer','vacuum_chamber','high_temperature_furnace','fractionation_tower','research_controller','isotope_separator','shielded_reactor','ambient_workbench']
for kind in kinds:
 p=Path('data/chemicalelements/chemicalelements/multiblocks')/(kind+'.json')
 if not (r/p).exists():put(p,{'schema_version':1,'parts':[{'offset':[0,0,0],'block':'chemicalelements:'+kind},{'offset':[1,0,0],'block':'chemicalelements:part_port'},{'offset':[-1,0,0],'block':'chemicalelements:part_column_short'}]})
for language in ['en_us','zh_cn']:
 p=Path('assets/chemicalelements/lang')/(language+'.json');j=json.loads((r/p).read_text(encoding='utf-8'))
 j.update({'item.chemicalelements.detector_'+t:('Element detector '+t if language=='en_us' else '元素探测仪 '+t) for t in tiers})
 j.update({'block.chemicalelements.part_'+n:('Multiblock part '+n if language=='en_us' else '多方块零件 '+n) for n in parts});put(p,j)
assert len(list((r/'data/chemicalelements/chemicalelements/multiblocks').glob('*.json')))==25
assert (r/'assets/chemicalelements/textures/item/element_iron.png').exists()
print('Incremental resources saved: six sized parts, three detectors, 18 missing structures. Seven existing structures preserved.')
