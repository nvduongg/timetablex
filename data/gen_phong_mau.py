# -*- coding: utf-8 -*-
"""Sinh phong_mau_200.tsv — khớp RoomService.importExcel (3 cột: Tên phòng, Sức chứa, Loại phòng).

Loại hợp lệ: LT PM TN SB XT BV DN ONLINE (uppercase khi import).
Chạy: python gen_phong_mau.py
"""
import csv
from pathlib import Path


def main():
    out = Path(__file__).resolve().parent / "phong_mau_200.tsv"
    rows = []

    # LT (55)
    rows.append(("Hội trường nhỏ A (Tầng 1)", 280, "LT"))
    rows.append(("Hội trường B - Khu Giảng đường", 320, "LT"))
    for i in range(1, 54):
        b = 1 + (i - 1) % 8
        f = 1 + (i - 1) % 5
        num = 100 + (i % 19)
        cap = 35 + (i * 7) % 95
        if cap < 40:
            cap = 40
        rows.append((f"GD-A{b}-T{f}-{num:03d}", cap, "LT"))

    # PM (32)
    for i in range(1, 33):
        cap = 28 + (i * 5) % 23
        if i <= 16:
            rows.append((f"PM-IT-L{i:02d}", cap, "PM"))
        else:
            rows.append((f"PM-Đồ họa-{i - 16:02d}", cap, "PM"))

    # TN (28)
    tn_labs = [
        "Hóa học",
        "Vật lý",
        "Sinh học",
        "Vi sinh",
        "Dược lý",
        "Giải phẫu",
        "Sinh hóa",
        "Điện sinh",
        "Quang phổ",
        "HPLC",
        "PCR",
        "Tế bào",
        "Mô học",
        "Ký sinh",
        "Miễn dịch",
        "Dinh dưỡng",
        "Phẩm chất",
        "Thực phẩm",
        "Môi trường",
        "Vi học",
        "Địa chất",
        "Thủy văn",
        "Điện tử",
        "Viễn thông",
        "Y sinh",
        "Xét nghiệm",
        "Hình ảnh",
        "Phục hồi",
    ]
    for i, lab in enumerate(tn_labs):
        cap = 16 + (i * 3) % 25
        rows.append((f"TN-{lab}-{i + 1:02d}", cap, "TN"))

    # SB (18)
    sb = [
        ("Sân bóng đá cỏ nhân tạo - A", 80),
        ("Sân bóng đá cỏ nhân tạo - B", 80),
        ("Sân bóng rổ ngoài trời - 1", 40),
        ("Sân bóng rổ ngoài trời - 2", 40),
        ("Sân tennis - 1", 24),
        ("Sân tennis - 2", 24),
        ("Sân cầu lông ngoài trời", 32),
        ("Nhà thi đấu đa năng (TDTT)", 200),
        ("Hồ bơi - khối thể chất", 60),
        ("Sân điền kinh (khu vực A)", 120),
        ("Phòng Gym - TDTT-1", 45),
        ("Phòng Gym - TDTT-2", 45),
        ("Phòng Yoga / Aerobic", 35),
        ("Sân võ thuật (Judo/Karate)", 50),
        ("Sân pickleball - 1", 28),
        ("Khu CLB thể thao (môn tự chọn)", 100),
        ("Sân bóng chuyền ngoài trời", 36),
        ("Đường chạy bộ / đi bộ (zone)", 150),
    ]
    for name, cap in sb:
        rows.append((name, cap, "SB"))

    # XT (22)
    tags = [
        "Cơ khí",
        "Điện",
        "Điện tử",
        "Ô tô",
        "Hóa công",
        "CNC",
        "Hàn",
        "Đúc",
        "Tiện",
        "Phay",
    ]
    for i in range(1, 23):
        cap = 18 + (i * 4) % 32
        t = tags[(i - 1) % len(tags)]
        rows.append((f"XT-{t}-{i:02d}", cap, "XT"))

    # BV (18)
    bv = [
        ("BV-Đa khoa TT - Khoa Nội tổng quát", 30),
        ("BV-Đa khoa TT - Khoa Ngoại", 28),
        ("BV-Đa khoa TT - Khoa Nhi", 25),
        ("BV-Đa khoa TT - Cấp cứu", 20),
        ("BV RHM - Phòng khám Nội nha", 18),
        ("BV RHM - Phòng Phục hình", 16),
        ("BV Sản - Buồng thực hành", 22),
        ("Trung tâm Y học cổ truyền - Lâm sàng", 24),
        ("Phòng khám Đông y - TT", 20),
        ("Trạm y tế trường (lâm sàng nhẹ)", 15),
        ("Khoa Dược lâm sàng - BV liên kết", 20),
        ("Khoa PHCN - Buồng tập", 18),
        ("Khoa XN - Khu lấy mẫu", 20),
        ("Khoa Chẩn đoán hình ảnh - BV", 14),
        ("BV Việt Pháp - Thực tập sinh viên", 26),
        ("Trung tâm Hộ sinh - Thực hành", 22),
        ("Phòng tiểu phẫu BV - TT", 12),
        ("Khoa Tâm thần - Thực tập (giới hạn)", 16),
    ]
    for name, cap in bv:
        rows.append((name, cap, "BV"))

    # DN (15)
    dn = [
        ("DN-FPT Software - Lab thực tập", 25),
        ("DN-Viettel R&D - Khu thực tập", 20),
        ("DN-VinBigdata - Phòng dự án", 18),
        ("DN-Ngân hàng TMCP - Chi nhánh TT", 30),
        ("DN-Khách sạn Mường Thanh - Du lịch", 35),
        ("DN-Nhà máy TH True Milk - Tham quan/TH", 40),
        ("DN-Bệnh viện tư - Luân phiên SV", 22),
        ("DN-Trung tâm Logistics - Kho thực tập", 28),
        ("DN-Studio truyền thông - Quay dựng", 20),
        ("DN-Công ty Kiểm toán - Team thực tập", 24),
        ("DN-Startup Hub - Không gian làm việc", 30),
        ("DN-Nhà máy ô tô - Line lắp ráp", 25),
        ("DN-Trại thực tập Nông nghiệp công nghệ cao", 45),
        ("DN-Bảo tàng / Di sản - Hướng dẫn viên", 50),
        ("DN-Cảng/ICD - Quan sát chuỗi cung ứng", 35),
    ]
    for name, cap in dn:
        rows.append((name, cap, "DN"))

    # ONLINE (12)
    on = [
        ("ONLINE - Zoom Room Pool 01", 500),
        ("ONLINE - Zoom Room Pool 02", 500),
        ("ONLINE - Microsoft Teams - Chung", 400),
        ("ONLINE - Google Meet - Pool A", 350),
        ("ONLINE - LMS Moodle (tiết ảo)", 999),
        ("ONLINE - BigBlueButton - Phòng 1", 200),
        ("ONLINE - BigBlueButton - Phòng 2", 200),
        ("ONLINE - ClassIn - Khoa NN", 300),
        ("ONLINE - Webex - Hội thảo", 250),
        ("ONLINE - Discord Stage (CNTT)", 150),
        ("ONLINE - Hyflex Studio (kết hợp)", 120),
        ("ONLINE - Bài giảng ghi sẵn (async)", 2000),
    ]
    for name, cap in on:
        rows.append((name, cap, "ONLINE"))

    assert len(rows) == 200, len(rows)

    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(["Tên phòng", "Sức chứa", "Loại phòng"])
        for name, cap, typ in rows:
            w.writerow([name, cap, typ])

    print(out, "rows:", len(rows))


if __name__ == "__main__":
    main()
