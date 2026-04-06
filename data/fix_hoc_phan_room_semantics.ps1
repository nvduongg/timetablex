# Chuẩn hóa Loại phòng theo Room.java — cột 8 (index 7), mã HP cột 1 (index 0)
param(
    [string[]]$TsvPaths = @()
)
$ErrorActionPreference = 'Stop'
if ($TsvPaths.Count -eq 0) {
    $TsvPaths = @(
        (Join-Path $PSScriptRoot 'hoc_phan_mau.tsv'),
        (Join-Path $PSScriptRoot 'hoc_phan_mau_550.tsv')
    ) | Where-Object { Test-Path -LiteralPath $_ }
}

$map = @{
    'FP-1001'='LT'; 'FP-1002'='LT'; 'FP-1004'='LT'; 'FP-1006'='LT'; 'FP-1009'='PM'; 'FP-1011'='TN'
    'FP-1013'='DN'; 'FP-1014'='TN'; 'FP-1015'='TN'
    'FN-1020'='LT'; 'FN-1022'='LT'; 'FN-1025'='LT'; 'FN-1026'='LT'; 'FN-1032'='LT'; 'FN-1033'='LT'
    'FN-1037'='LT'; 'FN-1038'='LT'
    'BMS-1041'='LT'; 'BMS-1046'='TN'; 'BMS-1047'='LT'; 'BMS-1050'='TN'; 'BMS-1051'='TN'; 'BMS-1052'='TN'
    'BMS-1053'='TN'; 'BMS-1054'='LT'; 'BMS-1057'='TN'
    'ME-1058'='LT'; 'ME-1059'='XT'; 'ME-1061'='TN'; 'ME-1072'='TN'; 'ME-1073'='LT'; 'ME-1074'='TN'; 'ME-1075'='TN'
    'PUD-1085'='LT'; 'PUD-1087'='TN'; 'PUD-1089'='TN'
    'MD-1097'='LT'; 'MD-1101'='LT'; 'MD-1102'='BV'; 'MD-1106'='LT'; 'MD-1108'='BV'; 'MD-1109'='BV'
    'MD-1113'='BV'; 'MD-1114'='BV'; 'MD-1612'='BV'
    'FTME-1115'='BV'; 'FTME-1126'='LT'; 'FTME-1128'='LT'; 'FTME-1129'='LT'; 'FTME-1131'='LT'; 'FTME-1132'='TN'
    'FPH-1137'='LT'; 'FPH-1139'='PM'; 'FPH-1141'='PM'; 'FPH-1143'='LT'; 'FPH-1144'='PM'
    'FOS-1161'='LT'; 'FOS-1163'='LT'; 'FOS-1170'='LT'
    'FL-1172'='LT'; 'FL-1176'='LT'; 'FL-1188'='LT'
    'FKL-1192'='LT'; 'FKL-1193'='LT'; 'FKL-1196'='LT'; 'FKL-1197'='LT'; 'FKL-1208'='LT'
    'FJL-1211'='LT'; 'FJL-1220'='LT'; 'FJL-1226'='LT'
    'FFL-1232'='LT'; 'FFL-1233'='LT'; 'FFL-1236'='LT'; 'FFL-1237'='LT'; 'FFL-1238'='LT'; 'FFL-1242'='LT'
    'FCL-1249'='LT'; 'FCL-1250'='LT'; 'FCL-1251'='LT'; 'FCL-1254'='LT'; 'FCL-1261'='LT'; 'FCL-1265'='LT'
    'MEM-1271'='PM'; 'MEM-1278'='XT'; 'MEM-1280'='LT'; 'MEM-1282'='PM'
    'BCEE-1292'='TN'; 'BCEE-1294'='LT'; 'BCEE-1296'='TN'; 'BCEE-1299'='LT'; 'BCEE-1301'='TN'
    'EEE-1308'='PM'; 'EEE-1320'='PM'; 'EEE-1321'='TN'
    'MSE-1332'='TN'; 'MSE-1333'='LT'; 'MSE-1334'='PM'; 'MSE-1335'='TN'; 'MSE-1336'='TN'
    'VEE-1347'='LT'; 'VEE-1355'='LT'; 'VEE-1360'='XT'
    'FTH-1362'='PM'; 'FTH-1366'='DN'; 'FTH-1371'='LT'; 'FTH-1375'='DN'; 'FTH-1376'='LT'
    'EIB-1384'='LT'; 'EIB-1386'='LT'; 'EIB-1387'='LT'; 'EIB-1392'='LT'; 'EIB-1394'='LT'
    'FBA-1404'='LT'; 'FBA-1405'='LT'; 'FBA-1409'='LT'; 'FBA-1410'='LT'; 'FBA-1411'='LT'; 'FBA-1413'='LT'
    'FBA-1415'='LT'; 'FBA-1418'='PM'; 'FFA-1424'='LT'; 'FFA-1436'='LT'
    'FIS-1438'='PM'; 'FIS-1442'='PM'; 'FIS-1443'='PM'; 'FIS-1445'='PM'; 'FIS-1447'='PM'; 'FIS-1449'='PM'
    'FCS-1459'='PM'; 'FCS-1460'='PM'; 'FCS-1465'='PM'; 'FCS-1467'='PM'; 'FCS-1468'='PM'; 'FCS-1473'='PM'; 'FCS-1475'='PM'
    'FAD-1476'='LT'; 'FAD-1489'='PM'; 'FAD-1491'='PM'
    'FFS-1496'='LT'; 'FFS-1502'='TN'; 'FFS-1505'='LT'; 'FFS-1506'='TN'; 'FFS-1511'='TN'; 'FFS-1512'='TN'
    'FOL-1517'='LT'; 'FOL-1519'='LT'; 'FOL-1521'='LT'; 'FOL-1522'='LT'; 'FOL-1523'='LT'; 'FOL-1526'='LT'
    'FOL-1528'='LT'; 'FOL-1529'='LT'; 'FOL-1530'='LT'; 'FOL-1531'='LT'
    'FIDT-1533'='LT'; 'FIDT-1537'='LT'; 'FIDT-1542'='LT'; 'FIDT-1547'='PM'; 'FIDT-1549'='PM'
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
        $code = $cols[0].Trim()
        $lm = $cols[6].Trim()
        # Hình thức online: để fix_online_learning_room_online.ps1 quyết định (thường là ONLINE; BV/DN giữ chỗ TT)
        $skipMap = ($lm -eq 'ONLINE_ELEARNING' -or $lm -eq 'ONLINE_COURSERA')
        if (-not $skipMap -and $map.ContainsKey($code)) {
            $nv = $map[$code]
            if ($cols[7] -ne $nv) { $changed++; $cols[7] = $nv }
        }
        $out.Add(($cols -join "`t")) | Out-Null
    }
    Set-Content -LiteralPath $path -Value $out -Encoding UTF8
    Write-Host "Updated $changed rows in $(Split-Path $path -Leaf)"
}
