# Môn ONLINE_ELEARNING / ONLINE_COURSERA: Loại phòng = ONLINE (trừ BV, DN — vẫn cần địa điểm thực tế)
param([string[]]$TsvPaths = @())
$ErrorActionPreference = 'Stop'
if ($TsvPaths.Count -eq 0) {
    $TsvPaths = @(
        (Join-Path $PSScriptRoot 'hoc_phan_mau.tsv'),
        (Join-Path $PSScriptRoot 'hoc_phan_mau_550.tsv')
    ) | Where-Object { Test-Path -LiteralPath $_ }
}

foreach ($path in $TsvPaths) {
    $lines = Get-Content -LiteralPath $path -Encoding UTF8
    $changed = 0
    $out = [System.Collections.Generic.List[string]]::new()
    $out.Add($lines[0]) | Out-Null
    for ($i = 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $cols = $line -split "`t", -1
        if ($cols.Count -lt 8) { $out.Add($line) | Out-Null; continue }
        $lm = $cols[6].Trim()
        $rt = $cols[7].Trim()
        if ($lm -eq 'ONLINE_ELEARNING' -or $lm -eq 'ONLINE_COURSERA') {
            if ($rt -ne 'BV' -and $rt -ne 'DN' -and $rt -ne 'ONLINE') {
                $cols[7] = 'ONLINE'
                $changed++
            }
        }
        $out.Add(($cols -join "`t")) | Out-Null
    }
    Set-Content -LiteralPath $path -Value $out -Encoding UTF8
    Write-Host "$(Split-Path $path -Leaf): set ONLINE for $changed rows"
}
