# Merge hoc_phan_mau_550 + supplement + extras + capstone -> hoc_phan_mau.tsv (UTF-8 BOM)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$legacy = Join-Path $root "hoc_phan_mau_550.tsv"
$supp = Join-Path $root "hoc_phan_bo_sung_khung_ctdt.tsv"
$out = Join-Path $root "hoc_phan_mau.tsv"

$meta = @(
    @("FP","FP-BM1"),@("FN","FN-BM1"),@("BMS","BMS-BM1"),@("ME","ME-BM1"),@("PUD","PUD-BM1"),
    @("MD","MD-BM1"),@("FTME","FTME-BM1"),@("FPH","FPH-BM1"),@("FOS","FOS-BM1"),@("FL","FL-BM1"),
    @("FKL","FKL-BM1"),@("FJL","FJL-BM1"),@("FFL","FFL-BM1"),@("FCL","FCL-BM1"),@("MEM","MEM-BM1"),
    @("BCEE","BCEE-BM1"),@("EEE","EEE-BM1"),@("MSE","MSE-BM1"),@("VEE","VEE-BM1"),@("FTH","FTH-BM1"),
    @("EIB","EIB-BM1"),@("FBA","FBA-BM1"),@("FFA","FFA-BM1"),@("FIS","FIS-BM1"),@("FCS","FCS-BM1"),
    @("FAD","FAD-BM1"),@("FFS","FFS-BM1"),@("FOL","FOL-BM1"),@("FIDT","FIDT-BM1")
)

$domainExtra = @{}
# Ten mon mo rong theo khoa (ASCII/UTF-8 don gian, tranh ky tu dac biet PowerShell)
$domainExtra["FP"] = @(
    "Thuong mai va phan phoi duoc pham","Duoc lam sang nang cao noi tru","Nano va he mang duoc",
    "Duoc pham sinh hoc hien dai","GLP GMP trong QC QA","Khoi nghiep cong ty duoc",
    "Doc hieu tai lieu duoc tieng Anh","Quan ly chuoi lanh duoc pham"
)
$domainExtra["FCS"] = @(
    "Kien truc phan mem huong mien","Ky thuat reverse engineering co ban","CSDL do thi va ung dung",
    "Ngon ngu Rust nen tang","Toi uu hieu nang phan mem","Dam bao chat luong ma nguon",
    "Compiler va thong dich gioi thieu","Lap trinh song song co ban"
)
$domainExtra["FAD"] = @(
    "Hoc lien ket va meta-learning","Federated learning gioi thieu","Trien khai mo hinh tai bien mang",
    "Danh gia do tin cay cua AI","AI cho xu ly tin hieu y sinh","Bao mat va tan cong mo hinh ML",
    "AutoML gioi thieu","Toi uu hoa suy luan inference"
)
$domainExtra["EEE"] = @(
    "He thong nhung an toan chuc nang","Thiet ke nguon thap tieu thu","Giao thuc cong nghiep thoi gian thuc",
    "Xu ly tin hieu so da toc do","Lidar va cam bien cho robot","Do tin cay he thong dien tu",
    "Chuan hoa EMC EMI co ban","Do an tich hop dien co"
)
$domainExtra["MD"] = @(
    "Lam sang tich hop theo he co quan","Y hoc dua tren bang chung nang cao","Lam sang tu xa va y hoc ky thuat so",
    "Cap cuu ngoai vien","Y hoc the thao chan thuong thuong gap","Dinh duong lam sang",
    "Duoc lam sang cho bac si da khoa","Luat hanh nghe va trach nhiem phap ly"
)
$domainExtra["FBA"] = @(
    "Chien luoc doanh nghiep case study","Design thinking trong kinh doanh","Phan tich doi thu canh tranh",
    "Dinh gia san pham va dich vu","Venture capital va goi von seed","Van hanh startup",
    "OKR va KPI trong doanh nghiep","Dao du lieu kinh doanh"
)
$domainExtra["BCEE"] = @(
    "Sinh hoc tong hop gioi thieu","Ky thuat nuoi cay te bao","Phan tich du lieu omics co ban",
    "Cong nghe vi sinh cong nghiep","Xu ly bun chat thai ran nguy hai","Quan trac moi truong dai tra",
    "Kinh te tuan hoan va tai che","Danh gia song con chung vi sinh"
)

function Get-DefaultExtras([string]$f) {
    $a = @()
    for ($i = 1; $i -le 8; $i++) { $a += "Chuyen de tu chon $i - $f" }
    return $a
}

function Get-MaxSeq($rows) {
    $m = 0
    foreach ($r in $rows) {
        if ($r -match '^[A-Z0-9]+-(\d+)\t') {
            $n = [int]$Matches[1]
            if ($n -gt $m) { $m = $n }
        }
    }
    return $m
}

$utf8bom = New-Object System.Text.UTF8Encoding $true
$lines = New-Object System.Collections.Generic.List[string]
Get-Content -LiteralPath $legacy -Encoding UTF8 | ForEach-Object { [void]$lines.Add($_) }

$seen = @{}
foreach ($ln in $lines) {
    if ($ln -match '^([^\t]+)\t') { $seen[$Matches[1]] = $true }
}

Get-Content -LiteralPath $supp -Encoding UTF8 | Select-Object -Skip 1 | ForEach-Object {
    $ln = $_
    if ($ln -match '^([^\t]+)\t') {
        $c = $Matches[1]
        if (-not $seen.ContainsKey($c)) {
            [void]$lines.Add($ln)
            $seen[$c] = $true
        }
    }
}

$next = (Get-MaxSeq $lines) + 1

foreach ($pair in $meta) {
    $fac = $pair[0]
    $dept = $pair[1]
    $extras = if ($domainExtra.ContainsKey($fac)) { $domainExtra[$fac] } else { Get-DefaultExtras $fac }
    foreach ($name in $extras) {
        [void]$lines.Add("$fac-$next`t$name`t3`t2`t1`t0`tHYBRID`tLT`t$fac`t$dept`t")
        $next++
    }
    [void]$lines.Add("$fac-$next`tDo an / Khoa luan tot nghiep`t10`t1`t2`t7`tOFFLINE`tDA`t$fac`t$dept`t")
    $next++
    [void]$lines.Add("$fac-$next`tSeminar huong dan do an tot nghiep`t2`t2`t0`t0`tOFFLINE`tLT`t$fac`t$dept`t")
    $next++
}

[System.IO.File]::WriteAllLines($out, $lines, $utf8bom)
Write-Host "Wrote" $out "line count" $lines.Count
