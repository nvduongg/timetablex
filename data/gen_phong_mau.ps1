# Sinh phong_mau_200.tsv — khớp RoomService (Tên phòng, Sức chứa, Loại phòng)
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'phong_mau_200.tsv'
$utf8bom = New-Object System.Text.UTF8Encoding $true
$sw = New-Object System.IO.StreamWriter($path, $false, $utf8bom)
$sw.WriteLine("Tên phòng`tSức chứa`tLoại phòng")

function Write-Room([string]$name, [int]$cap, [string]$type) {
  $sw.WriteLine("$name`t$cap`t$type")
}

# --- LT: Giảng đường (55) ---
for ($i = 1; $i -le 55; $i++) {
  $b = 1 + (($i - 1) % 8)
  $f = 1 + (($i - 1) % 5)
  $num = 100 + ($i % 19)
  $cap = 35 + (($i * 7) % 95)
  if ($cap -lt 40) { $cap = 40 }
  if ($i -eq 1) { Write-Room "Hội trường nhỏ A (Tầng 1)" 280 "LT" ; continue }
  if ($i -eq 2) { Write-Room "Hội trường B - Khu Giảng đường" 320 "LT" ; continue }
  Write-Room ("GD-A{0}-T{1}-{2:D3}" -f $b, $f, $num) $cap "LT"
}

# --- PM: Phòng máy (32) ---
for ($i = 1; $i -le 32; $i++) {
  $cap = 28 + (($i * 5) % 23)
  if ($i -le 16) {
    Write-Room ("PM-IT-L{0:D2}" -f $i) $cap "PM"
  } else {
    Write-Room ("PM-Đồ họa-{0:D2}" -f ($i - 16)) $cap "PM"
  }
}

# --- TN: Thí nghiệm (28) ---
$tnLabs = @(
  @('Hóa học'),@('Vật lý'),@('Sinh học'),@('Vi sinh'),@('Dược lý'),@('Giải phẫu'),
  @('Sinh hóa'),@('Điện sinh'),@('Quang phổ'),@('HPLC'),@('PCR'),@('Tế bào'),
  @('Mô học'),@('Ký sinh'),@('Miễn dịch'),@('Dinh dưỡng'),@('Phẩm chất'),@('Thực phẩm'),
  @('Môi trường'),@('Vi học'),@('Địa chất'),@('Thủy văn'),@('Điện tử'),@('Viễn thông'),
  @('Y sinh'),@('Xét nghiệm'),@('Hình ảnh'),@('Phục hồi')
)
for ($i = 0; $i -lt 28; $i++) {
  $cap = 16 + (($i * 3) % 25)
  Write-Room ("TN-{0}-{1:D2}" -f $tnLabs[$i][0], ($i + 1)) $cap "TN"
}

# --- SB: Sân bãi / thể chất (18) ---
$sb = @(
  @('Sân bóng đá cỏ nhân tạo - A', 80, 'SB'),
  @('Sân bóng đá cỏ nhân tạo - B', 80, 'SB'),
  @('Sân bóng rổ ngoài trời - 1', 40, 'SB'),
  @('Sân bóng rổ ngoài trời - 2', 40, 'SB'),
  @('Sân tennis - 1', 24, 'SB'),
  @('Sân tennis - 2', 24, 'SB'),
  @('Sân cầu lông ngoài trời', 32, 'SB'),
  @('Nhà thi đấu đa năng (TDTT)', 200, 'SB'),
  @('Hồ bơi - khối thể chất', 60, 'SB'),
  @('Sân điền kinh (khu vực A)', 120, 'SB'),
  @('Phòng Gym - TDTT-1', 45, 'SB'),
  @('Phòng Gym - TDTT-2', 45, 'SB'),
  @('Phòng Yoga / Aerobic', 35, 'SB'),
  @('Sân võ thuật (Judo/Karate)', 50, 'SB'),
  @('Sân pickleball - 1', 28, 'SB'),
  @('Khu CLB thể thao (môn tự chọn)', 100, 'SB'),
  @('Sân bóng chuyền ngoài trời', 36, 'SB'),
  @('Đường chạy bộ / đi bộ (zone)', 150, 'SB')
)
foreach ($x in $sb) { Write-Room $x[0] $x[1] $x[2] }

# --- XT: Xưởng thực hành (22) ---
for ($i = 1; $i -le 22; $i++) {
  $cap = 18 + (($i * 4) % 32)
  $tags = @('Cơ khí','Điện','Điện tử','Ô tô','Hóa công','CNC','Hàn','Đúc','Tiện','Phay')
  $t = $tags[($i - 1) % $tags.Length]
  Write-Room ("XT-{0}-{1:D2}" -f $t, $i) $cap "XT"
}

# --- BV: Bệnh viện / cơ sở lâm sàng (18) ---
$bv = @(
  @('BV-Đa khoa TT - Khoa Nội tổng quát', 30, 'BV'),
  @('BV-Đa khoa TT - Khoa Ngoại', 28, 'BV'),
  @('BV-Đa khoa TT - Khoa Nhi', 25, 'BV'),
  @('BV-Đa khoa TT - Cấp cứu', 20, 'BV'),
  @('BV RHM - Phòng khám Nội nha', 18, 'BV'),
  @('BV RHM - Phòng Phục hình', 16, 'BV'),
  @('BV Sản - Buồng thực hành', 22, 'BV'),
  @('Trung tâm Y học cổ truyền - Lâm sàng', 24, 'BV'),
  @('Phòng khám Đông y - TT', 20, 'BV'),
  @('Trạm y tế trường (lâm sàng nhẹ)', 15, 'BV'),
  @('Khoa Dược lâm sàng - BV liên kết', 20, 'BV'),
  @('Khoa PHCN - Buồng tập', 18, 'BV'),
  @('Khoa XN - Khu lấy mẫu', 20, 'BV'),
  @('Khoa Chuẩn đoán hình ảnh - BV', 14, 'BV'),
  @('BV Việt Pháp - Thực tập sinh viên', 26, 'BV'),
  @('Trung tâm Hộ sinh - Thực hành', 22, 'BV'),
  @('Phòng tiểu phẫu BV - TT', 12, 'BV'),
  @('Khoa Tâm thần - Thực tập (giới hạn)', 16, 'BV')
)
foreach ($x in $bv) { Write-Room $x[0] $x[1] $x[2] }

# --- DN: Doanh nghiệp / ngoài trường (15) ---
$dn = @(
  @('DN-FPT Software - Lab thực tập', 25, 'DN'),
  @('DN-Viettel R&D - Khu thực tập', 20, 'DN'),
  @('DN-VinBigdata - Phòng dự án', 18, 'DN'),
  @('DN-Ngân hàng TMCP - Chi nhánh TT', 30, 'DN'),
  @('DN-Khách sạn Mường Thanh - Du lịch', 35, 'DN'),
  @('DN-Nhà máy TH True Milk - Tham quan/TH', 40, 'DN'),
  @('DN-Bệnh viện tư - Luân phiên SV', 22, 'DN'),
  @('DN-Trung tâm Logistics - Kho thực tập', 28, 'DN'),
  @('DN-Studio truyền thông - Quay dựng', 20, 'DN'),
  @('DN-Công ty Kiểm toán - Team thực tập', 24, 'DN'),
  @('DN-Startup Hub - Không gian làm việc', 30, 'DN'),
  @('DN-Nhà máy ô tô - Line lắp ráp', 25, 'DN'),
  @('DN-Trại thực tập Nông nghiệp công nghệ cao', 45, 'DN'),
  @('DN-Bảo tàng / Di sản - Hướng dẫn viên', 50, 'DN'),
  @('DN-Cảng/ICD - Quan sát chuỗi cung ứng', 35, 'DN')
)
foreach ($x in $dn) { Write-Room $x[0] $x[1] $x[2] }

# --- ONLINE (12) ---
$on = @(
  @('ONLINE - Zoom Room Pool 01', 500, 'ONLINE'),
  @('ONLINE - Zoom Room Pool 02', 500, 'ONLINE'),
  @('ONLINE - Microsoft Teams - Chung', 400, 'ONLINE'),
  @('ONLINE - Google Meet - Pool A', 350, 'ONLINE'),
  @('ONLINE - LMS Moodle (tiết ảo)', 999, 'ONLINE'),
  @('ONLINE - BigBlueButton - Phòng 1', 200, 'ONLINE'),
  @('ONLINE - BigBlueButton - Phòng 2', 200, 'ONLINE'),
  @('ONLINE - ClassIn - Khoa NN', 300, 'ONLINE'),
  @('ONLINE - Webex - Hội thảo', 250, 'ONLINE'),
  @('ONLINE - Discord Stage (CNTT)', 150, 'ONLINE'),
  @('ONLINE - Hyflex Studio (kết hợp)', 120, 'ONLINE'),
  @('ONLINE - Bài giảng ghi sẵn (async)', 2000, 'ONLINE')
)
foreach ($x in $on) { Write-Room $x[0] $x[1] $x[2] }

$sw.Close()
$n = (Get-Content $path | Measure-Object -Line).Lines - 1
Write-Host "Wrote $path lines=$n (expect 200 data + 1 header)"
