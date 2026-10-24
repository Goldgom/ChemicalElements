from pathlib import Path
import zipfile,json
root=Path(__file__).resolve().parents[2];out=root/'build/d-evidence/compat'
jar=next((Path.home()/'.gradle/caches/modules-2/files-2.1/net.neoforged.fancymodloader/loader/4.0.45').rglob('*sources.jar'))
z=zipfile.ZipFile(jar)
for name in z.namelist():
 if name.endswith('.java'):
  text=z.read(name).decode()
  if any(k in text for k in ['class VersionSupportMatrix','testVersionSupportMatrix','class MavenVersionAdapter']):
   (out/(name.rsplit('/',1)[-1])).write_text(text)
   print(name)
for p in out.glob('*project.json'):
 try:
  j=json.loads(p.read_text());print(p.name,j.get('slug'),j.get('title'),j.get('game_versions'),j.get('loaders'))
 except:pass
for p in out.glob('*1.21.1-neoforge.json'):
 j=json.loads(p.read_text());print(p.name,'releases',len(j))
for p in out.glob('*.error.txt'):print(p.name,p.read_text())
