param(
    [Parameter(Mandatory)][ValidateSet('start', 'status', 'stop')][string]$Action,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Distribution
)
$ErrorActionPreference = 'Stop'
$sessionDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'abservice-wsl-sessions'
$statePath = Join-Path $sessionDirectory ($Distribution + '.json')
$sessionProcess = $null
if (Test-Path -LiteralPath $statePath) {
    $saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $candidate = Get-Process -Id $saved.pid -ErrorAction SilentlyContinue
    if ($candidate -and $candidate.ProcessName -eq 'wsl' -and
        $candidate.StartTime.ToUniversalTime() -eq ([datetime]$saved.started).ToUniversalTime()) {
        $sessionProcess = $candidate
    }
}
if ($Action -eq 'start' -and -not $sessionProcess) {
    New-Item -ItemType Directory -Path $sessionDirectory -Force | Out-Null
    $sessionProcess = Start-Process -FilePath "$env:SystemRoot/System32/wsl.exe" `
        -ArgumentList @('-d', $Distribution, '--exec', 'sleep', 'infinity') `
        -WindowStyle Hidden -PassThru
    @{ pid = $sessionProcess.Id; started = $sessionProcess.StartTime.ToUniversalTime().ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
}
if ($Action -eq 'stop') {
    # Only the exact keeper process created above; never terminate a distribution.
    if ($sessionProcess) { Stop-Process -Id $sessionProcess.Id }
    if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath }
    Write-Output "Stopped the ABService WSL keeper for $Distribution."
} elseif ($sessionProcess) {
    Write-Output "ABService WSL keeper for $Distribution is running (PID $($sessionProcess.Id))."
} else {
    Write-Output "No ABService WSL keeper for $Distribution."
}