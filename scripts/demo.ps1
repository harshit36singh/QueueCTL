# queuectl live walkthrough — run this in PowerShell while screen-recording.
#
# Set your DB credentials BEFORE running this script (they are never hardcoded here):
#   $env:QUEUECTL_DB_USER = "queuectl"
#   $env:QUEUECTL_DB_PASSWORD = "<your-password>"
#
# Then, from the project root:
#   .\scripts\demo.ps1
#
# The script pauses before each step so you can narrate. Press Enter to advance.
# It opens the worker and the dashboard in their own PowerShell windows so you can
# show live processing and the browser side by side with the commands you're running.
#
# Job JSON is piped into `enqueue` via stdin rather than passed as a CLI argument --
# Windows/PowerShell cannot reliably pass a double-quoted JSON string containing
# spaces as a single native-process argument, so `enqueue` supports reading from
# stdin instead (pass no positional argument, or "-").

$ErrorActionPreference = "Stop"
$jar = "target\queuectl.jar"
$port = 8080

function Section($title) {
    Write-Host ""
    Write-Host "==============================================================" -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host "==============================================================" -ForegroundColor Cyan
}

function Wait-ForNarration($msg = "Press Enter to continue...") {
    Read-Host $msg | Out-Null
}

function Run($cmd) {
    Write-Host ""
    Write-Host "> $cmd" -ForegroundColor Yellow
    Invoke-Expression $cmd
}

function Enqueue($jsonObj) {
    $json = $jsonObj | ConvertTo-Json -Compress
    Write-Host ""
    Write-Host "> ... | java -jar $jar enqueue    (piping: $json)" -ForegroundColor Yellow
    $json | & java -jar $jar enqueue
}

if (-not $env:QUEUECTL_DB_USER -or -not $env:QUEUECTL_DB_PASSWORD) {
    Write-Host "QUEUECTL_DB_USER / QUEUECTL_DB_PASSWORD are not set in this shell." -ForegroundColor Red
    Write-Host 'Set them first, e.g.: $env:QUEUECTL_DB_USER = "queuectl"; $env:QUEUECTL_DB_PASSWORD = "..."' -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $jar)) {
    Write-Host "Building queuectl (target\queuectl.jar not found)..." -ForegroundColor Cyan
    mvn -q package -DskipTests
}

$suffix = Get-Date -Format "HHmmss"

Section "0. Start the dashboard (own window) so it's visible for the whole recording"
$dashRunning = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if (-not $dashRunning) {
    Wait-ForNarration
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; java -jar $jar dashboard start --port $port"
    Write-Host "Waiting for the dashboard to come up..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 8
    Start-Process "http://localhost:$port"
} else {
    Write-Host "Dashboard already running on port $port, reusing it." -ForegroundColor DarkGray
    Start-Process "http://localhost:$port"
}
Wait-ForNarration "Dashboard should now be open in your browser (all zeros). Press Enter to continue..."

Section "1. Enqueue a job that succeeds"
Wait-ForNarration
Enqueue @{ id = "demo-ok-$suffix"; command = "echo Hello from queuectl" }

Section "2. Enqueue a job that will fail, retry with exponential backoff, and land in the DLQ"
Wait-ForNarration
Enqueue @{ id = "demo-fail-$suffix"; command = "exit 1"; max_retries = 3; backoff_base = 2 }

Section "3. Enqueue a longer-running job so you can point at 'processing' state"
Wait-ForNarration
Enqueue @{ id = "demo-slow-$suffix"; command = "ping -n 8 127.0.0.1" }

Section "4. Jobs are sitting in MySQL right now -- nothing is running them yet"
Wait-ForNarration
Run "java -jar $jar list"
Run "java -jar $jar status"

Section "5. Start a worker (own window) and watch it drain the queue live"
Wait-ForNarration
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; java -jar $jar worker start --count 2"
Write-Host "Worker window launched -- point at it and the dashboard as jobs move through pending -> processing -> completed/dead." -ForegroundColor Green
Wait-ForNarration "Give it ~10-15s to finish retries, then press Enter to continue..."

Section "6. Confirm the failing job reached the Dead Letter Queue"
Wait-ForNarration
Run "java -jar $jar dlq list"

Section "7. Retry the dead job from the CLI (you can also click Retry on the dashboard)"
Wait-ForNarration
Run "java -jar $jar dlq retry demo-fail-$suffix"
Run "java -jar $jar list --state pending"

Section "8. Runtime config"
Wait-ForNarration
Run "java -jar $jar config get"
Run "java -jar $jar config set max-retries 5"
Run "java -jar $jar config get max-retries"

Section "9. Persistence across restarts"
Write-Host "Every queuectl command is its own fresh process reading shared state from MySQL --" -ForegroundColor DarkGray
Write-Host "the 'status'/'list' output above is proof: no worker or dashboard process needs to" -ForegroundColor DarkGray
Write-Host "stay alive for job data to survive. Restart your machine and 'queuectl list' still" -ForegroundColor DarkGray
Write-Host "shows the same jobs." -ForegroundColor DarkGray
Wait-ForNarration

Section "Demo complete"
Write-Host "Stop the worker/dashboard windows with Ctrl+C when you're done recording." -ForegroundColor Cyan
