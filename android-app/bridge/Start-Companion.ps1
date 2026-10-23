$ErrorActionPreference = 'Stop'
$tokenPath = Join-Path $PSScriptRoot '.bridge-token.secret'
if (-not (Test-Path -LiteralPath $tokenPath)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes) | Set-Content -LiteralPath $tokenPath -Encoding ASCII
}
$env:MUSICTRANSI_TOKEN = (Get-Content -LiteralPath $tokenPath -Raw).Trim()
$env:MUSICTRANSI_HOST = '0.0.0.0'
Write-Host 'MusicTransi companion: allow Private networks only. Keep this window open.'
Write-Host 'Paste this token into MusicTransi iPhone Settings (do not share it):'
Write-Host $env:MUSICTRANSI_TOKEN
& (Join-Path $PSScriptRoot 'bin\bridge.bat')
