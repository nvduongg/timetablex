# -*- coding: utf-8 -*-
"""Xây dựng hoc_phan_mau.tsv — khớp CourseService.importExcel (11 cột như template API).

- Gộp: hoc_phan_mau_550.tsv (nếu có) + hoc_phan_bo_sung_khung_ctdt.tsv (nếu có).
- Bổ sung môn tự chọn / chuyên đề và **Đồ án — Khóa luận tốt nghiệp** cho mỗi khoa.
- Giữ nguyên mã học phần đã có để lộ trình CTĐT hiện tại không vỡ mã.

Chạy: python gen_hoc_phan_mau.py
"""
import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "hoc_phan_mau.tsv"
LEGACY = ROOT / "hoc_phan_mau_550.tsv"
SUPPLEMENT = ROOT / "hoc_phan_bo_sung_khung_ctdt.tsv"

FACULTY_META = [
    ("FP", "FP-BM1"),
    ("FN", "FN-BM1"),
    ("BMS", "BMS-BM1"),
    ("ME", "ME-BM1"),
    ("PUD", "PUD-BM1"),
    ("MD", "MD-BM1"),
    ("FTME", "FTME-BM1"),
    ("FPH", "FPH-BM1"),
    ("FOS", "FOS-BM1"),
    ("FL", "FL-BM1"),
    ("FKL", "FKL-BM1"),
    ("FJL", "FJL-BM1"),
    ("FFL", "FFL-BM1"),
    ("FCL", "FCL-BM1"),
    ("MEM", "MEM-BM1"),
    ("BCEE", "BCEE-BM1"),
    ("EEE", "EEE-BM1"),
    ("MSE", "MSE-BM1"),
    ("VEE", "VEE-BM1"),
    ("FTH", "FTH-BM1"),
    ("EIB", "EIB-BM1"),
    ("FBA", "FBA-BM1"),
    ("FFA", "FFA-BM1"),
    ("FIS", "FIS-BM1"),
    ("FCS", "FCS-BM1"),
    ("FAD", "FAD-BM1"),
    ("FFS", "FFS-BM1"),
    ("FOL", "FOL-BM1"),
    ("FIDT", "FIDT-BM1"),
]

HEADER = [
    "Mã HP",
    "Tên Học Phần",
    "Tổng TC",
    "TC Lý thuyết",
    "TC Thực hành",
    "TC Tự học",
    "Hình thức (OFFLINE/ONLINE_ELEARNING/ONLINE_COURSERA/HYBRID)",
    "Loại phòng (LT / PM / TN / SB / XT / BV / DN / ONLINE)",
    "Mã Khoa",
    "Mã Bộ môn (Tùy chọn)",
    "Mã Khoa dùng chung (cách nhau dấu phẩy, VD: IT,CS,MATH)",
]

# Môn mở rộng theo khoa (để dự trữ thiết kế CTĐT); mỗi khoa 8 dòng
DOMAIN_EXTRA = {
    "FP": [
        "Thương mại và phân phối dược phẩm",
        "Dược lâm sàng nâng cao nội trú",
        "Nano và hệ mang dược",
        "Dược phẩm sinh học hiện đại",
        "GLP — GMP trong QC/QA",
        "Khởi nghiệp công ty dược",
        "Đọc hiểu tài liệu dược tiếng Anh",
        "Quản lý chuỗi lạnh dược phẩm",
    ],
    "FCS": [
        "Kiến trúc phần mềm hướng miền",
        "Kỹ thuật reverse engineering cơ bản",
        "CSDL đồ thị và ứng dụng",
        "Ngôn ngữ Rust nền tảng",
        "Tối ưu hiệu năng phần mềm",
        "Đảm bảo chất lượng mã nguồn",
        "Compiler và trình thông dịch — giới thiệu",
        "Lập trình song song cơ bản",
    ],
    "FAD": [
        "Học liên kết và meta-learning",
        "Federated learning — giới thiệu",
        "Triển khai mô hình tại biên mạng",
        "Đánh giá độ tin cậy của AI",
        "AI cho xử lý tín hiệu y sinh",
        "Bảo mật và tấn công vào mô hình ML",
        "AutoML — giới thiệu",
        "Tối ưu hóa suy luận inference",
    ],
    "EEE": [
        "Hệ thống nhúng an toàn chức năng",
        "Thiết kế nguồn thấp tiêu thụ",
        "Giao thức công nghiệp thời gian thực",
        "Xử lý tín hiệu số đa tốc độ",
        "Lidar và cảm biến cho robot",
        "Độ tin cậy hệ thống điện tử",
        "Chuẩn hóa EMC/EMI cơ bản",
        "Đồ án tích hợp điện — cơ",
    ],
    "MD": [
        "Lâm sàng tích hợp theo hệ cơ quan",
        "Y học dựa trên bằng chứng nâng cao",
        "Lâm sàng từ xa và y học kỹ thuật số",
        "Cấp cứu ngoại viện",
        "Y học thể thao — chấn thương thường gặp",
        "Dinh dưỡng lâm sàng",
        "Dược lâm sàng cho bác sĩ đa khoa",
        "Luật hành nghề và trách nhiệm pháp lý",
    ],
    "FBA": [
        "Chiến lược doanh nghiệp Bài học case study",
        "Design thinking trong kinh doanh",
        "Phân tích đối thủ cạnh tranh",
        "Định giá sản phẩm và dịch vụ",
        "Venture capital và gọi vốn seed",
        "Vận hành startup",
        "OKR và KPI trong doanh nghiệp",
        "Đạo đức dữ liệu kinh doanh",
    ],
    "BCEE": [
        "Sinh học tổng hợp — giới thiệu",
        "Kỹ thuật nuôi cấy tế bào",
        "Phân tích dữ liệu omics cơ bản",
        "Công nghệ vi sinh công nghiệp",
        "Xử lý bùn và chất thải rắn nguy hại",
        "Quan trắc môi trường đại trà",
        "Kinh tế tuần hoàn và tái chế",
        "Đánh giá sống còn chủng vi sinh",
    ],
}


def default_extras(fac: str):
    return [
        f"Chuyên đề tự chọn 1 — {fac}",
        f"Chuyên đề tự chọn 2 — {fac}",
        f"Seminar xu hướng ngành — {fac}",
        f"Dự án nhóm chuyên ngành — {fac}",
        f"Kỹ năng nghề nghiệp và hội nhập — {fac}",
        f"Hội thảo khoa học và viết báo cáo — {fac}",
        f"Pháp lý ngành — nâng cao — {fac}",
        f"Thực tập chuyên sâu — {fac}",
    ]


def capstone_rows(fac: str, dept: str, seq: int):
    """Đồ án / KLTN + seminar hướng dẫn — mã liên tiếp."""
    rows = [
        [
            f"{fac}-{seq}",
            "Đồ án / Khóa luận tốt nghiệp",
            10,
            1,
            2,
            7,
            "OFFLINE",
            "DA",
            fac,
            dept,
            "",
        ],
        [
            f"{fac}-{seq + 1}",
            "Seminar hướng dẫn đồ án tốt nghiệp",
            2,
            2,
            0,
            0,
            "OFFLINE",
            "LT",
            fac,
            dept,
            "",
        ],
    ]
    return rows, seq + 2


def parse_max_seq(codes: set[str]) -> int:
    m = 0
    for c in codes:
        p = c.split("-", 1)
        if len(p) == 2 and p[1].isdigit():
            m = max(m, int(p[1]))
    return m


def read_tsv_data(path: Path) -> list[list]:
    if not path.is_file():
        return []
    with path.open(encoding="utf-8-sig", newline="") as f:
        r = csv.reader(f, delimiter="\t")
        rows = list(r)
    if not rows:
        return []
    if rows[0] and rows[0][0] == HEADER[0]:
        return rows[1:]
    return rows


def row_to_writable(r: list) -> list:
    out = []
    for i, x in enumerate(r):
        if i in (2, 3, 4, 5) and x != "" and re.match(r"^\d+(\.\d+)?$", str(x).strip()):
            v = float(str(x).strip().replace(",", "."))
            out.append(int(v) if v == int(v) else v)
        else:
            out.append(str(x).strip() if x is not None else "")
    while len(out) < 11:
        out.append("")
    return out[:11]


def main():
    merged: list[list] = []
    seen_codes: set[str] = set()

    for path in (LEGACY, SUPPLEMENT):
        for r in read_tsv_data(path):
            if not r or not r[0]:
                continue
            code = r[0].strip()
            if code in seen_codes:
                continue
            seen_codes.add(code)
            merged.append(row_to_writable(r))

    next_seq = parse_max_seq(seen_codes) + 1

    for fac, dept in FACULTY_META:
        extras = DOMAIN_EXTRA.get(fac, default_extras(fac))
        for name in extras:
            code = f"{fac}-{next_seq}"
            merged.append(
                row_to_writable(
                    [
                        code,
                        name,
                        3,
                        2,
                        1,
                        0,
                        "HYBRID",
                        "LT",
                        fac,
                        dept,
                        "",
                    ]
                )
            )
            seen_codes.add(code)
            next_seq += 1
        cap_r, next_seq = capstone_rows(fac, dept, next_seq)
        for row in cap_r:
            rw = row_to_writable(row)
            if rw[0] not in seen_codes:
                merged.append(rw)
                seen_codes.add(rw[0])

    with OUT.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(HEADER)
        for r in merged:
            w.writerow(r)

    print(OUT, "rows", len(merged), "(+ header); next_seq sau cùng =", next_seq)


if __name__ == "__main__":
    main()
