# -*- coding: utf-8 -*-
"""Sinh lớp biên chế mẫu khớp import backend (AdministrativeClassService.importExcel).

Cột Excel (sheet đầu, hàng 0 = tiêu đề) — trùng generateTemplate():
  A Mã lớp | B Tên lớp | C Khóa | D Sĩ số | E Mã ngành

Điều kiện import:
  - Mã ngành (cột E) phải trùng Major.code đã có trong DB (không phải Mã Khoa).
  - Khóa (cột C) phải tồn tại trong danh mục Niên khóa (entity Cohort, findByCode).
    File này dùng K16–K19: cần khai báo 4 mã này trong hệ thống trước khi import.

Phân bổ: 57 mã ngành × (44×9 + 13×8 lớp) = 500 lớp.
Chạy: python gen_lop_bienche.py
"""
import csv
from pathlib import Path

# (major_code,) — major_code = giá trị cột "Mã ngành" trong file import (= Major.code trong DB).
# Nếu DB của bạn dùng mã 7 số (VD 7480201), đổi cột E trong CSV sau khi sinh hoặc sửa list này.
PROGRAMS = [
    "MED1",
    "PHA1",
    "DEN1",
    "NUR1",
    "MIW",
    "MTT1",
    "RET1",
    "RTS1",
    "FTME",
    "BMS",
    "HM1",
    "ICT1",
    "ICT2",
    "ICT3",
    "ICT4",
    "ICT5",
    "FIDT1",
    "FIDT2",
    "FIDT3",
    "FIDT4",
    "FIDT5",
    "FIDT6",
    "EEE1",
    "EEE2",
    "EEE3",
    "EEE4",
    "EEE-AI",
    "MEM1",
    "MEM2",
    "VEE1",
    "VEE2",
    "VEE3",
    "MSE1",
    "MSE-AI",
    "MSE-IC",
    "BIO1",
    "CHE1",
    "FBE1",
    "FBE4",
    "FBE8",
    "FBE2",
    "FBE3",
    "FBE5",
    "FBE9",
    "FBE6",
    "FBE7",
    "FTS1",
    "FTS2",
    "FTS3",
    "FTS4",
    "FLE1",
    "FLK1",
    "FLC1",
    "FLJ1",
    "FLF1",
    "FOS1",
    "FOL1",
]

TOTAL_TARGET = 500
COHORTS = ["K16", "K17", "K18", "K19"]
SECTIONS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")


def class_counts(n_programs: int, total: int) -> list[int]:
    base, rem = divmod(total, n_programs)
    return [base + (1 if i < rem else 0) for i in range(n_programs)]


def main():
    out = Path(__file__).resolve().parent / "lop_bienche_mau_500.tsv"
    counts = class_counts(len(PROGRAMS), TOTAL_TARGET)
    assert sum(counts) == TOTAL_TARGET

    rows = []
    h = 0
    for j, major_code in enumerate(PROGRAMS):
        n = counts[j]
        for i in range(n):
            cohort = COHORTS[i % len(COHORTS)]
            sec = SECTIONS[i // len(COHORTS)]
            class_id = f"{major_code}-{cohort}{sec}"
            size = 32 + ((h + j * 3 + i * 7) % 24)
            rows.append(
                {
                    "Mã lớp": class_id,
                    "Tên lớp": f"Lớp {major_code} {cohort}{sec}",
                    "Khóa": cohort,
                    "Sĩ số": size,
                    "Mã ngành": major_code,
                }
            )
            h += 1

    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["Mã lớp", "Tên lớp", "Khóa", "Sĩ số", "Mã ngành"],
            delimiter="\t",
        )
        w.writeheader()
        w.writerows(rows)

    print(out)
    print("rows:", len(rows))


if __name__ == "__main__":
    main()
