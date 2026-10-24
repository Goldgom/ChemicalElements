$ErrorActionPreference = 'Stop'
Set-Location 'E:\Projects\ChemicalElements'
$root = (Get-Location).Path
$out = Join-Path $root 'docs/material-owner-key-r24'
$files = @(
 'src/main/java/org/goldgom/chemicalelements/api/tx/OwnerKey.java',
 'src/test/java/org/goldgom/chemicalelements/api/tx/MaterialOwnerKeyTest.java',
 'src/main/java/org/goldgom/chemicalelements/gametest/core/MaterialOwnerKeyGameTests.java'
)
$source = [IO.File]::ReadAllText((Join-Path $root $files[0])).Replace("`r`n", "`n")
$legacy = @'
    record PlayerInventory(UUID player) implements OwnerKey {
        public PlayerInventory { Objects.requireNonNull(player, "player"); }
    }
    record MachineInventory(ResourceLocation dimension, long controllerPos, UUID incarnation,
                            ResourceLocation machineType) implements OwnerKey {
        public MachineInventory {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(incarnation, "incarnation");
            Objects.requireNonNull(machineType, "machineType");
        }
    }
'@
$checks = [ordered]@{}
$checks.legacyRecordsUnchanged = $source.Contains($legacy.Replace("`r`n", "`n"))
$checks.fourRecords = ([regex]::Matches($source, '\brecord\s+\w+').Count -eq 4)
$checks.worldSignature = $source.Contains('record WorldBlock(ResourceLocation dimension, long blockPos, UUID incarnation) implements OwnerKey')
$checks.heatSignature = [regex]::IsMatch($source, 'record FiniteHeatSource\(ResourceLocation dimension, long blockPos, UUID incarnation,\s+ResourceLocation port\) implements OwnerKey')
$checks.permitsFour = [regex]::IsMatch($source, 'permits OwnerKey.PlayerInventory, OwnerKey.MachineInventory,\s+OwnerKey.WorldBlock, OwnerKey.FiniteHeatSource')
$checks.nineNullGuards = ([regex]::Matches($source, 'Objects.requireNonNull\(').Count -eq 9)
$checks.noRandomIncarnation = ($source -notmatch 'randomUUID|nameUUIDFromBytes')
$checks.noVersionComponents = ($source -notmatch '(?m)^\s*(record.*|ResourceLocation .*).*(session|revision|generation)')
$test = [IO.File]::ReadAllText((Join-Path $root $files[1]))
$game = [IO.File]::ReadAllText((Join-Path $root $files[2]))
$checks.testNineNullCases = ([regex]::Matches($test, 'rejectsNull\(\(\)').Count -eq 9)
$checks.gameNineNullCases = ([regex]::Matches($game, 'rejectsNull\(helper,').Count -eq 9)
$checks.twoGameTests = ([regex]::Matches($game, '@GameTest\(').Count -eq 2)
$checks.bothHaveVariantMatrix = ($test.Contains('List<OwnerKey> variants') -and $game.Contains('List<OwnerKey> variants'))
$checks.noTrailingWhitespace = $true
foreach ($file in $files) {
 if ([IO.File]::ReadAllText((Join-Path $root $file)) -match '(?m)[\t ]+\r?$') { $checks.noTrailingWhitespace = $false }
}
$indexExpected = '82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69'
$indexAfter = (Get-FileHash '.git/index' -Algorithm SHA256).Hash
$staged = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read staged paths' }
$checks.indexHashUnchanged = ($indexAfter -eq $indexExpected)
$checks.thirteenStagedPaths = ($staged.Count -eq 13)
$checks.noIndexLock = !(Test-Path '.git/index.lock')
$evidence = [ordered]@{
 version = 'material-owner-key-r24-v1'; taskId = 'chem-material-owner-key-r24'
 authoritativeUserTime = '2026-10-24 07:51 EDT (turn start)'
 root = $root; baselineOwnerKeySHA256 = 'A3CD1BBE496D577FA175217F4864E92460030768CA35560B2E404AEEFF409291'
 indexBeforeSHA256 = $indexExpected; indexAfterSHA256 = $indexAfter
 stagedPaths = $staged; checks = $checks
 execution = 'Static PowerShell text/hash checks only; Gradle, javac, Java main, GameTest, client and screenshots NOT RUN'
}
$evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $out 'static-evidence.json') -Encoding UTF8
$entries = @($files + @('docs/material-owner-key-r24/static-check.ps1','docs/material-owner-key-r24/static-evidence.json','docs/material-owner-key-r24/report.md') | ForEach-Object {
 $path = Join-Path $root $_
 [ordered]@{path=$_;sha256=(Get-FileHash $path -Algorithm SHA256).Hash;bytes=(Get-Item $path).Length}
})
$manifest = [ordered]@{
 version='material-owner-key-r24-v1'; taskId='chem-material-owner-key-r24'; files=$entries
 validation='Static checks only; Java compilation and all runtime checks NOT RUN'
 integration='Next explicitly versioned integration input, not fixed 2693; main MaterialOwnerKeyTest and two MaterialOwnerKeyGameTests methods'
 safety='Value identities are not real Owner/CAS, placement/phase or water/production readiness; gates remain closed'
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $out 'manifest.json') -Encoding UTF8
$checks | ConvertTo-Json
Get-FileHash (Join-Path $out 'manifest.json') -Algorithm SHA256 | Format-List
if (@($checks.Values | Where-Object {$_ -ne $true}).Count -gt 0) { throw 'Static checks failed: inspect static-evidence.json' }
