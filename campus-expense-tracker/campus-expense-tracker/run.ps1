param(
    [ValidateSet('build', 'run', 'test')][string]$Task = 'run',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$AppArgs
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { 'javac' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
    New-Item -ItemType Directory -Force -Path build\classes | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -d build/classes '@sources.txt'
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Task -eq 'test') {
        New-Item -ItemType Directory -Force -Path build\test-classes | Out-Null
        & $javac -source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -cp build/classes -d build/test-classes src/test/java/campus/TrackerTests.java
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $java -cp 'build/classes;build/test-classes' campus.TrackerTests
        exit $LASTEXITCODE
    }
    if ($Task -eq 'run') {
        & $java -cp build/classes campus.Main @AppArgs
        exit $LASTEXITCODE
    }
    Write-Output 'Build successful.'
} finally { Pop-Location }
