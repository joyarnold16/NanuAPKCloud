param([Parameter(Mandatory=$true)][string]$Dependencies)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $deps = (Resolve-Path $Dependencies).Path
    $cp = "$deps/json.jar;$deps/junit.jar;$deps/hamcrest.jar"
    New-Item -ItemType Directory -Force build/paper-classes | Out-Null
    $names = @('Ohlcv','PaperExecution','PaperLedger','DexCandidate','DexSafetyPolicy','TokenSecurity','TradeRecord','CandlePatterns','DexDataClient','ScalpingStrategy','AutoTradingPolicy')
    $sources = $names | ForEach-Object { "app/src/main/java/com/nanu/aitradingbot/$_.java" }
    $tests = @(Get-ChildItem app/src/test/java/com/nanu/aitradingbot -Filter '*.java') + @(Get-ChildItem nas/test/com/nanu/aitradingbot -Filter '*.java')
    $nas = Get-ChildItem nas/src/com/nanu/aitradingbot -Filter '*.java'
    & javac --release 11 -cp $cp -d build/paper-classes @sources $nas.FullName $tests.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    $testNames = $tests | Where-Object { $_.BaseName.EndsWith('Test') } | ForEach-Object { 'com.nanu.aitradingbot.' + $_.BaseName }
    & java -cp "build/paper-classes;$cp" org.junit.runner.JUnitCore @testNames
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
    New-Item -ItemType Directory -Force build/nas-classes | Out-Null
    & javac --release 11 -cp "$deps/json.jar" -d build/nas-classes @sources $nas.FullName
    if ($LASTEXITCODE -ne 0) { throw 'NAS compilation failed' }
    & jar --create --file build/nanu-paper-nas.jar --main-class com.nanu.aitradingbot.NasPaperMain -C build/nas-classes com/nanu/aitradingbot
    if ($LASTEXITCODE -ne 0) { throw 'NAS build failed' }
} finally { Pop-Location }
