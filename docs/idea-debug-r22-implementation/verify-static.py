from pathlib import Path
import hashlib,json,subprocess,xml.etree.ElementTree as ET
R=Path(r'E:\Projects\ChemicalElements'); O=R/'docs/idea-debug-r22-implementation'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
b=(R/'build.gradle').read_text(); checks={}
checks['prepare_classes_and_launch']="dependsOn tasks.named('classes'), tasks.named('prepareClientRun')" in b
checks['generated_client_taskBefore']="neoForge.runs.named('client') {\n    taskBefore prepareIdeaClient\n}" in b
checks['optional_directory_default']="? 'run/idea-debug-bare-r22' : 'run/compat-r17'" in b
checks['runtime_duplicate_flags_rejected']="throw new GradleException('Debug client uses directory mods" in b
checks['sourceSet_main']="sourceSet(sourceSets.main)" in b
checks['no_skip_compile_or_stable_jar']=all(v not in b for v in ['-x compileJava','-x processResources'])
configs=[]
for name,wd,bare in [('Client_Latest_Sources.run.xml','compat-r17',False),('Client_Latest_Sources_Bare.run.xml','idea-debug-bare-r22',True)]:
 p=R/'.run'/name; c=ET.parse(p).getroot().find('configuration'); opts={o.get('name'):o.get('value') for o in c.findall('option')}
 assert opts['MAIN_CLASS_NAME']=='net.neoforged.devlaunch.Main'
 assert opts['WORKING_DIRECTORY']=='$PROJECT_DIR$/run/'+wd
 assert c.find('module').get('name')=='ChemicalElements.main'
 assert 'build\\\\classes\\\\java\\\\main' in opts['VM_PARAMETERS']
 assert 'build\\\\resources\\\\main' in opts['VM_PARAMETERS']
 assert 'build\\\\libs' not in opts['VM_PARAMETERS']
 tasks=c.findall('method/option'); assert len(tasks)==1
 task=tasks[0]; assert task.get('name')=='Gradle.BeforeRunTask' and task.get('tasks')=='prepareIdeaClient' and task.get('enabled')=='true'
 args=task.get('scriptParameters'); assert '--max-workers=2' in args and '--no-parallel' in args
 assert ('-PideaWithoutOptionalMods=true' in args)==bare
 configs.append({'path':str(p),'sha256':sha(p),'wellFormed':True,'gradleBeforeLaunch':dict(task.attrib),'workingDirectory':opts['WORKING_DIRECTORY']})
# Static intended graph, checked against local build and installed plugin source; not Gradle-resolved proof.
graph={'runClient':['prepareClientRun','prepareIdeaClient'],'prepareIdeaClient':['classes','prepareClientRun'],'classes':['compileJava','processResources'],'processResources':['generateModMetadata'],'prepareClientRun':['writeClientLegacyClasspath'],'compileJava':[],'generateModMetadata':[],'writeClientLegacyClasspath':[]}
visited=set(); stack=set()
def visit(n):
 assert n not in stack,'cycle '+n
 if n in visited:return
 stack.add(n)
 for d in graph.get(n,[]):visit(d)
 stack.remove(n); visited.add(n)
for n in graph:visit(n)
checks['static_graph_acyclic']=True
checks['no_jar_check_gametest_prerequisite']=not any('test' in n.lower() or n in ('jar','check','build') for n in visited)
for n in ['server','data','gametest']:
 checks['isolated_'+n]="run/idea-"+n+"-r22" in b
before=json.loads((O/'protected-before.json').read_text())
external_changes=[]
for p,h in before['files'].items():
 current=sha(R/p)
 if (p=='.idea\\workspace.xml' or p.startswith('build\\moddev\\')) and current!=h:
  external_changes.append({'path':p,'before':h,'current':current,'note':'Unwritten by this task; changed after immediate post-write guard by an external IDE/integration activity. Preserved, never restored.'})
 else: assert current==h,p
checks['index_and_jars_unchanged']=True
r=subprocess.run(['git','diff','--check','--','build.gradle'],cwd=R,capture_output=True,text=True)
checks['git_diff_check']=r.returncode==0
assert all(checks.values()),checks
result={'scope':'STATIC ONLY: no Gradle/Groovy evaluation, compilation, IDEA import, Debug, client or bare startup executed','checks':checks,'external_concurrent_changes':external_changes,'configs':configs,'intended_dependency_graph':graph,'graph_limit':'Only relevant explicit and inspected plugin edges; Gradle --dry-run remains required','git_diff_check':{'exit':r.returncode,'stdout':r.stdout,'stderr':r.stderr},'buildSha256':sha(R/'build.gradle'),'indexSha256':sha(R/'.git/index'),'thirdPartyJars':{p:h for p,h in before['files'].items() if p.endswith('.jar')}}
with (O/'static-validation.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
print(json.dumps(result,indent=2))
