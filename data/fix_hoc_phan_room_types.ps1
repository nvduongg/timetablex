# Chuan hoa cot "Loai phong" (cot 8, index 7) cho hoc_phan_mau*.tsv
# BV chi giu cho thuc tap/lam sang thuc su (MD, PUD, FN thuc tap, FTME thuc tap, ME vat ly tri lieu...)

$ErrorActionPreference = 'Stop'
$validRooms = @('LT','PM','TN','SB','XT','BV','DN','ONLINE','DA')

function Get-NewRoomType {
    param([string]$code, [string]$name, [string]$fac, [string]$current)
    if ($current -ne 'BV') { return $current }

    # --- Giu BV (lam sang / thuc tap co so y te) ---
    if ($code -match '^MD-') { return 'BV' }
    if ($code -match '^PUD-') { return 'BV' }
    if ($code -in @('FN-1035','FN-1036')) { return 'BV' }
    if ($code -eq 'FTME-1130') { return 'BV' }
    if ($code -eq 'ME-1062') { return 'BV' }
    if ($code -eq 'FTME-1121') { return 'BV' }

    # --- Chuyen BV sai sang loai hop ly ---
    if ($fac -eq 'FP' -and $code -eq 'FP-1003') { return 'TN' }

    if ($fac -eq 'FN' -and $code -in @('FN-1021','FN-1027')) { return 'LT' }

    if ($fac -eq 'ME' -and $code -eq 'ME-1059') { return 'TN' }

    if ($fac -eq 'FPH' -and $code -eq 'FPH-1146') { return 'LT' }

    if ($fac -eq 'FL' -and $code -in @('FL-1177','FL-1184','FL-1187')) { return 'LT' }
    if ($fac -eq 'FKL' -and $code -eq 'FKL-1201') { return 'LT' }
    if ($fac -eq 'FJL' -and $code -in @('FJL-1210','FJL-1221','FJL-1222')) { return 'LT' }
    if ($fac -eq 'FFL' -and $code -eq 'FFL-1240') { return 'LT' }
    if ($fac -eq 'FCL' -and $code -in @('FCL-1255','FCL-1259')) { return 'LT' }

    if ($fac -eq 'MEM' -and $code -eq 'MEM-1284') { return 'TN' }
    if ($fac -eq 'BCEE' -and $code -in @('BCEE-1295','BCEE-1302')) { return 'TN' }
    if ($fac -eq 'MSE' -and $code -match '^MSE-132') { return 'TN' }

    if ($fac -eq 'VEE' -and $code -eq 'VEE-1348') { return 'TN' }
    if ($fac -eq 'VEE' -and $code -eq 'VEE-1353') { return 'PM' }

    if ($fac -eq 'FTH' -and $code -eq 'FTH-1365') { return 'LT' }

    if ($fac -eq 'EIB' -and $code -eq 'EIB-1390') { return 'LT' }
    if ($fac -eq 'EIB' -and $code -eq 'EIB-1398') { return 'DN' }

    if ($fac -eq 'FBA' -and $code -match '^FBA-14') { return 'LT' }

    if ($fac -eq 'FCS' -and $code -eq 'FCS-1470') { return 'PM' }
    if ($fac -eq 'FAD' -and $code -match '^FAD-14') { return 'PM' }

    if ($fac -eq 'FFS' -and $code -match '^FFS-15') { return 'XT' }

    if ($fac -eq 'FOL' -and $code -eq 'FOL-1518') { return 'LT' }

    if ($fac -eq 'FIDT' -and $code -match '^FIDT-15') { return 'PM' }

    return 'BV'
}

function Repair-Tsv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { Write-Warning "Skip missing: $Path"; return }
    $lines = Get-Content -LiteralPath $Path -Encoding UTF8
    if ($lines.Count -lt 2) { return }
    $out = New-Object System.Collections.Generic.List[string]
    $out.Add($lines[0]) | Out-Null
    $changed = 0
    $creditWarn = 0

    for ($i = 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $cols = $line -split "`t", -1
        if ($cols.Count -lt 9) {
            $out.Add($line) | Out-Null
            continue
        }

        try {
            $tot = [int]$cols[2]; $ltc = [int]$cols[3]; $thc = [int]$cols[4]; $slf = [int]$cols[5]
            if ($tot -ne ($ltc + $thc + $slf)) { $creditWarn++ }
        } catch { }

        $rt = $cols[7]
        if ($rt -notin $validRooms) {
            Write-Warning ("[$Path] " + $cols[0] + " room type not in list: " + $rt)
        }

        $newRt = Get-NewRoomType -code $cols[0] -name $cols[1] -fac $cols[8] -current $rt
        if ($newRt -ne $rt) {
            $cols[7] = $newRt
            $changed++
        }
        $out.Add(($cols -join "`t")) | Out-Null
    }

    Set-Content -LiteralPath $Path -Value $out -Encoding UTF8
    Write-Host ($Path + ' : changed ' + $changed + ' rows; TC mismatch warnings: ' + $creditWarn)
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Repair-Tsv (Join-Path $root 'hoc_phan_mau.tsv')
Repair-Tsv (Join-Path $root 'hoc_phan_mau_550.tsv')
Write-Host 'Done.'
