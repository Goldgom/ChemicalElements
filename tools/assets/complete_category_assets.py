from pathlib import Path
import json
r=Path(__file__).resolve().parents[2]/'src/main/resources'
labels={'elements':('Elements','元素'),'species':('Compounds and species','化合物与物种'),'materials':('Minerals and materials','矿物与材料'),'machines':('Machines','机器'),'parts':('Multiblock parts','多方块零件'),'containers':('Containers and tanks','容器与储罐'),'tools':('Tools and accessories','工具与饰品'),'reagents':('Reagents and solvents','试剂与溶剂'),'nuclides':('Nuclides and radioactivity','核素与放射性')}
for language,index in [('en_us',0),('zh_cn',1)]:
 p=r/'assets/chemicalelements/lang'/(language+'.json');j=json.loads(p.read_text(encoding='utf-8'));j.update({'itemGroup.chemicalelements.'+k:v[index] for k,v in labels.items()});p.write_text(json.dumps(j,ensure_ascii=False,indent=2),encoding='utf-8')
assets={'blockstates/example_block.json':{'variants':{'':{'model':'chemicalelements:block/example_block'}}},'models/block/example_block.json':{'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/stone'}},'models/item/example_block.json':{'parent':'chemicalelements:block/example_block'},'models/item/example_item.json':{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/apple'}}}
for name,data in assets.items():
 p=r/'assets/chemicalelements'/name
 if not p.exists():p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2),encoding='utf-8')
print('Nine category translations merged; missing registered example resources added only if absent.')
