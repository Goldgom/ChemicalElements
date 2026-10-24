from pathlib import Path
import difflib
import xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[2]
d=Path(__file__).resolve().parent
s=(root/'build.gradle').read_text()
a=s.index('            // Dedicated optional-mod test environment;')
b=s.index('            // Comma-separated list',a)
replacement="""            // Directory mods are the single optional-mod source for the debug client.
            if (['withCurios', 'withJade', 'withJei'].any { providers.gradleProperty(it).getOrElse('false').toBoolean() }) {
                throw new GradleException('Debug client uses directory mods; remove withCurios/withJade/withJei runtimeOnly flags')
            }
            gameDirectory = project.file(providers.gradleProperty('ideaWithoutOptionalMods')
                .getOrElse('false').toBoolean() ? 'run/idea-debug-bare-r22' : 'run/compat-r17')

"""
t=s[:a]+replacement+s[b:]
for old,new in [('            server()\n',"            server()\n            gameDirectory = project.file('run/idea-server-r22')\n"),('            type = "gameTestServer"\n',"            type = \"gameTestServer\"\n            gameDirectory = project.file('run/idea-gametest-r22')\n"),('            data()\n',"            data()\n            gameDirectory = project.file('run/idea-data-r22')\n")]:
    assert t.count(old)==1
    t=t.replace(old,new,1)
anchor='neoForge.ideSyncTask generateModMetadata\n'
t=t.replace(anchor,anchor+"""
// Explicit before-launch entry: no check/build/GameTest/jar prerequisite.
tasks.register('prepareIdeaClient') {
    dependsOn tasks.named('classes'), tasks.named('prepareClientRun')
}
tasks.named('runClient') {
    dependsOn tasks.named('classes')
}
""",1)
(d/'build.gradle.candidate.patch').write_text(''.join(difflib.unified_diff(s.splitlines(True),t.splitlines(True),fromfile='a/build.gradle',tofile='b/build.gradle')),encoding='utf-8')
w=E.parse(root/'.idea/workspace.xml')
orig=w.find('.//component[@name="RunManager"]/configuration[@name="Client"]')
orig.set('name','Client - Latest Sources + Curios Jade JEI')
orig.find('option[@name="WORKING_DIRECTORY"]').set('value','$PROJECT_DIR$/run/compat-r17')
m=orig.find('method'); m.clear(); m.set('v','2')
E.SubElement(m,'option',{'name':'Gradle.BeforeRunTask','enabled':'true','tasks':'prepareIdeaClient','externalProjectPath':'$PROJECT_DIR$','vmOptions':'','scriptParameters':'--max-workers=2 --no-parallel','executionName':'Prepare current sources and resources'})
c=E.Element('component',{'name':'ProjectRunConfigurationManager'}); c.append(orig); E.indent(c)
E.ElementTree(c).write(d/'Client_Latest.run.xml.candidate.txt',encoding='utf-8',xml_declaration=True)
print('Generated candidate diff and XML only; no project configuration applied.')
