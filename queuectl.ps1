#!/usr/bin/env pwsh
# Wrapper so you can run `./queuectl.ps1 <command>` instead of the full java -jar invocation.
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $ScriptDir "target/queuectl.jar"

if (-not (Test-Path $Jar)) {
    Write-Error "queuectl.jar not found at $Jar. Build it first with: mvn package"
    exit 1
}

& java -jar $Jar @Args
exit $LASTEXITCODE
