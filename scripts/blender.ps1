# Runs one of tools\blender\<name>.py in Blender without a window (hdfv 0.11): textures, details, fx, machines,
# review, selftest. The scripts write their results into neoforge\src\main\resources (committed, so building the mod
# never needs Blender) or, for review renders, under %USERPROFILE%\.mcmod. Blender is found from HDFV_BLENDER or the
# default install path; its output goes to a log under %USERPROFILE%\.mcmod\tmp.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\blender.ps1 textures
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\blender.ps1 review C:\path\to\model.obj
param(
    [Parameter(Mandatory = $true, Position = 0)] [string] $Script,
    [Parameter(ValueFromRemainingArguments = $true)] [string[]] $ScriptArguments = @()
)
$ErrorActionPreference = 'Stop'
$blender = $env:HDFV_BLENDER
if (-not $blender) {
    $candidates = @(
        'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe',
        'C:\Program Files\Blender Foundation\Blender 5.1\blender.exe',
        'C:\Program Files\Blender Foundation\Blender 5.0\blender.exe',
        'C:\Program Files\Blender Foundation\Blender 4.5\blender.exe'
    )
    $blender = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $blender -or -not (Test-Path -LiteralPath $blender)) { throw 'Blender not found: set HDFV_BLENDER to blender.exe' }
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root ("tools\blender\" + $Script + '.py')
if (-not (Test-Path -LiteralPath $file)) { throw "No such script: $file" }
$logDir = Join-Path $env:USERPROFILE '.mcmod\tmp'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir ("blender-" + $Script + '.log')
$env:HDFV_ROOT = $root
# Start-Process, not &: Windows PowerShell 5 turns a program's stderr lines into errors that stop the script
$quoted = @('-b', '--factory-startup', '--python-exit-code', '1', '--python', ('"' + $file + '"'), '--') + ($ScriptArguments | ForEach-Object { '"' + $_ + '"' })
$errLog = $log + '.err'
$process = Start-Process -FilePath $blender -ArgumentList $quoted -NoNewWindow -Wait -PassThru -RedirectStandardOutput $log -RedirectStandardError $errLog
$code = $process.ExitCode
Get-Content -LiteralPath $log, $errLog | Select-String -Pattern '^(hdfv|Error|Traceback|  File|\w+Error)' | ForEach-Object { $_.Line }
Write-Output "blender $Script exit $code (log: $log)"
exit $code
