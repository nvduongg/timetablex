# -*- coding: utf-8 -*-
"""
Sinh file nhập giảng viên (Excel) cho TimetableX — cùng schema LecturerService.importExcel.

Cần: pip install openpyxl
Chạy: python gen_giang_vien_mau.py
"""
from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

try:
    from openpyxl import Workbook
except ImportError:
    Workbook = None

ROOT = Path(__file__).resolve().parent
HOC_PHAN = ROOT / "hoc_phan_mau.tsv"
POOL = ROOT / "giang_vien_cong_khai_pool.tsv"
OUT_XLSX = ROOT / "giang_vien_import_mau.xlsx"
OUT_TSV = ROOT / "giang_vien_import_mau.tsv"
HEADER_LINE = ROOT / "giang_vien_import_header.txt"

LECTURERS_PER_COURSE = 4
MIN_LECTURERS_PER_FACULTY = 14


def lecturer_email(fac: str, slot: int) -> str:
    """Ngắn gọn, duy nhất theo khoa + chỉ số slot trong pool."""
    return f"{fac.lower()}.{slot:03d}@gv.phenikaa.vn"


def load_courses():
    by_faculty: dict[str, list[tuple[str, str]]] = defaultdict(list)
    with HOC_PHAN.open(encoding="utf-8-sig", newline="") as f:
        r = csv.reader(f, delimiter="\t")
        next(r)
        for row in r:
            if len(row) < 10:
                continue
            code, fac, dept = row[0].strip(), row[8].strip(), row[9].strip()
            if code and fac:
                by_faculty[fac].append((code, dept or ""))
    return by_faculty


def load_pool_raw():
    rows = []
    with POOL.open(encoding="utf-8", newline="") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            name, fac = parts[0].strip(), parts[1].strip()
            if name and fac:
                rows.append({"ho_ten": name, "ma_khoa": fac})
    return rows


def expand_pool(pool_rows, faculties_needed: set[str]):
    by_f: dict[str, list[dict]] = defaultdict(list)
    for p in pool_rows:
        by_f[p["ma_khoa"]].append(dict(p))

    fcs = list(by_f.get("FCS", []))

    def donor_for(fac: str) -> list[dict]:
        if fac in ("FIS", "FAD"):
            return fcs
        if fac in ("FBA", "FFA", "EIB", "FIDT", "FIS"):
            out = []
            for k in ("FBA", "EIB", "FFA", "FCS"):
                out.extend(list(by_f.get(k, [])))
            return out or fcs
        if fac in ("BMS", "MD", "ME", "PUD"):
            out = []
            for k in ("FP", "FN", "BMS"):
                out.extend(list(by_f.get(k, [])))
            return out or fcs
        if fac == "FN":
            return list(by_f.get("FN", [])) + list(by_f.get("FP", [])) or fcs
        if fac in ("FFS", "FOL", "FOS", "FL", "FTH", "FTME", "FJL", "FKL", "FFL", "FCL"):
            out = []
            for k in ("FL", "FOL", "FOS", "FBA", "FCS"):
                out.extend(list(by_f.get(k, [])))
            return out or fcs
        if fac in ("BCEE", "EEE", "VEE", "MEM", "MSE"):
            return fcs + list(by_f.get("BCEE", [])) + list(by_f.get("EEE", []))
        return fcs

    expanded: dict[str, list[dict]] = {}
    for fac in sorted(faculties_needed):
        base = list(by_f.get(fac, []))
        donor = donor_for(fac)
        i = 0
        while len(base) < MIN_LECTURERS_PER_FACULTY and donor:
            d = donor[i % len(donor)]
            base.append({"ho_ten": d["ho_ten"], "ma_khoa": fac})
            i += 1
            if i > 8000:
                break
        expanded[fac] = base
    return expanded


def assign_courses(
    by_faculty: dict[str, list[tuple[str, str]]],
    expanded: dict[str, list[dict]],
):
    lecturers: dict[str, dict] = {}

    for fac, course_rows in by_faculty.items():
        pool = expanded.get(fac, [])
        if not pool:
            continue
        # Mỗi slot trong pool = một email / một hồ sơ GV trong khoa này
        slot_emails = []
        for slot, lec in enumerate(pool):
            email = lecturer_email(fac, slot)
            slot_emails.append(email)
            lecturers[email] = {
                "ho_ten": lec["ho_ten"],
                "ma_khoa": fac,
                "dept": "",
                "courses": set(),
            }

        course_list = course_rows
        for idx, (code, dept) in enumerate(course_list):
            for k in range(LECTURERS_PER_COURSE):
                slot = (idx * LECTURERS_PER_COURSE + k) % len(pool)
                email = slot_emails[slot]
                lecturers[email]["courses"].add(code)
                if dept and not lecturers[email]["dept"]:
                    lecturers[email]["dept"] = dept

    return lecturers


def write_tsv(lecturers: dict[str, dict]):
    hdr = HEADER_LINE.read_text(encoding="utf-8").strip().split("\t")
    with OUT_TSV.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(hdr)
        for email in sorted(lecturers.keys()):
            d = lecturers[email]
            codes = ",".join(sorted(d["courses"]))
            w.writerow([d["ho_ten"], email, d["ma_khoa"], d.get("dept") or "", codes])


def write_xlsx(lecturers: dict[str, dict]):
    if Workbook is None:
        print("Không có openpyxl — chỉ ghi TSV. Chạy: pip install openpyxl")
        return
    wb = Workbook()
    ws = wb.active
    ws.title = "Lecturers"
    hdr = HEADER_LINE.read_text(encoding="utf-8").strip().split("\t")
    if len(hdr) < 5:
        hdr = hdr + ["Mã môn dạy được (Cách nhau dấu phẩy)"] * (5 - len(hdr))
    ws.append(hdr[:5])
    for email in sorted(lecturers.keys()):
        d = lecturers[email]
        codes = ",".join(sorted(d["courses"]))
        ws.append([d["ho_ten"], email, d["ma_khoa"], d.get("dept") or "", codes])
    wb.save(OUT_XLSX)


def main():
    courses_by_fac = load_courses()
    fac_need = set(courses_by_fac.keys())
    pool = load_pool_raw()
    expanded = expand_pool(pool, fac_need)
    lecturers = assign_courses(courses_by_fac, expanded)
    write_tsv(lecturers)
    write_xlsx(lecturers)
    print(f"Đã ghi {len(lecturers)} hồ sơ giảng viên → {OUT_TSV} và {OUT_XLSX}")


if __name__ == "__main__":
    main()
