from __future__ import annotations

import json
import sys
from datetime import datetime
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.db import db_cursor  # noqa: E402


def split_slots(value: str) -> list[str]:
    value = (value or "").strip()
    if not value or value == "无":
        return []
    return [slot.strip() for slot in value.replace(";", "；").split("；") if slot.strip()]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: restore_wear_hourly_from_export.py wear_35d_hourly_data.json")

    data_path = Path(sys.argv[1])
    data = json.loads(data_path.read_text(encoding="utf-8"))
    child_id = data["child_id"]
    prescribed_hours = float(data["prescribed_hours"])
    daily_rows = data["daily_rows"]
    hourly_rows = data["hourly_rows"]
    hourly_by_date: dict[str, list[dict]] = {}
    for row in hourly_rows:
        hourly_by_date.setdefault(row["date"], []).append(row)

    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            UPDATE children
            SET prescribed_hours = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (prescribed_hours, child_id),
        )

        for row in daily_rows:
            record_date = row["date"]
            worn_hours = round(float(row["worn_hours"]), 1)
            hourly_records = [
                {
                    "hour_index": item["hour_index"],
                    "hour_start": item["time_slot"].split("-")[0],
                    "hour_end": item["time_slot"].split("-")[-1],
                    "worn": item["worn"] == "是",
                    "worn_hours": float(item["worn_hours"]),
                }
                for item in hourly_by_date.get(record_date, [])
            ]
            intervals_payload = {
                "source": "virtual_hourly_test_data",
                "scenario": row["scenario"],
                "notes": row["note"],
                "gap_slots": split_slots(row["non_wear_slots"]),
                "effective_intervals": split_slots(row["wear_slots"]),
                "hourly_records": hourly_records,
                "non_wear_hours": float(row["non_wear_hours"]),
                "prescribed_hours": prescribed_hours,
                "sample_interval_minutes": 60,
                "restored_at": datetime.utcnow().isoformat(timespec="seconds") + "Z",
            }
            cur.execute(
                """
                INSERT INTO wear_records
                    (id, child_id, record_date, worn_hours, prescribed_hours, is_compliant, intervals_json, synced_at)
                VALUES
                    (%s, %s, %s, %s, %s, %s, CAST(%s AS JSON), NOW())
                ON DUPLICATE KEY UPDATE
                    worn_hours = VALUES(worn_hours),
                    prescribed_hours = VALUES(prescribed_hours),
                    is_compliant = VALUES(is_compliant),
                    intervals_json = VALUES(intervals_json),
                    synced_at = VALUES(synced_at)
                """,
                (
                    f"wear-hourly-{uuid5(NAMESPACE_URL, f'{child_id}:{record_date}').hex[:24]}",
                    child_id,
                    record_date,
                    worn_hours,
                    prescribed_hours,
                    worn_hours >= prescribed_hours,
                    json.dumps(intervals_payload, ensure_ascii=False),
                ),
            )

    print(f"restored {len(daily_rows)} wear rows for {child_id}")


if __name__ == "__main__":
    main()
