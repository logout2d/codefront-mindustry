<#
.SYNOPSIS
  CF-MIND-SPIKE-001 local headless runner (Windows).

.DESCRIPTION
   1. Builds the Codefront mod (JDK 17 via Gradle wrapper).
   2. Locates the official Mindustry v159.7 server artifact (downloads it to a
      local, gitignored runtime cache if missing).
   3. Builds an isolated runtime directory under build/spike-runtime.
   4. Launches the headless server as a MANAGED background process.
   5. Polls the log for `CodefrontSpike Status=PASS` / failure markers with a
      bounded timeout, then terminates the server.
   6. Prints the resulting log tail and returns an exit code.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\run-spike.ps1
#>

param(
    [string]$MindustryVersion = "v159.7",
    [string]$ServerJarUrl = "https://github.com/Anuken/Mindustry/releases/download/v159.7/server-release.jar",
    # Bounded wall-clock timeout (seconds) for the whole acceptance run.
    [int]$TimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- locate JDK --------------------------------------------------------------
# Prefer JAVA_HOME; otherwise assume `java` is on PATH.
$java = if($env:JAVA_HOME){ Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

Write-Host "[run-spike] building mod..."
& "$root\gradlew.bat" build --no-daemon | Out-Host
if($LASTEXITCODE -ne 0){ throw "Gradle build failed" }
$modJar = Get-ChildItem "$root\build\libs\*.jar" | Select-Object -First 1
if(-not $modJar){ throw "No mod jar produced" }

# --- cache server artifact ---------------------------------------------------
$cacheDir = Join-Path $root "build\mindustry-cache"
New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
$serverJar = Join-Path $cacheDir "server-release.jar"
if(-not (Test-Path $serverJar)){
    Write-Host "[run-spike] downloading official Mindustry $MindustryVersion server..."
    Invoke-WebRequest -Uri $ServerJarUrl -OutFile $serverJar -UseBasicParsing
    if(-not (Test-Path $serverJar)){ throw "Server download failed" }
}

# --- prepare isolated runtime dir --------------------------------------------
$rt = Join-Path $root "build\spike-runtime"
$modsDir = Join-Path $rt "config\mods"
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Copy-Item $serverJar -Destination (Join-Path $rt "server-release.jar") -Force
Copy-Item $modJar.FullName -Destination (Join-Path $modsDir "codefront-mindustry.jar") -Force

$log = Join-Path $root "build\spike-run.log"
$err = Join-Path $root "build\spike-run.log.err"

# --- run the acceptance (with one retry for transient early-exit) -------------
$result = ""
for($attempt = 1; $attempt -le 2; $attempt++){
    Remove-Item $log, $err -ErrorAction SilentlyContinue

    # make sure no stale headless server is running before launching
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    Write-Host "[run-spike] launching headless server (attempt $attempt, managed)..."
    $proc = Start-Process -FilePath $java -ArgumentList "-jar","server-release.jar" `
        -WorkingDirectory $rt -RedirectStandardOutput $log -RedirectStandardError $err -PassThru

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while((Get-Date) -lt $deadline -and -not $proc.HasExited){
        if(Test-Path $log){
            $content = Get-Content $log -Raw -ErrorAction SilentlyContinue
            if($content -match "CodefrontSpike Status=PASS"){ $result = "PASS"; break }
            if($content -match "CodefrontSpike Match=\d+ Status=FAIL"){ $result = "FAIL_MATCH"; break }
            if($content -match "Exception in thread" -or $content -match "^\s+at mindustry\."){ $result = "CRASH"; break }
        }
        Start-Sleep -Seconds 2
    }

    if(-not $proc.HasExited){
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        $proc.WaitForExit(5000) | Out-Null
    }

    # derive the result from the log if the poll did not set it (JVM self-exit)
    if(-not $result -and (Test-Path $log)){
        $content = Get-Content $log -Raw -ErrorAction SilentlyContinue
        if($content -match "CodefrontSpike Status=PASS"){ $result = "PASS" }
        elseif($content -match "CodefrontSpike Match=\d+ Status=FAIL"){ $result = "FAIL_MATCH" }
        elseif($content -match "Exception in thread" -or $content -match "^\s+at mindustry\."){ $result = "CRASH" }
        else{ $result = "TIMEOUT" }
    }
    if(-not $result){ $result = "TIMEOUT" }

    if($result -eq "PASS"){ break }
    if($attempt -lt 2){ Write-Host "[run-spike] attempt $attempt ended with $result, retrying once..." }
}

Write-Host ""
Write-Host "==================== log tail ===================="
if(Test-Path $log){ Get-Content $log -Tail 70 }
if(Test-Path $err){
    $e = Get-Content $err -Tail 20
    if($e){ Write-Host "--- stderr ---"; $e }
}
Write-Host "=================================================="

Write-Host "[run-spike] RESULT=$result"
exit ($result -eq "PASS" ? 0 : 1)
