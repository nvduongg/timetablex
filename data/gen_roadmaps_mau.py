# -*- coding: utf-8 -*-
"""Sinh roadmap mẫu — 3 cột đúng CurriculumService.generateRoadmapTemplate.

Khóa (cohort): K16, K19 — cùng nội dung pool môn, tên file phân biệt khóa khi import CTĐT.
- roadmap_mau_<MÃ>_<K>.tsv: **12 học kỳ**, tổng TC mỗi kỳ trong **[8, 16]** (theo hoc_phan_mau.tsv).
- roadmap_mau_<MÃ>_<K>_hk8.tsv: 8 học kỳ (chia theo số môn, không ràng TC/kỳ).

FCS / FP: đọc pool từ file K16 hiện có rồi **ghi lại** K16/K19 với phân bổ 12 kỳ thỏa 8–16 TC/kỳ.

Chạy: python gen_roadmaps_mau.py  (hoặc docker run ... python gen_roadmaps_mau.py)
"""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
COHORTS = ("K16", "K19")
HP = ROOT / "hoc_phan_mau.tsv"
HEADER_12 = [
    "Học kỳ (1-12)",
    "Mã học phần",
    "Tên học phần (gợi ý, không bắt buộc)",
]
HEADER_8 = [
    "Học kỳ (1-8)",
    "Mã học phần",
    "Tên học phần (gợi ý, không bắt buộc)",
]


def load_courses():
    by_pref = {}
    with HP.open(encoding="utf-8-sig", newline="") as f:
        r = csv.reader(f, delimiter="\t")
        next(r)
        for row in r:
            code, name = row[0], row[1]
            pref = code.split("-")[0]
            by_pref.setdefault(pref, []).append((code, name))
    return by_pref


def write_roadmap(filename, rows, header_row):
    out = ROOT / filename
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(header_row)
        w.writerows(rows)
    print(out.name, "rows", len(rows))


def pool_from_roadmap_tsv(path: Path):
    """Đọc file roadmap 3 cột đã có → list (mã, tên) theo thứ tự, không trùng mã."""
    seen = set()
    out = []
    with path.open(encoding="utf-8-sig", newline="") as f:
        r = csv.reader(f, delimiter="\t")
        next(r, None)
        for row in r:
            if len(row) < 2:
                continue
            code = row[1].strip()
            if not code or code in seen:
                continue
            seen.add(code)
            name = row[2].strip() if len(row) > 2 else ""
            out.append((code, name))
    return out


def uniq_by_code(*sequences):
    """Nối nhiều list (code, name), bỏ trùng mã — giữ thứ tự ưu tiên block trước."""
    seen = set()
    out = []
    for seq in sequences:
        for pair in seq:
            c = pair[0]
            if c in seen:
                continue
            seen.add(c)
            out.append(pair)
    return out


def load_hp_map():
    m = {}
    with HP.open(encoding="utf-8-sig", newline="") as f:
        r = csv.reader(f, delimiter="\t")
        next(r)
        for row in r:
            m[row[0]] = float(row[2])
    return m


def main():
    hp_map = load_hp_map()
    p = load_courses()

    fl4 = [
        ("FL-1185", "Tiếng Anh Y khoa cơ bản"),
        ("FL-1180", "Critical thinking bằng tiếng Anh"),
        ("FL-1184", "Viết học thuật tiếng Anh"),
        ("FL-1179", "Dịch — Biên dịch cơ bản"),
    ]
    fol3 = [
        ("FOL-1525", "Pháp luật đại cương"),
        ("FOL-1517", "Lý luận Nhà nước và Pháp luật"),
        ("FOL-1519", "Luật Doanh nghiệp"),
    ]
    fba_extra = [
        ("FBA-1404", "Nguyên lý quản trị"),
        ("FBA-1415", "Quản trị marketing"),
        ("FBA-1416", "Tài chính doanh nghiệp cho QT KD"),
        ("FBA-1418", "Data-driven decision making"),
    ]

    specs = [
        (
            "MD",
            uniq_by_code(
                p["MD"],
                p["BMS"][:9],
                p["FPH"][:7],
                p["ME"][:5],
                fl4,
                fol3,
            ),
        ),
        (
            "FN",
            uniq_by_code(
                p["FN"],
                p["BMS"][:14],
                p["FPH"][:12],
                fl4,
                fol3,
                [("FOL-1529", "Luật Thương mại")],
            ),
        ),
        (
            "EEE",
            uniq_by_code(
                p["EEE"],
                p["BCEE"][:13],
                p["VEE"][:6],
                fl4,
                fol3,
                fba_extra[:3],
            ),
        ),
        (
            "FIDT",
            uniq_by_code(
                p["FIDT"],
                p["FCS"],
                p["FBA"][:10],
                fl4,
                fol3,
            ),
        ),
        (
            "FTH",
            uniq_by_code(
                p["FTH"],
                p["FBA"][:12],
                p["EIB"][:8],
                fl4,
                fol3,
                [("FOL-1526", "Đàm phán và soạn thảo hợp đồng")],
            ),
        ),
        (
            "MEM",
            uniq_by_code(
                p["MEM"],
                p["BCEE"][:14],
                p["EEE"][:5],
                fl4,
                fol3,
                fba_extra[:2],
            ),
        ),
        (
            "EIB",
            uniq_by_code(
                p["EIB"],
                p["FBA"][:15],
                p["FFA"][:7],
                p["FIS"][:6],
                fl4,
                fol3,
            ),
        ),
        (
            "PUD",
            uniq_by_code(
                p["PUD"],
                p["BMS"][:12],
                p["FPH"][:10],
                fl4,
                fol3,
            ),
        ),
        (
            "FFA",
            uniq_by_code(
                p["FFA"],
                p["FBA"],
                p["FIS"][:8],
                fl4,
                fol3,
            ),
        ),
        (
            "FAD",
            uniq_by_code(
                p["FAD"],
                p["FCS"],
                p["FIS"][:8],
                fl4,
                fol3,
            ),
        ),
        (
            "MSE",
            uniq_by_code(
                p["MSE"],
                p["BCEE"][:13],
                p["MEM"][:4],
                p["EEE"][:4],
                fl4,
                fol3,
                fba_extra[:1],
            ),
        ),
        (
            "FTME",
            uniq_by_code(
                p["FTME"],
                p["BMS"][:12],
                p["FPH"][:8],
                p["FP"][:6],
                fl4,
                fol3,
            ),
        ),
        (
            "VEE",
            uniq_by_code(
                p["VEE"],
                p["BCEE"][:14],
                p["MEM"][:4],
                p["EEE"][:4],
                fl4,
                fol3,
                fba_extra[:2],
            ),
        ),
        (
            "ME",
            uniq_by_code(
                p["ME"],
                p["BMS"][:12],
                p["FPH"][:10],
                fl4,
                fol3,
            ),
        ),
    ]

    for cohort in COHORTS:
        for code, pool in specs:
            rows12 = distribute_by_credits(pool, hp_map, n_sem=12, lo=8.0, hi=16.0)
            write_roadmap(
                f"roadmap_mau_{code}_{cohort}.tsv",
                rows12,
                HEADER_12,
            )
            write_roadmap(
                f"roadmap_mau_{code}_{cohort}_hk8.tsv",
                distribute(8, pool),
                HEADER_8,
            )

    # FCS / FP: pool từ file K16 cũ → ghi lại 12 kỳ (8–16 TC/kỳ) cho K16 & K19; hk8 theo số môn
    for st in ("FCS", "FP"):
        k16 = ROOT / f"roadmap_mau_{st}_K16.tsv"
        if not k16.exists():
            continue
        pool_st = pool_from_roadmap_tsv(k16)
        rows12 = distribute_by_credits(pool_st, hp_map, n_sem=12, lo=8.0, hi=16.0)
        for cohort in COHORTS:
            write_roadmap(
                f"roadmap_mau_{st}_{cohort}.tsv",
                rows12,
                HEADER_12,
            )
        write_roadmap(
            f"roadmap_mau_{st}_K19_hk8.tsv",
            distribute(8, pool_st),
            HEADER_8,
        )
        write_roadmap(
            f"roadmap_mau_{st}_K16_hk8.tsv",
            distribute(8, pool_st),
            HEADER_8,
        )
        print(
            f"roadmap_mau_{st}_K16.tsv + roadmap_mau_{st}_K19.tsv rows {len(rows12)} "
            "(12 kỳ, 8–16 TC/kỳ)"
        )

    # In tổng TC từng file roadmap mẫu (K16 / K19, 12 HK và 8 HK)
    printed = []
    for cohort in COHORTS:
        printed.extend(ROOT.glob(f"roadmap_mau_*_{cohort}.tsv"))
        printed.extend(ROOT.glob(f"roadmap_mau_*_{cohort}_hk8.tsv"))
    for fn in sorted(set(printed)):
        tot = 0.0
        with fn.open(encoding="utf-8-sig", newline="") as f:
            rr = csv.reader(f, delimiter="\t")
            next(rr)
            for row in rr:
                tot += hp_map.get(row[1].strip(), 0)
        print(f"  TC≈ {tot:.0f}  {fn.name}")


def integer_targets_n(total: int, n_sem: int, lo: int, hi: int) -> list:
    """Chia total thành n_sem số nguyên, mỗi số trong [lo, hi], tổng đúng bằng total."""
    if total < n_sem * lo or total > n_sem * hi:
        raise ValueError(
            f"Tổng {total} TC không thể chia {n_sem} kỳ với mỗi kỳ [{lo},{hi}] TC"
        )
    t = [lo] * n_sem
    rem = total - n_sem * lo
    guard = 0
    while rem > 0:
        i = guard % n_sem
        if t[i] < hi:
            t[i] += 1
            rem -= 1
        guard += 1
        if guard > n_sem * (hi - lo + 1) * 20:
            raise RuntimeError("integer_targets_n: không phân được mục tiêu TC/kỳ")
    if sum(t) != total:
        raise RuntimeError("integer_targets_n: sai tổng")
    return t


def distribute_by_credits(pool, hp_map, n_sem=12, lo=8.0, hi=16.0):
    """Phân pool vào đúng n_sem học kỳ; tổng TC mỗi kỳ trong [lo, hi] (dữ liệu TC từ hp_map)."""
    lo, hi = float(lo), float(hi)
    items = []
    for code, name in pool:
        cr = float(hp_map.get(code, 0) or 0)
        if cr <= 0:
            cr = 3.0
        if cr > hi + 1e-9:
            raise ValueError(f"Môn {code}: {cr} TC > max {hi} TC/kỳ")
        items.append((code, name, cr))
    total_f = sum(x[2] for x in items)
    total = int(round(total_f))
    if abs(total_f - total) > 0.01:
        raise ValueError(f"Tổng TC không nguyên: {total_f}")

    if total < n_sem * lo - 0.01 or total > n_sem * hi + 0.01:
        raise ValueError(
            f"Tổng {total} TC không nằm trong [{int(n_sem * lo)}, {int(n_sem * hi)}] — "
            f"không thể chia {n_sem} kỳ mỗi kỳ {lo:.0f}–{hi:.0f} TC"
        )

    integer_targets_n(total, n_sem, int(lo), int(hi))

    sums = [0.0] * n_sem
    bins = [[] for _ in range(n_sem)]
    for code, name, cr in items:
        cand = [s for s in range(n_sem) if sums[s] + cr <= hi + 1e-9]
        if not cand:
            raise RuntimeError(
                f"Không còn học kỳ nào chứa được môn {code} ({cr:g} TC) mà không vượt {hi} TC/kỳ"
            )
        s = min(
            cand,
            key=lambda x: (0 if sums[x] < lo - 1e-9 else 1, sums[x], x),
        )
        bins[s].append((code, name))
        sums[s] += cr

    _rebalance_bins(bins, sums, hp_map, n_sem, lo, hi)

    for s in range(n_sem):
        if sums[s] < lo - 1e-6 or sums[s] > hi + 1e-6:
            raise RuntimeError(
                f"Sau cân bằng: kỳ {s + 1} có {sums[s]:g} TC (yêu cầu [{lo:g}, {hi:g}])"
            )

    rows = []
    for s in range(n_sem):
        for code, name in bins[s]:
            rows.append([str(s + 1), code, name])
    return rows


def _rebalance_bins(bins, sums, hp_map, n_sem, lo, hi):
    """Hoán đổi môn giữa các kỳ để mọi kỳ nằm trong [lo, hi] nếu có thể."""
    for _ in range(n_sem * 80):
        bad_lo = [s for s in range(n_sem) if sums[s] < lo - 1e-6]
        bad_hi = [s for s in range(n_sem) if sums[s] > hi + 1e-6]
        if not bad_lo and not bad_hi:
            return
        moved = False
        for h in range(n_sem):
            for l in range(n_sem):
                if h == l:
                    continue
                for j, (code, name) in enumerate(list(bins[h])):
                    cr = float(hp_map.get(code, 0) or 0)
                    nh, nl = sums[h] - cr, sums[l] + cr
                    if not (
                        nh >= lo - 1e-6
                        and nh <= hi + 1e-6
                        and nl >= lo - 1e-6
                        and nl <= hi + 1e-6
                    ):
                        continue
                    old_v = _violation_score(sums, n_sem, lo, hi)
                    if old_v <= 0:
                        continue
                    new_sums = sums[:]
                    new_sums[h], new_sums[l] = nh, nl
                    new_v = _violation_score(new_sums, n_sem, lo, hi)
                    if new_v < old_v - 1e-9:
                        bins[h].pop(j)
                        bins[l].append((code, name))
                        sums[h], sums[l] = nh, nl
                        moved = True
                        break
                if moved:
                    break
            if moved:
                break
        if not moved:
            return


def _violation_score(sums, n_sem, lo, hi):
    v = 0.0
    for s in range(n_sem):
        x = sums[s]
        if x < lo:
            v += (lo - x) ** 2
        elif x > hi:
            v += (x - hi) ** 2
    return v


def distribute(n_sem, pool):
    """Chia đều pool vào n_sem theo số môn (dùng cho khung 8 kỳ)."""
    pool = list(pool)
    n = len(pool)
    base, rem = divmod(n, n_sem)
    rows = []
    idx = 0
    for s in range(1, n_sem + 1):
        take = base + (1 if s <= rem else 0)
        for _ in range(take):
            if idx >= n:
                break
            code, name = pool[idx]
            rows.append([str(s), code, name])
            idx += 1
        if idx >= n:
            break
    while idx < n:
        rows.append([str(n_sem), pool[idx][0], pool[idx][1]])
        idx += 1
    return rows


if __name__ == "__main__":
    main()
