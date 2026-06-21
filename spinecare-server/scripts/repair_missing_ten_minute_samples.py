from __future__ import annotations

import argparse
import json
from datetime import date, datetime, timezone
from pathlib import Path

from app.db import db_cursor
from app.main import cloud_correct_missing_ten_minute_samples, json_value, normalized_hour_record


def iso_value(value):
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    return value


def repair_payload(row: dict) -> tuple[dict, float, int, list[dict]] | None:
    intervals = json_value(row.get("intervals_json"))
    hourly_records = [
        record
        for record in (
            normalized_hour_record(record, row["record_date"])
            for record in (intervals.get("hourly_records") or [])
            if isinstance(record, dict)
        )
        if record
    ]
    corrected_records, corrections = cloud_correct_missing_ten_minute_samples(hourly_records)
    if not corrections:
        return None

    intervals["hourly_records"] = corrected_records
    intervals["sample_count"] = sum(int(record.get("sample_count", 0)) for record in corrected_records)
    intervals["worn_count"] = sum(int(record.get("worn_count", 0)) for record in corrected_records)
    intervals["cloud_correction_version"] = 1
    existing_corrections = intervals.get("cloud_corrections") if isinstance(intervals.get("cloud_corrections"), list) else []
    intervals["cloud_corrections"] = existing_corrections + corrections
    worn_hours = round(min(24.0, sum(float(record.get("worn_hours", 0.0)) for record in corrected_records)), 1)
    prescribed = float(row.get("prescribed_hours") or 0)
    is_compliant = int(prescribed > 0 and worn_hours >= prescribed)
    return intervals, worn_hours, is_compliant, corrections


def main() -> None:
    parser = argparse.ArgumentParser(description="Repair cloud wear rows that have exactly one missing 10-minute sample in an hour.")
    parser.add_argument("--child-id", default=None)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    params: list[object] = []
    where = ""
    if args.child_id:
        where = "WHERE child_id = %s"
        params.append(args.child_id)
    limit_sql = ""
    if args.limit > 0:
        limit_sql = " LIMIT %s"
        params.append(args.limit)

    with db_cursor(commit=args.apply) as cur:
        cur.execute(
            f"""
            SELECT id, child_id, record_date, worn_hours, prescribed_hours, is_compliant,
                   intervals_json, synced_at
            FROM wear_records
            {where}
            ORDER BY record_date ASC, synced_at ASC
            {limit_sql}
            """,
            tuple(params),
        )
        rows = cur.fetchall()

        changed: list[dict] = []
        for row in rows:
            result = repair_payload(row)
            if not result:
                continue
            intervals, worn_hours, is_compliant, corrections = result
            changed.append(
                {
                    "id": row["id"],
                    "child_id": row["child_id"],
                    "record_date": iso_value(row["record_date"]),
                    "old_worn_hours": float(row["worn_hours"]),
                    "new_worn_hours": worn_hours,
                    "corrections": corrections,
                    "original_intervals_json": json_value(row.get("intervals_json")),
                }
            )
            if args.apply:
                cur.execute(
                    """
                    UPDATE wear_records
                    SET worn_hours = %s,
                        is_compliant = %s,
                        intervals_json = CAST(%s AS JSON),
                        synced_at = NOW()
                    WHERE id = %s
                    """,
                    (
                        worn_hours,
                        is_compliant,
                        json.dumps(intervals, ensure_ascii=False),
                        row["id"],
                    ),
                )

    backup_path = None
    if changed:
        backup_path = Path("/tmp") / f"spinecaremom_missing_10min_repair_{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
        backup_path.write_text(json.dumps(changed, ensure_ascii=False, indent=2), encoding="utf-8")

    print(
        json.dumps(
            {
                "apply": args.apply,
                "scanned": len(rows),
                "changed": len(changed),
                "backup_path": str(backup_path) if backup_path else None,
                "changed_rows": [
                    {
                        "id": item["id"],
                        "child_id": item["child_id"],
                        "record_date": item["record_date"],
                        "old_worn_hours": item["old_worn_hours"],
                        "new_worn_hours": item["new_worn_hours"],
                        "correction_count": len(item["corrections"]),
                    }
                    for item in changed
                ],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
