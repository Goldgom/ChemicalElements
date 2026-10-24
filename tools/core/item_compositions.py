from pathlib import Path
import json,re
from fractions import Fraction as F
from decimal import Decimal,localcontext
R=Path(__file__).resolve().parents[2]; base=R/'src/main/resources/data/chemicalelements/chemicalelements'; rows=json.loads((R/'run/a-audit/vanilla-items-runtime.json').read_text()); recipes=json.loads((R/'build/a-audit/vanilla-recipe-audit.json').read_text()); known={};why={}
def setc(n,c,reason):
 if n not in known:known[n]={k:F(v) for k,v in c.items() if F(v)};why[n]=reason
metals={'iron':'iron','gold':'gold','copper':'copper','diamond':'carbon','coal':'carbon','netherite':'tungsten','emerald':'beryllium','lapis':'silicon','redstone':'copper'}
for row in rows:
 n=row['id'].split(':')[1]
 if n=='air':setc(n,{},'Air item has no material; atmospheric gas is separate finite inventory.');continue
 if n in ['barrier','light','structure_void','command_block','repeating_command_block','chain_command_block','structure_block','jigsaw','debug_stick','knowledge_book'] or n.endswith('_spawn_egg'):
  setc(n,{'silica':1},'Explicit game convention: technical/spawn token is a silica carrier; spawned entity matter requires a separate authorized creation ledger, not chemical synthesis.');continue
 for name,species in metals.items():
  if n in [name,name+'_ingot',name+'_block',name+'_nugget','raw_'+name,'raw_'+name+'_block']:
   setc(n,{species:9 if n.endswith('_block') else F(1,9) if n.endswith('_nugget') else 1},'Game-calibrated elemental sample; ingot unit 1 mol, storage block 9 units, nugget 1/9 unit (quantized by explicit shared rounding).')
 if n in ['coal','charcoal','diamond']:setc(n,{'carbon':1},'Carbon sample approximation; one mol of carbon atoms per item.')
 if n.endswith('_ore'):
  for key,s in metals.items():
   if key in n:setc(n,{s:1,'silica':3},'Ore grade gameplay calibration: one mol target element plus three mol silicate gangue. No claim of universal real ore grade.')
  if 'quartz' in n:setc(n,{'silica':4},'Quartz-bearing rock approximation.')
 if n in ['quartz','amethyst_shard']:setc(n,{'silica':1},'Silica crystal approximation; amethyst trace color impurities omitted.')
 if re.search(r'(?:^|_)(?:stone|cobblestone|granite|diorite|andesite|deepslate|tuff|basalt|blackstone|sandstone|obsidian|end_stone|netherrack|prismarine|purpur|quartz)(?:_|$)',n) and row['class'] in ['BlockItem','StandingAndWallBlockItem']:
  setc(n,{'silica':F(1,2) if n.endswith('_slab') else F(3,4) if n.endswith('_stairs') else 1},'Silicate rock bulk approximation; fantasy lithologies explicitly game-convention silica analogue; shape volume calibrated slab=1/2 stairs=3/4.')
 if n in ['sand','suspicious_sand','gravel','suspicious_gravel','bedrock','flint']:setc(n,{'silica':1},'Silicate aggregate approximation; sediment composition naturally variable.')
 if n=='red_sand':setc(n,{'silica':1,'iron':F(1,100)},'Iron-bearing sand approximation; iron oxidation state not modeled in this component grade.')
 if n in ['dirt','grass_block','coarse_dirt','podzol','rooted_dirt','farmland','dirt_path','mycelium','soul_soil','soul_sand','crimson_nylium','warped_nylium','mud','packed_mud']:
  setc(n,{'silica':1,'cellulose':F(1,10),'water':F(1,5) if 'mud' in n else F(1,20)},'Soil bulk mixture game-calibrated silicate/organic matter/moisture, not real universal soil formula.')
 if 'copper' in n and n not in known:
  c={'copper':F(1,2) if n.endswith('_slab') else 1}
  if any(k in n for k in ['exposed','weathered','oxidized']):c['oxygen']=F(1,2)
  if n.startswith('waxed'):c['carbon']=F(1,10);c['hydrogen']=F(1,5)
  setc(n,c,'Copper object game-calibrated size; patina approximated oxygen component, wax hydrocarbon component. Oxidation/waxing must supply these inventories.')
 if re.search(r'(?:^|_)(?:oak|spruce|birch|jungle|acacia|cherry|mangrove|bamboo|crimson|warped)(?:_|$)',n) and any(n.endswith('_'+x) for x in ['planks','log','wood','stem','hyphae','block','slab','stairs','fence','fence_gate','door','trapdoor','button','pressure_plate','sign','hanging_sign','boat','chest_boat','raft','chest_raft']):
  amount=4 if any(n.endswith('_'+x) for x in ['log','wood','stem','hyphae']) else F(1,2) if n.endswith('_slab') else F(3,4) if n.endswith('_stairs') else 1
  setc(n,{'cellulose':amount},'Lignocellulosic material approximated as cellulose repeat units; fungal Nether wood uses explicit gameplay analogue; per-item volume calibration.')
 if n.endswith(('_leaves','_sapling','_roots','_seeds','_flower','_fungus','_mushroom','_berries')) or n in ['stick','paper','bamboo','wheat','sugar_cane','kelp','dried_kelp','fern','large_fern','short_grass','tall_grass','vine','weeping_vines','twisting_vines','moss_block','moss_carpet','lily_pad','seagrass','cactus','azalea','flowering_azalea','hanging_roots','mangrove_propagule','pink_petals','dandelion','poppy','blue_orchid','allium','azure_bluet','red_tulip','orange_tulip','white_tulip','pink_tulip','oxeye_daisy','cornflower','lily_of_the_valley','wither_rose','torchflower','pitcher_plant','spore_blossom','dead_bush','sunflower','lilac','rose_bush','peony','nether_sprouts','big_dripleaf','small_dripleaf','glow_lichen','nether_wart','torchflower_seeds','pitcher_pod']:
  setc(n,{'cellulose':F(1,4) if n in ['stick','paper'] else F(1,10),'water':F(1,10) if n not in ['stick','paper','dried_kelp'] else 0},'Plant bulk cellulose/water approximation; organics and micronutrients not claimed fully modeled.')
 if 'glass' in n:setc(n,{'silica':F(3,8) if n.endswith('_pane') else 1},'Silica glass approximation; color additives must be tracked separately by actual input ledger.')
 if 'terracotta' in n or n in ['clay','clay_ball','brick','bricks','decorated_pot','flower_pot'] or n.endswith('_pottery_sherd'):
  setc(n,{'silicon':1,'aluminium':1,'oxygen':3,'hydrogen':F(1,2) if n in ['clay','clay_ball'] else 0},'Clay/ceramic aluminosilicate bulk approximation; moisture removal requires water byproduct.')
 if 'concrete' in n:setc(n,{'silica':1,'calcium':F(1,2),'oxygen':F(1,2),'water':F(1,10) if not n.endswith('_powder') else 0},'Concrete calcium-silicate/aggregate approximation with bound water for cured form.')
 if n in ['calcite','dripstone_block','pointed_dripstone'] or 'coral' in n or n in ['nautilus_shell','egg','turtle_egg','sniffer_egg']:
  setc(n,{'calcium':1,'carbon':1,'oxygen':3},'Calcium carbonate biomineral/mineral approximation; organic egg contents require instance-specific additions.')
 if n in ['snow','snow_block','snowball','ice','packed_ice','blue_ice']:setc(n,{'water':F(1,4) if n=='snowball' else 1},'Water material convention; water species is liquid reference and phase transition solver must account for frozen state.')
 if n.endswith(('_wool','_carpet','_banner')) or n in ['string','leather','rabbit_hide','feather','phantom_membrane','turtle_scute','armadillo_scute','shulker_shell','goat_horn']:
  setc(n,{'carbon':1,'hydrogen':2,'nitrogen':F(1,3),'oxygen':F(1,3),'sulfur':F(1,50)},'Protein/fiber bulk elemental approximation; fantasy membranes/shells explicitly use game-convention protein analogue.')
 if n in ['bone','bone_meal','bone_block']:setc(n,{'calcium':5,'phosphorus':3,'oxygen':13,'hydrogen':1},'Hydroxyapatite formula-unit inventory approximation; collagen omitted.')
 if n in ['apple','carrot','potato','baked_potato','poisonous_potato','beetroot','melon_slice','pumpkin','melon','chorus_fruit','popped_chorus_fruit','sweet_berries','glow_berries','bread','cookie','sugar','honey_bottle','honey_block','cocoa_beans']:
  setc(n,{'carbon':6,'hydrogen':12,'oxygen':6,'water':F(1,2)},'Food carbohydrate/water bulk game calibration; not nutritional/biochemical complete model.')
 if n in ['porkchop','cooked_porkchop','beef','cooked_beef','chicken','cooked_chicken','mutton','cooked_mutton','rabbit','cooked_rabbit','rotten_flesh','cod','cooked_cod','salmon','cooked_salmon','tropical_fish','pufferfish','spider_eye','rabbit_foot']:
  setc(n,{'carbon':3,'hydrogen':7,'nitrogen':1,'oxygen':2,'water':1},'Protein-rich tissue approximate amino-acid elemental inventory; cooking is material-preserving here unless explicit loss products are emitted.')
 if row['class']=='DyeItem':
  palette={'white':'calcium','orange':'iron','magenta':'manganese','light_blue':'copper','yellow':'iron','lime':'chromium','pink':'manganese','gray':'carbon','light_gray':'carbon','cyan':'copper','purple':'manganese','blue':'cobalt','brown':'iron','green':'chromium','red':'iron','black':'carbon'}
  setc(n,{palette[n.removesuffix('_dye')]:F(1,100),'oxygen':F(1,100)},'Explicit pigment element game convention, not assertion that vanilla plant dyes are these inorganic pigments.')
# Explicit additional material families, not an unknown-item fallback.
for row in rows:
 n=row['id'].split(':')[1]
 if n in ['iron_ingot','gold_ingot','copper_ingot']:setc(n,{n.split('_')[0]:1},'One mol metallic ingredient gameplay unit.')
 if n in ['netherite_scrap','ancient_debris','heavy_core']:setc(n,{'tungsten':1,'iron':1},'Explicit fictional refractory alloy convention; not a real netherite formula.')
 if n in ['glowstone_dust','glowstone','blaze_rod','blaze_powder','breeze_rod','wind_charge','nether_star','echo_shard','ender_pearl','heart_of_the_sea','ghast_tear','prismarine_shard','prismarine_crystals']:
  materials={'glowstone_dust':{'calcium':1,'sulfur':1},'glowstone':{'calcium':4,'sulfur':4},'blaze_rod':{'magnesium':2},'blaze_powder':{'magnesium':1},'breeze_rod':{'silicon':1,'oxygen':2},'wind_charge':{'nitrogen':1},'nether_star':{'zirconium':1},'echo_shard':{'silica':1,'copper':F(1,10)},'ender_pearl':{'silica':1,'beryllium':F(1,10)},'heart_of_the_sea':{'copper':1,'silica':1},'ghast_tear':{'water':1,'silica':F(1,10)},'prismarine_shard':{'silica':1,'copper':F(1,10)},'prismarine_crystals':{'silica':1,'calcium':F(1,10)}}
  setc(n,materials[n],'Explicit named fantasy-material game convention; magical behavior is not a real chemical property.')
 if n in ['sponge','wet_sponge','slime_ball','slime_block','cobweb','elytra','frogspawn'] or n.startswith('sculk'):
  setc(n,{'carbon':1,'hydrogen':2,'nitrogen':F(1,10),'oxygen':1,'water':10 if n=='wet_sponge' else 1},'Explicit porous/protein-polysaccharide material approximation; absorbed water is finite.')
 if n in ['gunpowder']:setc(n,{'potassium':2,'nitrogen':2,'oxygen':6,'carbon':3,'sulfur':1},'Black powder component inventory approximation; reaction safety and conditions belong to modeled engine.')
 if n in ['honeycomb','honeycomb_block'] or n.endswith('_candle') or n=='candle':setc(n,{'carbon':25,'hydrogen':52,'oxygen':2},'Wax hydrocarbon/ester bulk approximation, gameplay amount one reference unit.')
 if 'amethyst' in n:setc(n,{'silica':1},'Amethyst silica approximation, trace coloring omitted.')
 if n in ['nether_brick','nether_bricks','cracked_nether_bricks','chiseled_nether_bricks'] or 'nether_brick_' in n:setc(n,{'silica':1,'iron':F(1,10)},'Explicit fantasy fired ceramic analogue.')
 if n.endswith('_smithing_template'):setc(n,{'carbon':1,'silica':1,'gold':F(1,10)},'Explicit patterned stone/diamond reference template material, copying must fund every output.')
 if n.startswith('music_disc_') or n=='disc_fragment_5':setc(n,{'carbon':2,'hydrogen':3,'chlorine':1},'Vinyl reference material gameplay convention; historical disc material not claimed.')
 if n.endswith(('_skull','_head')):setc(n,{'calcium':5,'phosphorus':3,'oxygen':13,'hydrogen':1},'Head trophy hydroxyapatite approximation; organic contents simplified.')
 if n in ['ink_sac','glow_ink_sac']:setc(n,{'carbon':8,'hydrogen':7,'nitrogen':1,'oxygen':2},'Melanin-like pigment elemental approximation; bioluminescence not implied.')
 if n in ['potion','splash_potion','lingering_potion','experience_bottle','ominous_bottle','dragon_breath']:setc(n,{'silica':1,'water':1},'Explicit bottle plus aqueous carrier convention; contents/components require instance additions, effects are not chemical atoms.')
 if n.endswith('_bucket') or n=='bucket':
  c={'iron':3}
  if n!='bucket':c['water']=F(1000,18) if n!='lava_bucket' else 0
  if n=='lava_bucket':c['silica']=10
  if n=='milk_bucket':c.update({'carbon':1,'hydrogen':2,'oxygen':1})
  if row['class']=='MobBucketItem':c.update({'carbon':3,'nitrogen':1})
  setc(n,c,'Explicit iron container plus finite contents calibration; returned container participates in same transaction.')
# Expand actual vanilla item tags; differing alternatives are NOT silently averaged.
import zipfile
tags={}
for jar in (R/'build/moddev/artifacts').glob('*.jar'):
 with zipfile.ZipFile(jar) as z:
  for p in z.namelist():
   match=re.fullmatch(r'data/([^/]+)/tags/item/(.+)[.]json',p)
   if match:tags[match[1]+':'+match[2]]=json.loads(z.read(p)).get('values',[])
def tag_items(t,seen=frozenset()):
 if t in seen:return []
 out=[]
 for v in tags.get(t,[]):
  v=v.get('id') if isinstance(v,dict) else v
  out.extend(tag_items(v[1:],seen|{t}) if v.startswith('#') else [v])
 return out
# Derive crafted composite objects only when each ingredient branch has the SAME known composition.
# This does not declare chemistry feasibility, consume time, or authorize a recipe.
def ingredient(x):
 if isinstance(x,list):
  cs=[ingredient(v) for v in x];return cs[0] if cs and all(v==cs[0] for v in cs) else None
 if 'item' in x:return known.get(x['item'].removeprefix('minecraft:'))
 if 'tag' in x:
  cs=[known.get(v.removeprefix('minecraft:')) for v in tag_items(x['tag'])]
  return cs[0] if cs and cs[0] is not None and all(c==cs[0] for c in cs) else None
 return None
for step in range(40):
 changed=0
 for r in recipes:
  d=r['recipe']; result=d.get('result');rid=result if isinstance(result,str) else result.get('id') if isinstance(result,dict) else None
  if not rid or not rid.startswith('minecraft:'):continue
  n=rid.split(':')[1]
  if n in known:continue
  typ=d.get('type');parts=[]
  if typ=='minecraft:crafting_shaped':parts=[d['key'][c] for line in d['pattern'] for c in line if c!=' ']
  elif typ=='minecraft:crafting_shapeless':parts=d['ingredients']
  else:continue
  cs=[ingredient(x) for x in parts]
  if not cs or any(c is None for c in cs):continue
  # Container-return recipes cannot use simple summed-input derivation.
  if any('bucket' in str(x) or 'bottle' in str(x) for x in parts):continue
  count=result.get('count',1) if isinstance(result,dict) else 1;total={}
  for c in cs:
   for k,v in c.items():total[k]=total.get(k,F(0))+v/count
  setc(n,total,'Derived composite inventory from explicit ingredient items of '+r['id']+'; not a proof of reaction feasibility.');changed+=1
 if not changed:break
# Explicit residual identities and named composite conventions, reviewed against runtime list.
for row in rows:
 n=row['id'].split(':')[1]
 if n in known:continue
 parent=None;extra={}
 if n in ['chipped_anvil','damaged_anvil']:parent='anvil'
 elif n.startswith('netherite_') and n.rsplit('_',1)[1] in ['sword','shovel','pickaxe','axe','hoe','helmet','chestplate','leggings','boots']:parent='diamond_'+n.split('_',1)[1];extra=known['netherite_ingot']
 elif n.startswith('chainmail_'):parent='iron_'+n.split('_',1)[1]
 elif n.endswith('_horse_armor'):parent={'iron':'iron_chestplate','golden':'golden_chestplate','diamond':'diamond_chestplate'}[n.split('_')[0]]
 elif n in ['filled_map','written_book','enchanted_book','enchanted_golden_apple','tipped_arrow']:parent={'filled_map':'map','written_book':'writable_book','enchanted_book':'book','enchanted_golden_apple':'golden_apple','tipped_arrow':'arrow'}[n]
 elif n.endswith('_banner_pattern'):parent='paper'
 elif n in ['chest_minecart','tnt_minecart','hopper_minecart','command_block_minecart']:parent='minecart'; extra={'iron':5,'cellulose':8} if n=='hopper_minecart' else {'cellulose':8} if n=='chest_minecart' else {'silica':1} if n=='command_block_minecart' else known['gunpowder']
 if parent and parent in known:
  c=dict(known[parent]);
  for k,v in extra.items():c[k]=c.get(k,F(0))+v
  setc(n,c,'Explicit inheritance from '+parent+' plus stated additions; enchantment/map content is information, not new matter. Damage retains material until an explicit abrasion transaction.');continue
 groups=[
 (['sea_pickle','chorus_plant','brown_mushroom_block','red_mushroom_block','mushroom_stem','carved_pumpkin','jack_o_lantern','shroomlight','bee_nest'],{'cellulose':1,'water':1},'Biological/fungal bulk carrier; light emission separately modeled.'),
 (['spawner','trial_spawner','vault'],{'iron':8,'silica':2,'copper':1},'Technical cage/control housing game convention; spawning is not free chemical conversion.'),
 (['end_portal_frame','dragon_egg'],{'silica':4,'beryllium':1},'Named End material fictional silicate/beryllium convention; no physical portal chemistry claimed.'),
 (['shulker_box']+[c+'_shulker_box' for c in ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']],{'cellulose':8,'carbon':2,'hydrogen':4,'nitrogen':F(2,3),'oxygen':F(2,3),'sulfur':F(1,25)},'Chest plus two protein-shell carriers; contents are independent inventories, not folded into casing template.'),
 (['hopper'],{'iron':5,'cellulose':8},'Five ingot hopper plus chest casing.'),
 (['trapped_chest'],{'cellulose':8,'iron':1},'Wood chest and iron trigger material calibration.'),
 (['tnt'],{'potassium':10,'nitrogen':10,'oxygen':30,'carbon':15,'sulfur':5,'silica':4},'Five powder reference units plus four sand reference units; detonation must transfer all products.'),
 (['saddle','bundle'],{'carbon':5,'hydrogen':10,'nitrogen':1,'oxygen':2,'iron':F(1,10)},'Leather/fiber approximation with fittings.'),
 (['cake','suspicious_stew'],{'carbon':12,'hydrogen':24,'oxygen':12,'nitrogen':1,'water':3},'Explicit mixed-food reference inventory; recipe containers remain separate returned inventories.'),
 (['firework_star'],{'potassium':2,'nitrogen':2,'oxygen':6,'carbon':3,'sulfur':1},'Powder carrier reference; customized pigments/extra charges retained in actual instance.'),
 (['name_tag'],{'cellulose':F(1,4),'iron':F(1,100)},'Paper label with metal attachment.'),
 (['totem_of_undying'],{'gold':4,'beryllium':F(1,10)},'Named fictional gold/emerald carrier; resurrection not modeled as chemical reaction.'),
 (['trident'],{'copper':3,'tin':1},'Explicit bronze analogue for fictional marine weapon.'),
 (['bell'],{'copper':3,'tin':1},'Bell bronze alloy gameplay unit.'),
 (['ochre_froglight','verdant_froglight','pearlescent_froglight'],{'calcium':1,'phosphorus':1,'oxygen':4,'carbon':1},'Named fictional bioluminescent mineral carrier.'),
 (['trial_key','ominous_trial_key'],{'iron':1,'copper':F(1,10)},'Metal key reference; ominous property informational.')]
 for members,c,note in groups:
  if n in members:setc(n,c,note)
# Fixed point conversion is deterministic; values not representable exactly are explicitly labeled.
quant=10**18
output=base/'item_compositions';output.mkdir(parents=True,exist_ok=True)
for n,c in known.items():
 amounts={};rounded=False
 for k,v in c.items():
  scaled=v*quant;q=scaled.numerator//scaled.denominator;rounded|=scaled.denominator!=1
  if q:amounts['chemicalelements:'+k]=str(q//quant)+'.'+str(q%quant).zfill(18)
 d=dict(schema_version=1,match={'items':['minecraft:'+n]},priority=100,per_instance=amounts,basis='game_convention',notes=why[n]+(' Quantized downward at 10^-18 mol; all actual recipe transactions must carry deterministic residuals, not regenerate this template.' if rounded else ''),source='ChemicalElements explicit material calibration v1; bundled species sources describe chemical formula approximations.')
 (output/(n+'.json')).write_text(json.dumps(d,indent=2)+'\n')
missing=[r['id'] for r in rows if r['id'].split(':')[1] not in known]
(R/'build/a-audit/composition-generation.json').write_text(json.dumps({'registered':len(rows),'mapped':len(known),'missing':missing,'policy':'No fallback for unknown items. All mappings declare game calibration.'},indent=2))
print('mapped',len(known),'of',len(rows),'missing',len(missing));print(' '.join(missing))
