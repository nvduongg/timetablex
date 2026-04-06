# Sinh giang_vien_import_mau.tsv (UTF-8 BOM) — mo Excel roi Save As xlsx neu can.
# Logic giong gen_giang_vien_mau.py
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$hocPhan = Join-Path $root "hoc_phan_mau.tsv"
$poolFile = Join-Path $root "giang_vien_cong_khai_pool.tsv"
$outTsv = Join-Path $root "giang_vien_import_mau.tsv"

$LECT_PER_COURSE = 4
$MIN_PER_FAC = 14

# Email ngắn, duy nhất theo (khoa + slot trong pool): fidt.007@gv.phenikaa.vn
function Get-LecturerEmail([string]$fac, [int]$slot) {
    return "$($fac.ToLowerInvariant()).$($slot.ToString('000'))@gv.phenikaa.vn"
}

# courses by faculty: list of @{code; dept}
$byFac = @{}
Get-Content $hocPhan -Encoding UTF8 | Select-Object -Skip 1 | ForEach-Object {
    $p = $_ -split "`t"
    if ($p.Count -lt 10) { return }
    $code = $p[0].Trim(); $fac = $p[8].Trim(); $dept = $p[9].Trim()
    if (-not $code -or -not $fac) { return }
    if (-not $byFac.ContainsKey($fac)) { $byFac[$fac] = New-Object System.Collections.ArrayList }
    [void]$byFac[$fac].Add(@{ code = $code; dept = $dept })
}

# pool rows
$poolRows = New-Object System.Collections.ArrayList
Get-Content $poolFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }
    $p = $line -split "`t"
    if ($p.Count -lt 2) { return }
    $n = $p[0].Trim(); $f = $p[1].Trim()
    if ($n -eq "ho_ten") { return }
    if ($n -and $f) { [void]$poolRows.Add(@{ ho_ten = $n; ma_khoa = $f }) }
}

$byF = @{}
foreach ($r in $poolRows) {
    if (-not $byF.ContainsKey($r.ma_khoa)) { $byF[$r.ma_khoa] = New-Object System.Collections.ArrayList }
    [void]$byF[$r.ma_khoa].Add(@{ ho_ten = $r.ho_ten; ma_khoa = $r.ma_khoa })
}
$fcs = if ($byF.ContainsKey("FCS")) { @($byF["FCS"]) } else { @() }

function Get-Donor([string]$fac) {
    if ($fac -in @("FIS","FAD")) { return $fcs }
    if ($fac -in @("FBA","FFA","EIB","FIDT","FIS")) {
        $o = New-Object System.Collections.ArrayList
        foreach ($k in @("FBA","EIB","FFA","FCS")) {
            if ($byF.ContainsKey($k)) { foreach ($x in $byF[$k]) { [void]$o.Add($x) } }
        }
        if ($o.Count -eq 0) { return $fcs }
        return @($o)
    }
    if ($fac -in @("BMS","MD","ME","PUD")) {
        $o = New-Object System.Collections.ArrayList
        foreach ($k in @("FP","FN","BMS")) {
            if ($byF.ContainsKey($k)) { foreach ($x in $byF[$k]) { [void]$o.Add($x) } }
        }
        if ($o.Count -eq 0) { return $fcs }
        return @($o)
    }
    if ($fac -eq "FN") {
        $o = New-Object System.Collections.ArrayList
        if ($byF.ContainsKey("FN")) { foreach ($x in $byF["FN"]) { [void]$o.Add($x) } }
        if ($byF.ContainsKey("FP")) { foreach ($x in $byF["FP"]) { [void]$o.Add($x) } }
        if ($o.Count -eq 0) { return $fcs }
        return @($o)
    }
    if ($fac -in @("FFS","FOL","FOS","FL","FTH","FTME","FJL","FKL","FFL","FCL")) {
        $o = New-Object System.Collections.ArrayList
        foreach ($k in @("FL","FOL","FOS","FBA","FCS")) {
            if ($byF.ContainsKey($k)) { foreach ($x in $byF[$k]) { [void]$o.Add($x) } }
        }
        if ($o.Count -eq 0) { return $fcs }
        return @($o)
    }
    if ($fac -in @("BCEE","EEE","VEE","MEM","MSE")) {
        $o = New-Object System.Collections.ArrayList
        foreach ($x in $fcs) { [void]$o.Add($x) }
        if ($byF.ContainsKey("BCEE")) { foreach ($x in $byF["BCEE"]) { [void]$o.Add($x) } }
        if ($byF.ContainsKey("EEE")) { foreach ($x in $byF["EEE"]) { [void]$o.Add($x) } }
        return @($o)
    }
    return $fcs
}

$expanded = @{}
foreach ($fac in ($byFac.Keys | Sort-Object)) {
    $base = New-Object System.Collections.ArrayList
    if ($byF.ContainsKey($fac)) {
        foreach ($x in $byF[$fac]) { [void]$base.Add(@{ ho_ten = $x.ho_ten; ma_khoa = $fac }) }
    }
    $donor = Get-Donor $fac
    $i = 0
    while ($base.Count -lt $MIN_PER_FAC -and $donor.Count -gt 0) {
        $d = $donor[$i % $donor.Count]
        [void]$base.Add(@{ ho_ten = $d.ho_ten; ma_khoa = $fac })
        $i++
        if ($i -gt 2000) { break }
    }
    $expanded[$fac] = @($base)
}

$lecturers = @{}

foreach ($fac in $byFac.Keys) {
    $pool = $expanded[$fac]
    if ($pool.Count -eq 0) { continue }
    $slotEmails = @()
    for ($slot = 0; $slot -lt $pool.Count; $slot++) {
        $lec = $pool[$slot]
        $email = Get-LecturerEmail $fac $slot
        $slotEmails += $email
        $lecturers[$email] = @{ ho_ten = $lec.ho_ten; ma_khoa = $fac; dept = ""; courses = New-Object System.Collections.Generic.HashSet[string] }
    }
    $courseList = $byFac[$fac]
    for ($idx = 0; $idx -lt $courseList.Count; $idx++) {
        $code = $courseList[$idx].code
        $dept = $courseList[$idx].dept
        for ($k = 0; $k -lt $LECT_PER_COURSE; $k++) {
            $slot = ($idx * $LECT_PER_COURSE + $k) % $pool.Count
            $email = $slotEmails[$slot]
            [void]$lecturers[$email].courses.Add($code)
            if ($dept -and -not $lecturers[$email].dept) { $lecturers[$email].dept = $dept }
        }
    }
}

$utf8Bom = New-Object System.Text.UTF8Encoding $true
$headerPath = Join-Path $root "giang_vien_import_header.txt"
$hdrLine = ([System.IO.File]::ReadAllText($headerPath, [System.Text.Encoding]::UTF8)).Trim()
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine($hdrLine)
foreach ($email in ($lecturers.Keys | Sort-Object)) {
    $d = $lecturers[$email]
    $codes = ($d.courses | Sort-Object) -join ","
    [void]$sb.AppendLine(($d.ho_ten, $email, $d.ma_khoa, $d.dept, $codes) -join "`t")
}
[System.IO.File]::WriteAllText($outTsv, $sb.ToString(), $utf8Bom)
Write-Host "Da ghi $($lecturers.Count) ho so -> $outTsv"
