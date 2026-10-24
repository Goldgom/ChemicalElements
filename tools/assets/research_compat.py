from pathlib import Path
import urllib.request,json,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/d-evidence/compat';OUT.mkdir(parents=True,exist_ok=True)
def fetch(url,name):
    req=urllib.request.Request(url,headers={'User-Agent':'ChemicalElements-compat-verification/1.0'})
    try:
        data=urllib.request.urlopen(req,timeout=40).read();(OUT/name).write_bytes(data);return data
    except Exception as e:
        (OUT/(name+'.error.txt')).write_text(str(e));return b''
# Primary project API: supported game_versions/loaders are authoritative project metadata.
for project in ['notenoughitems','nei','jei','emi','roughly-enough-items']:
    fetch('https://api.modrinth.com/v2/project/'+project,project+'-project.json')
    fetch('https://api.modrinth.com/v2/project/'+project+'/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D',project+'-1.21.1-neoforge.json')
fetch('https://api.github.com/repos/TheCBProject/NotEnoughItems','nei-github.json')
fetch('https://api.github.com/repos/TheCBProject/NotEnoughItems/releases','nei-releases.json')
fetch('https://raw.githubusercontent.com/TheCBProject/NotEnoughItems/master/build.properties','nei-build.properties')
fetch('https://raw.githubusercontent.com/TheCBProject/NotEnoughItems/master/src/main/java/codechicken/nei/api/API.java','nei-API.java')
# NeoForge FML dependency validation: ArtifactVersionRange.containsVersion may implement MC compatibility transforms.
fetch('https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/loading/VersionSupportMatrix.java','VersionSupportMatrix.java')
print('Primary sources saved:',len(list(OUT.iterdir())))
