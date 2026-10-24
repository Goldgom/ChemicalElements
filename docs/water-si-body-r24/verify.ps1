$ErrorActionPreference='Stop'
Set-Location 'E:\Projects\ChemicalElements'
$out='build/water-si-body-r24/final'
New-Item -ItemType Directory -Force "$out/model","$out/adapter","$out/empty" | Out-Null
$files=@('src/main/java/org/goldgom/chemicalelements/fluid/FiniteVolume.java','src/main/java/org/goldgom/chemicalelements/fluid/SorptionExchange.java')+@(Get-ChildItem src/main/java/org/goldgom/chemicalelements/fluid/waterbody/*.java|ForEach-Object FullName)+@('src/test/java/org/goldgom/chemicalelements/fluid/waterbody/WaterBodyModelTest.java')
& 'D:/Java/jdk-21/bin/javac.exe' -J-XX:ActiveProcessorCount=1 -J-Xmx256m -proc:none -d "$out/model" $files
if($LASTEXITCODE -ne 0){throw "model compile exit=$LASTEXITCODE"}; 'MODEL_COMPILE_EXIT=0'
& 'D:/Java/jdk-21/bin/java.exe' -XX:ActiveProcessorCount=1 -Xmx128m -cp "$out/model" org.goldgom.chemicalelements.fluid.waterbody.WaterBodyModelTest
if($LASTEXITCODE -ne 0){throw "model run exit=$LASTEXITCODE"}; 'MODEL_RUN_EXIT=0'
$cp=(@('build/moddev/artifacts/neoforge-21.1.264.jar','build/moddev/artifacts/neoforge-21.1.264-merged.jar')+(Get-Content build/moddev/gameTestServerLegacyClasspath.txt)+@("$out/model",'build/classes/java/main')) -join ';'
$files=@(Get-ChildItem src/main/java/org/goldgom/chemicalelements/world/water/*.java|ForEach-Object FullName)+@('src/main/java/org/goldgom/chemicalelements/world/NaturalWaterInitialization.java','src/main/java/org/goldgom/chemicalelements/world/NaturalWaterProfile.java','src/main/java/org/goldgom/chemicalelements/fluid/SorptionLibrary.java','src/main/java/org/goldgom/chemicalelements/gametest/water/WaterGeometryGameTests.java')
# Empty sourcepath prevents javac from implicitly compiling Java sources embedded in dependency jars.
& 'D:/Java/jdk-21/bin/javac.exe' -J-XX:ActiveProcessorCount=1 -J-Xmx384m -proc:none -sourcepath "$out/empty" -cp $cp -d "$out/adapter" $files
if($LASTEXITCODE -ne 0){throw "adapter compile exit=$LASTEXITCODE"}; 'ADAPTER_COMPILE_EXIT=0'
