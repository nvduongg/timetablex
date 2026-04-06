# -*- coding: utf-8 -*-
"""Chuẩn hóa cột Loại phòng theo Room.java (backend):
  LT  — Giảng đường lý thuyết
  PM  — Phòng máy tính
  TN  — Phòng thí nghiệm khoa học
  SB  — Sân bãi / nhà thể chất (chỉ môn thể dục-thể thao thực sự)
  XT  — Xưởng thực hành (cơ khí, ô tô, chế tạo…)
  BV  — Bệnh viện / lâm sàng
  DN  — Thực tập tại doanh nghiệp (ngoài trường)
Chạy: python fix_hoc_phan_room_semantics.py
"""
from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TSV = ROOT / "hoc_phan_mau.tsv"

# Mã HP -> loại phòng đúng nghĩa (ghi đè)
OVERRIDES: dict[str, str] = {
    # FP — DN/SB/XT/PM sai nghĩa
    "FP-1001": "LT",  # Dược lâm sàng: lớp lý thuyết/tình huống, không phải TT DN
    "FP-1002": "LT",
    "FP-1004": "LT",  # Marketing: giảng đường / seminar
    "FP-1006": "LT",
    "FP-1009": "PM",  # Thông tin dược, kê đơn: máy tính
    "FP-1011": "TN",  # Bào chế: lab
    "FP-1013": "DN",  # Thực tập cơ sở SX dược
    "FP-1014": "TN",
    "FP-1015": "TN",  # Kiểm nghiệm: lab
    # FN
    "FN-1020": "LT",  # Y học cơ sở điều dưỡng: chủ yếu lý thuyết
    "FN-1022": "LT",
    "FN-1025": "LT",  # Hộ sinh cơ sở
    "FN-1026": "LT",
    "FN-1032": "LT",  # Luật Y tế
    "FN-1033": "LT",  # Hộ sinh chuyển dạ: lý thuyết + mô phỏng lớp
    "FN-1037": "LT",
    "FN-1038": "LT",
    # BMS
    "BMS-1041": "LT",
    "BMS-1046": "TN",  # TH sinh lý động vật
    "BMS-1047": "LT",  # Pháp y cơ sở
    "BMS-1050": "TN",
    "BMS-1051": "TN",  # Mô học
    "BMS-1052": "TN",  # Giải phẫu (prosection/cadaver lab)
    "BMS-1053": "TN",
    "BMS-1054": "LT",  # Di truyền y học: chủ yếu lớp
    "BMS-1057": "TN",
    # ME
    "ME-1058": "LT",  # Seminar
    "ME-1059": "XT",  # Bảo trì TB y tế: xưởng
    "ME-1061": "TN",  # Siêu âm: lab mô phỏng/thiết bị
    "ME-1073": "LT",
    "ME-1074": "TN",
    "ME-1075": "TN",
    "ME-1072": "TN",  # Vi sinh lâm sàng: có TH lab
    # PUD
    "PUD-1085": "LT",
    "PUD-1087": "TN",
    "PUD-1089": "TN",
    # MD
    "MD-1097": "LT",
    "MD-1101": "LT",
    "MD-1102": "BV",
    "MD-1106": "LT",
    "MD-1108": "BV",
    "MD-1109": "BV",
    "MD-1113": "BV",
    "MD-1114": "BV",
    "MD-1612": "BV",
    # FTME
    "FTME-1115": "BV",
    "FTME-1126": "LT",
    "FTME-1128": "LT",
    "FTME-1129": "LT",
    "FTME-1131": "LT",
    "FTME-1132": "TN",
    # FPH
    "FPH-1137": "LT",
    "FPH-1139": "PM",
    "FPH-1141": "PM",
    "FPH-1143": "LT",
    "FPH-1144": "PM",
    # FOS / FL / ngôn ngữ — SB/XT/DN sai
    "FOS-1161": "LT",
    "FOS-1163": "LT",
    "FOS-1170": "LT",
    "FL-1172": "LT",
    "FL-1176": "LT",
    "FL-1188": "LT",
    "FKL-1192": "LT",
    "FKL-1193": "LT",
    "FKL-1196": "LT",
    "FKL-1197": "LT",
    "FKL-1208": "LT",
    "FJL-1211": "LT",
    "FJL-1220": "LT",
    "FJL-1226": "LT",
    "FFL-1232": "LT",
    "FFL-1233": "LT",
    "FFL-1236": "LT",
    "FFL-1237": "LT",
    "FFL-1238": "LT",
    "FFL-1242": "LT",
    "FCL-1249": "LT",
    "FCL-1250": "LT",
    "FCL-1251": "LT",
    "FCL-1254": "LT",
    "FCL-1261": "LT",
    "FCL-1265": "LT",
    # MEM
    "MEM-1271": "PM",  # CAD-CAE
    "MEM-1278": "XT",
    "MEM-1280": "LT",
    "MEM-1282": "PM",
    # BCEE
    "BCEE-1292": "TN",
    "BCEE-1294": "LT",
    "BCEE-1296": "TN",
    "BCEE-1299": "LT",
    "BCEE-1301": "TN",
    # EEE
    "EEE-1308": "PM",
    "EEE-1320": "PM",
    "EEE-1321": "TN",
    # MSE
    "MSE-1332": "TN",
    "MSE-1333": "LT",
    "MSE-1334": "PM",
    "MSE-1335": "TN",
    "MSE-1336": "TN",
    # VEE — giữ XT cho garage/vật liệu ô tô; lý thuyết / mô hình
    "VEE-1347": "LT",
    "VEE-1355": "LT",
    "VEE-1360": "XT",
    # FTH
    "FTH-1362": "PM",
    "FTH-1366": "DN",
    "FTH-1371": "LT",
    "FTH-1375": "DN",
    "FTH-1376": "LT",
    # EIB
    "EIB-1384": "LT",
    "EIB-1386": "LT",
    "EIB-1387": "LT",
    "EIB-1392": "LT",
    "EIB-1394": "LT",
    # FBA / FFA
    "FBA-1404": "LT",
    "FBA-1405": "LT",
    "FBA-1409": "LT",
    "FBA-1410": "LT",
    "FBA-1411": "LT",
    "FBA-1413": "LT",
    "FBA-1415": "LT",
    "FBA-1418": "PM",
    "FFA-1424": "LT",
    "FFA-1436": "LT",
    # FIS / FCS / FAD
    "FIS-1438": "PM",
    "FIS-1442": "PM",
    "FIS-1443": "PM",
    "FIS-1445": "PM",
    "FIS-1447": "PM",
    "FIS-1449": "PM",
    "FCS-1459": "PM",
    "FCS-1460": "PM",
    "FCS-1465": "PM",
    "FCS-1467": "PM",
    "FCS-1468": "PM",
    "FCS-1473": "PM",
    "FCS-1475": "PM",
    "FAD-1476": "LT",
    "FAD-1489": "PM",
    "FAD-1491": "PM",
    # FFS
    "FFS-1496": "LT",
    "FFS-1502": "TN",
    "FFS-1505": "LT",
    "FFS-1506": "TN",
    "FFS-1511": "TN",
    "FFS-1512": "TN",
    # FOL / FIDT
    "FOL-1517": "LT",
    "FOL-1519": "LT",
    "FOL-1521": "LT",
    "FOL-1522": "LT",
    "FOL-1523": "LT",
    "FOL-1526": "LT",
    "FOL-1528": "LT",
    "FOL-1529": "LT",
    "FOL-1530": "LT",
    "FOL-1531": "LT",
    "FIDT-1533": "LT",
    "FIDT-1537": "LT",
    "FIDT-1542": "LT",
    "FIDT-1547": "PM",
    "FIDT-1549": "PM",
}


def main() -> None:
    rows: list[list[str]] = []
    with TSV.open(encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter="\t")
        for row in reader:
            rows.append(row)

    if not rows:
        raise SystemExit("empty file")

    header = rows[0]
    try:
        idx_rt = header.index(
            "Loại phòng (LT / PM / TN / SB / XT / BV / DN / ONLINE / DA)"
        )
        idx_lm = header.index(
            "Hình thức (OFFLINE/ONLINE_ELEARNING/ONLINE_COURSERA/HYBRID)"
        )
    except ValueError:
        raise SystemExit("cannot find room type column in header")

    changed = 0
    for i in range(1, len(rows)):
        row = rows[i]
        if len(row) <= idx_rt:
            continue
        code = row[0].strip()
        lm = row[idx_lm].strip() if len(row) > idx_lm else ""
        if lm in ("ONLINE_ELEARNING", "ONLINE_COURSERA"):
            continue
        if code in OVERRIDES:
            newv = OVERRIDES[code]
            if row[idx_rt] != newv:
                row[idx_rt] = newv
                changed += 1

    with TSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerows(rows)

    print(f"Updated {changed} rows in {TSV.name}")


if __name__ == "__main__":
    main()
