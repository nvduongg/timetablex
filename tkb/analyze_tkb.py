import pandas as pd
from collections import defaultdict
import glob
import os

file_pattern = "c:/Users/duong/sciences/timetablex/tkb/*.csv"
for file_path in glob.glob(file_pattern):
    try:
        print(f"\n--- Analyzing: {os.path.basename(file_path)} ---")
        # Find where header is
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        header_idx = 0
        for i, line in enumerate(lines[:10]):
            if 'Mã HP' in line or 'Mã Học Phần' in line or 'Mã LHP' in line:
                header_idx = i
                break
                
        df = pd.read_csv(file_path, skiprows=header_idx, encoding='utf-8')
        
        # Clean columns
        df.columns = [str(c).strip() for c in df.columns]
        if 'Mã HP' not in df.columns:
            print("Skipping, unknown format")
            continue
            
        print(f"Total rows: {len(df)}")
        
        # 1. Distribution of classes by day
        if 'Thứ' in df.columns:
            days = df['Thứ'].value_counts()
            print("\nClasses by day of week:")
            print(days.to_string())
        
        # 2. Number of evening classes (Tiết BĐ >= 13)
        if 'Tiết BĐ' in df.columns:
            df['Tiết BĐ'] = pd.to_numeric(df['Tiết BĐ'], errors='coerce')
            evening_classes = df[df['Tiết BĐ'] >= 13]
            print(f"\nNumber of evening classes (Tiết >= 13): {len(evening_classes)}")
            
        # 3. Fragmented schedules for student groups
        # Look for the same "Nhóm KS" having both Tiết 1 and Tiết 10 on the same day
        if 'Nhóm KS' in df.columns and 'Thứ' in df.columns and 'Tiết BĐ' in df.columns:
            group_day_shifts = defaultdict(lambda: defaultdict(set))
            for _, row in df.iterrows():
                groups = str(row['Nhóm KS']).split(';')
                day = row['Thứ']
                shift = row['Tiết BĐ']
                if pd.isna(day) or pd.isna(shift) or str(shift).upper() == 'NAN': continue
                for g in groups:
                    g = g.strip()
                    if g and g.lower() != 'nan':
                        group_day_shifts[g][day].add(shift)
            
            fragmented_count = 0
            for g, days in group_day_shifts.items():
                for d, shifts in days.items():
                    if any(s <= 3 for s in shifts) and any(s >= 10 for s in shifts):
                        fragmented_count += 1
                        break # count group once
            print(f"\nNumber of student groups with fragmented schedule (Ca 1 + Ca 4+ on same day): {fragmented_count}")

            # Overloaded days for students
            overloaded_groups = 0
            for g, days in group_day_shifts.items():
                for d, shifts in days.items():
                    if len(shifts) >= 3:
                        overloaded_groups += 1
                        break
            print(f"Number of student groups studying >= 3 shifts/day: {overloaded_groups}")

        # 4. Heavy load for lecturers
        if 'Giảng Viên 1' in df.columns:
            lec_day_shifts = defaultdict(lambda: defaultdict(set))
            for _, row in df.iterrows():
                lec = str(row['Giảng Viên 1']).strip()
                day = row['Thứ']
                shift = row['Tiết BĐ']
                if pd.isna(day) or pd.isna(shift) or lec == '' or lec.lower() == 'nan': continue
                lec_day_shifts[lec][day].add(shift)
                
            heavy_lecs = 0
            evening_lecs = 0
            for lec, days in lec_day_shifts.items():
                for d, shifts in days.items():
                    if len(shifts) >= 3:
                        heavy_lecs += 1
                        break
                for d, shifts in days.items():
                    if any(s >= 13 for s in shifts):
                        evening_lecs += 1
                        break
            print(f"\nNumber of lecturers teaching >= 3 shifts in a single day: {heavy_lecs}")
            print(f"Number of lecturers teaching evening shifts: {evening_lecs}")
            
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
