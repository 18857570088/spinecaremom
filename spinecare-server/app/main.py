from __future__ import annotations

import json
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from .db import db_cursor, ping
from .settings import settings


def u(value: str) -> str:
    return value.encode("ascii").decode("unicode_escape")


EMERGENCY_KEYWORDS = [
    u("\\u547c\\u5438\\u56f0\\u96be"),
    u("\\u5598\\u4e0d\\u4e0a\\u6c14"),
    u("\\u80f8\\u95f7\\u6c14\\u77ed"),
    u("\\u65e0\\u6cd5\\u547c\\u5438"),
    u("\\u76ae\\u80a4\\u7834\\u6e83"),
    u("\\u7834\\u76ae"),
    u("\\u6d41\\u8113"),
    u("\\u6e83\\u70c2"),
    u("\\u6c34\\u6ce1\\u7834\\u4e86"),
    u("\\u75bc\\u75db\\u6301\\u7eed"),
    u("\\u75bc\\u4e86\\u597d\\u51e0\\u5929"),
    u("\\u591c\\u91cc\\u75bc\\u9192"),
    u("\\u9ebb\\u6728"),
    u("\\u65e0\\u529b"),
    u("\\u6655\\u5012"),
    u("\\u9ad8\\u70e7"),
    u("\\u4f24\\u53e3\\u611f\\u67d3"),
]

DISCLAIMER = "本回答仅供健康科普与参考，不替代医生的诊断与医嘱；如有疑虑请及时咨询主治医生或支具师。"
DELETE_CONFIRMATION_TEXT = "删除全部数据"
DELETE_COOLING_HOURS = 24


class AskRequest(BaseModel):
    child_id: str = Field(default="demo-child")
    question: str = Field(min_length=1, max_length=500)


class SkinLogRequest(BaseModel):
    child_id: str = "demo-child"
    region: str
    status: str
    note: str | None = None
    photos: list[str] = Field(default_factory=list)


class GrowthLogRequest(BaseModel):
    child_id: str = "demo-child"
    height_cm: float
    note: str | None = None


class ImagingRequest(BaseModel):
    child_id: str = "demo-child"
    image_type: str = Field(default="X光", max_length=32)
    file_url: str | None = Field(default=None, max_length=512)
    shot_date: date
    note: str | None = None


class ReportArchiveRequest(BaseModel):
    child_id: str = Field(default="demo-child", max_length=64)
    kind: str = Field(max_length=32)
    period_start: date | None = None
    period_end: date | None = None
    payload_json: dict = Field(default_factory=dict)
    pdf_url: str | None = Field(default=None, max_length=512)


class DeleteDataRequestCreate(BaseModel):
    child_id: str = Field(default="demo-child", max_length=64)
    child_nickname: str = Field(min_length=1, max_length=32)
    backup_confirmed: bool = False
    irreversible_confirmed: bool = False
    current_child_confirmed: bool = False
    confirmation_text: str = Field(min_length=1, max_length=32)


class DeleteDataRequestExecute(BaseModel):
    confirmation_text: str = Field(min_length=1, max_length=32)


class ChildProfileUpsertRequest(BaseModel):
    child_id: str = Field(default="demo-child", max_length=64)
    phone: str | None = Field(default=None, max_length=32)
    verification_code: str | None = Field(default=None, max_length=16)
    login_method: str = Field(default="sms", max_length=32)
    consent_accepted: bool = True
    nickname: str = Field(default="朵朵", min_length=1, max_length=32)
    gender: str = Field(default="女", max_length=16)
    birth_date: date | None = None
    cobb_initial: int | None = Field(default=None, ge=0, le=120)
    curve_type: str | None = Field(default=None, max_length=32)
    risser: str | None = Field(default=None, max_length=16)
    prescribed_hours: float = Field(default=20.0, ge=0, le=24)
    brace_type: str | None = Field(default=None, max_length=32)
    first_visit_date: date | None = None
    app_project: str | None = Field(default=None, max_length=64)
    raw: dict = Field(default_factory=dict)


class DeviceWearRecord(BaseModel):
    date: date
    worn_hours: float = Field(ge=0, le=24)
    sample_count: int = Field(ge=0)
    worn_count: int = Field(ge=0)
    sample_interval_minutes: int = Field(default=10, ge=1, le=60)
    hourly_records: list[dict] = Field(default_factory=list)


class DeviceWearSyncRequest(BaseModel):
    child_id: str = "demo-child"
    device_name: str | None = None
    device_address: str | None = None
    records: list[DeviceWearRecord] = Field(default_factory=list)
    raw: dict = Field(default_factory=dict)


app = FastAPI(title=settings.app_name, version="1.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def parse_json(value):
    if value is None:
        return None
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except Exception:
        return None


def ensure_children_profile_columns(cur):
    columns = {
        "guardian_phone": "VARCHAR(32) NULL",
        "verification_code": "VARCHAR(16) NULL",
        "login_method": "VARCHAR(32) NULL",
        "consent_accepted": "BOOLEAN NOT NULL DEFAULT TRUE",
        "source_json": "JSON NULL",
        "last_login_at": "DATETIME NULL",
    }
    for name, definition in columns.items():
        cur.execute("SHOW COLUMNS FROM children LIKE %s", (name,))
        if cur.fetchone() is None:
            cur.execute(f"ALTER TABLE children ADD COLUMN {name} {definition}")


def ensure_delete_requests_table(cur):
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS delete_requests (
            id VARCHAR(64) PRIMARY KEY,
            child_id VARCHAR(64) NOT NULL,
            status VARCHAR(32) NOT NULL DEFAULT 'confirmed',
            backup_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
            requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            confirmed_at DATETIME NULL,
            scheduled_delete_at DATETIME NOT NULL,
            cancelled_at DATETIME NULL,
            completed_at DATETIME NULL,
            deleted_counts_json JSON NULL,
            request_json JSON NULL,
            INDEX idx_delete_child_status (child_id, status),
            INDEX idx_delete_child_requested (child_id, requested_at)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """
    )


def upload_file_id_from_ref(ref: str | None) -> str | None:
    if not ref:
        return None
    value = str(ref).strip()
    if not value:
        return None
    marker = "/api/v1/uploads/"
    if marker in value:
        value = value.rsplit(marker, 1)[-1]
    value = value.split("?", 1)[0].split("#", 1)[0].strip("/")
    file_id = Path(value).name
    if file_id != value or not file_id or len(file_id) > 220:
        return None
    return file_id


def delete_upload_files(refs: list[str]) -> int:
    upload_root = Path(settings.upload_dir).resolve()
    deleted = 0
    for ref in refs:
        file_id = upload_file_id_from_ref(ref)
        if not file_id:
            continue
        path = (upload_root / file_id).resolve()
        if upload_root not in path.parents and path != upload_root:
            continue
        if path.is_file():
            path.unlink()
            deleted += 1
    return deleted


def collect_upload_refs_for_child(cur, child_id: str) -> list[str]:
    refs: list[str] = []
    cur.execute("SELECT photos_json FROM skin_logs WHERE child_id = %s", (child_id,))
    for row in cur.fetchall():
        photos = parse_json(row.get("photos_json")) or []
        if isinstance(photos, list):
            refs.extend(str(item) for item in photos if item)
    cur.execute("SELECT file_url FROM imaging_files WHERE child_id = %s", (child_id,))
    refs.extend(str(row["file_url"]) for row in cur.fetchall() if row.get("file_url"))
    cur.execute("SELECT pdf_url FROM reports WHERE child_id = %s", (child_id,))
    refs.extend(str(row["pdf_url"]) for row in cur.fetchall() if row.get("pdf_url"))
    return refs


def delete_child_cloud_data(cur, child_id: str) -> dict:
    tables = [
        "ai_messages",
        "alerts",
        "reports",
        "imaging_files",
        "growth_logs",
        "skin_logs",
        "wear_records",
        "devices",
        "children",
    ]
    counts: dict[str, int] = {}
    for table in tables:
        cur.execute(f"SELECT COUNT(*) AS count FROM {table} WHERE child_id = %s" if table != "children" else "SELECT COUNT(*) AS count FROM children WHERE id = %s", (child_id,))
        counts[table] = int(cur.fetchone()["count"])
    for table in tables:
        cur.execute(f"DELETE FROM {table} WHERE child_id = %s" if table != "children" else "DELETE FROM children WHERE id = %s", (child_id,))
    return counts


def normalize_gender(value: str) -> str:
    normalized = value.strip().lower()
    if normalized in {"男", "male", "m"}:
        return "male"
    return "female"


def iso_row(row: dict) -> dict:
    result = {}
    for key, value in row.items():
        if isinstance(value, (date, datetime)):
            result[key] = value.isoformat()
        elif key.endswith("_json"):
            result[key] = parse_json(value)
        else:
            result[key] = value
    return result


def iso_wear_row(row: dict) -> dict:
    result = iso_row(row)
    intervals = result.get("intervals_json")
    record_date_text = result.get("record_date")
    if isinstance(intervals, dict) and isinstance(record_date_text, str):
        try:
            record_date_value = date.fromisoformat(record_date_text)
        except ValueError:
            record_date_value = None
        if record_date_value:
            hourly_records = [
                record
                for record in (normalized_hour_record(record, record_date_value) for record in intervals.get("hourly_records") or [])
                if record
            ]
            if hourly_records:
                intervals["hourly_records"] = hourly_records
                intervals["sample_count"] = sum(int(record.get("sample_count", 0)) for record in hourly_records)
                intervals["worn_count"] = sum(int(record.get("worn_count", 0)) for record in hourly_records)
                result["worn_hours"] = round(min(24.0, sum(float(record.get("worn_hours", 0.0)) for record in hourly_records)), 1)
                prescribed = float(result.get("prescribed_hours") or 0)
                if prescribed > 0:
                    result["is_compliant"] = int(float(result["worn_hours"]) >= prescribed)
    return result


def range_days(range_name: str) -> int:
    mapping = {
        "week": 7,
        "month": 30,
        "35d": 35,
        "all": 35,
    }
    return mapping.get(range_name, 7)


def longest_streak(rows: list[dict], prescribed_hours: float | None = None) -> int:
    longest = 0
    current = 0
    for row in sorted(rows, key=lambda item: item["record_date"]):
        is_compliant = (
            float(row["worn_hours"]) >= prescribed_hours
            if prescribed_hours is not None and prescribed_hours > 0
            else bool(row["is_compliant"])
        )
        if is_compliant:
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    return longest


def top_gap_slots(rows: list[dict]) -> list[str]:
    counter: Counter[str] = Counter()
    for row in rows:
        payload = parse_json(row.get("intervals_json")) or {}
        for slot in payload.get("gap_slots") or []:
            counter[slot] += 1
    return [slot for slot, _ in counter.most_common(2)]


def query_wear_rows(child_id: str, days: int) -> list[dict]:
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT MAX(record_date) AS max_date
            FROM wear_records
            WHERE child_id = %s
            """,
            (child_id,),
        )
        max_row = cur.fetchone()
        max_date = max_row["max_date"] if max_row else None
        if not max_date:
            return []
        start_date = max_date - timedelta(days=days - 1)
        cur.execute(
            """
            SELECT id, child_id, record_date, worn_hours, prescribed_hours, is_compliant,
                   intervals_json, synced_at
            FROM wear_records
            WHERE child_id = %s AND record_date >= %s AND record_date <= %s
            ORDER BY record_date ASC
            """,
            (child_id, start_date, max_date),
        )
        return cur.fetchall()


def prescribed_hours_for_child(child_id: str) -> float:
    with db_cursor() as cur:
        ensure_children_profile_columns(cur)
        cur.execute(
            """
            SELECT prescribed_hours
            FROM children
            WHERE id = %s
            """,
            (child_id,),
        )
        row = cur.fetchone()
    if row and row.get("prescribed_hours") is not None:
        value = round(float(row["prescribed_hours"]), 1)
        if 0 < value <= 24:
            return value
    return 20.0


def json_value(value):
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return {}
    return value if isinstance(value, dict) else {}


def ten_minute_sample_slot(raw_time: str) -> str | None:
    value = raw_time.replace("T", " ").strip()
    if len(value) < 16:
        return None
    try:
        parsed = datetime.strptime(value[:16], "%Y-%m-%d %H:%M")
    except ValueError:
        return None
    parsed = parsed.replace(minute=(parsed.minute // 10) * 10, second=0, microsecond=0)
    return parsed.strftime("%Y-%m-%d %H:%M")


def normalized_hour_record(record: dict, record_date: date) -> dict | None:
    hour_text = str(record.get("hour_start", "")).strip()
    if len(hour_text) >= 13 and hour_text[4] == "-":
        hour_start = hour_text.replace("T", " ")[:13] + ":00"
    else:
        hour_value = record.get("hour_index", str(hour_text).split(":", 1)[0])
        try:
            hour = max(0, min(23, int(hour_value)))
        except (TypeError, ValueError):
            return None
        hour_start = f"{record_date.isoformat()} {hour:02d}:00"
    try:
        worn_hours = round(max(0.0, min(1.0, float(record.get("worn_hours", 0.0)))), 1)
    except (TypeError, ValueError):
        worn_hours = 0.0
    samples_by_time: dict[str, dict] = {}
    for sample in record.get("samples") or []:
        if not isinstance(sample, dict):
            continue
        raw_time = str(sample.get("recorded_at", "")).replace("T", " ").strip()
        if len(raw_time) < 16:
            continue
        sample_time = ten_minute_sample_slot(raw_time)
        if not sample_time:
            continue
        if sample_time[:13] + ":00" != hour_start:
            continue
        previous = samples_by_time.get(sample_time)
        worn = bool(sample.get("worn", False))
        samples_by_time[sample_time] = {
            "recorded_at": sample_time,
            "worn": (previous["worn"] if previous else worn) and worn,
        }
    if samples_by_time:
        samples = [samples_by_time[key] for key in sorted(samples_by_time)]
        sample_count = min(6, len(samples))
        worn_count = min(6, sum(1 for sample in samples if sample["worn"]))
        worn_hours = round(worn_count / 6.0, 1)
    else:
        samples = []
        try:
            sample_count = max(0, min(6, int(record.get("sample_count", 6))))
        except (TypeError, ValueError):
            sample_count = 6
        try:
            worn_count = max(0, min(6, int(record.get("worn_count", round(worn_hours * 6)))))
        except (TypeError, ValueError):
            worn_count = round(worn_hours * 6)
    return {
        "hour_start": hour_start,
        "worn_hours": worn_hours,
        "sample_count": sample_count,
        "worn_count": worn_count,
        "samples": samples,
    }


def merge_hour_record(existing: dict | None, incoming: dict) -> dict:
    if not existing:
        return incoming
    existing_samples = {sample["recorded_at"]: sample for sample in existing.get("samples") or []}
    incoming_samples = {sample["recorded_at"]: sample for sample in incoming.get("samples") or []}
    if existing_samples or incoming_samples:
        merged_samples = dict(existing_samples)
        for recorded_at, sample in incoming_samples.items():
            if recorded_at in merged_samples:
                merged_samples[recorded_at] = {
                    "recorded_at": recorded_at,
                    "worn": bool(merged_samples[recorded_at].get("worn", False)) and bool(sample.get("worn", False)),
                }
            else:
                merged_samples[recorded_at] = sample
        if merged_samples:
            samples = [merged_samples[key] for key in sorted(merged_samples)]
            sample_count = min(6, len(samples))
            worn_count = min(6, sum(1 for sample in samples if sample["worn"]))
            return {
                "hour_start": incoming["hour_start"],
                "worn_hours": round(worn_count / 6.0, 1),
                "sample_count": sample_count,
                "worn_count": worn_count,
                "samples": samples,
            }
    if int(incoming.get("sample_count", 6)) < int(existing.get("sample_count", 6)):
        return existing
    return incoming


def merged_hourly_records(existing_intervals, incoming_records: list[dict], record_date: date) -> list[dict]:
    by_hour: dict[str, dict] = {}
    existing = json_value(existing_intervals)
    for record in existing.get("hourly_records") or []:
        normalized = normalized_hour_record(record, record_date)
        if normalized:
            by_hour[normalized["hour_start"]] = normalized
    for record in incoming_records:
        normalized = normalized_hour_record(record, record_date)
        if normalized:
            by_hour[normalized["hour_start"]] = merge_hour_record(by_hour.get(normalized["hour_start"]), normalized)
    return [by_hour[key] for key in sorted(by_hour)]


@app.get("/")
def root():
    return {
        "name": settings.app_name,
        "env": settings.app_env,
        "public_base_url": settings.public_base_url,
        "status": "ok",
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "spinecaremom",
        "database": "ok" if ping() else "unknown",
        "time": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/api/v1/children/profile")
def upsert_child_profile(payload: ChildProfileUpsertRequest):
    source_payload = {
        "app_project": payload.app_project,
        "login_method": payload.login_method,
        "raw": payload.raw,
        "saved_at": datetime.now(timezone.utc).isoformat(),
    }
    with db_cursor(commit=True) as cur:
        ensure_children_profile_columns(cur)
        cur.execute(
            """
            INSERT INTO children (
                id, nickname, gender, birth_date, cobb_initial, curve_type, risser,
                prescribed_hours, brace_type, first_visit_date, guardian_phone,
                verification_code, login_method, consent_accepted, source_json,
                last_login_at, created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s, %s, %s, CAST(%s AS JSON),
                NOW(), NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                nickname = VALUES(nickname),
                gender = VALUES(gender),
                birth_date = VALUES(birth_date),
                cobb_initial = VALUES(cobb_initial),
                curve_type = VALUES(curve_type),
                risser = VALUES(risser),
                prescribed_hours = VALUES(prescribed_hours),
                brace_type = VALUES(brace_type),
                first_visit_date = VALUES(first_visit_date),
                guardian_phone = VALUES(guardian_phone),
                verification_code = VALUES(verification_code),
                login_method = VALUES(login_method),
                consent_accepted = VALUES(consent_accepted),
                source_json = VALUES(source_json),
                last_login_at = VALUES(last_login_at),
                updated_at = NOW()
            """,
            (
                payload.child_id,
                payload.nickname.strip(),
                normalize_gender(payload.gender),
                payload.birth_date,
                payload.cobb_initial,
                payload.curve_type,
                payload.risser,
                round(float(payload.prescribed_hours), 1),
                payload.brace_type,
                payload.first_visit_date,
                payload.phone,
                payload.verification_code,
                payload.login_method,
                payload.consent_accepted,
                json.dumps(source_payload, ensure_ascii=False),
            ),
        )
    return {"ok": True, "child_id": payload.child_id, "status": "saved"}


@app.get("/api/v1/children/{child_id}")
def get_child_profile(child_id: str):
    with db_cursor(commit=True) as cur:
        ensure_children_profile_columns(cur)
        cur.execute(
            """
            SELECT id, nickname, gender, birth_date, cobb_initial, curve_type, risser,
                   prescribed_hours, brace_type, first_visit_date, guardian_phone,
                   verification_code, login_method, consent_accepted, source_json,
                   last_login_at, created_at, updated_at
            FROM children
            WHERE id = %s
            """,
            (child_id,),
        )
        row = cur.fetchone()
    if not row:
        return JSONResponse(status_code=404, content={"detail": "child not found"})
    return iso_row(row)


@app.get("/api/v1/wear/summary")
def wear_summary(child_id: str = "demo-child", range: str = "week"):
    rows = query_wear_rows(child_id, range_days(range))
    prescribed = prescribed_hours_for_child(child_id)
    if not rows:
        return {
            "child_id": child_id,
            "range": range,
            "avg_hours": 0,
            "prescribed": prescribed,
            "compliance_rate": 0,
            "days_counted": 0,
            "longest_streak": 0,
            "gap_slots": [],
            "trend_vs_last": 0,
        }

    avg_hours = round(sum(float(row["worn_hours"]) for row in rows) / len(rows), 1)
    compliance_rate = round(sum(1 for row in rows if float(row["worn_hours"]) >= prescribed) / len(rows) * 100)
    current_days = min(7, len(rows))
    previous_rows = rows[-current_days * 2 : -current_days]
    current_rows = rows[-current_days:]
    previous_avg = (
        sum(float(row["worn_hours"]) for row in previous_rows) / len(previous_rows)
        if previous_rows
        else avg_hours
    )
    current_avg = sum(float(row["worn_hours"]) for row in current_rows) / len(current_rows)
    return {
        "child_id": child_id,
        "range": range,
        "avg_hours": avg_hours,
        "prescribed": prescribed,
        "compliance_rate": compliance_rate,
        "days_counted": len(rows),
        "longest_streak": longest_streak(rows, prescribed),
        "gap_slots": top_gap_slots(rows),
        "trend_vs_last": round(current_avg - previous_avg, 1),
        "period_start": rows[0]["record_date"].isoformat(),
        "period_end": rows[-1]["record_date"].isoformat(),
    }


@app.get("/api/v1/wear/records")
def wear_records(child_id: str = "demo-child", days: int = 35):
    rows = query_wear_rows(child_id, max(1, min(days, 3650)))
    return {"items": [iso_wear_row(row) for row in rows]}


@app.post("/api/v1/wear/device-sync")
def sync_device_wear(payload: DeviceWearSyncRequest):
    if not payload.records:
        return {
            "ok": True,
            "saved": 0,
            "message": "no records",
        }
    prescribed_hours = prescribed_hours_for_child(payload.child_id)
    saved_dates: list[str] = []
    with db_cursor(commit=True) as cur:
        for item in payload.records:
            cur.execute(
                """
                SELECT intervals_json
                FROM wear_records
                WHERE child_id = %s AND record_date = %s
                FOR UPDATE
                """,
                (payload.child_id, item.date),
            )
            existing_row = cur.fetchone()
            if payload.raw.get("replace_hourly_records"):
                hourly_records = [
                    record
                    for record in (normalized_hour_record(record, item.date) for record in item.hourly_records)
                    if record
                ]
                hourly_records = [hourly_records[key] for key in sorted(range(len(hourly_records)), key=lambda index: hourly_records[index]["hour_start"])]
            else:
                hourly_records = merged_hourly_records(
                    existing_row.get("intervals_json") if existing_row else None,
                    item.hourly_records,
                    item.date,
                )
            worn_hours = round(min(24.0, sum(float(record["worn_hours"]) for record in hourly_records)), 1)
            if not hourly_records:
                worn_hours = round(float(item.worn_hours), 1)
            intervals_payload = {
                "source": "bluetooth_device",
                "device_name": payload.device_name,
                "device_address": payload.device_address,
                "sample_count": item.sample_count,
                "worn_count": item.worn_count,
                "sample_interval_minutes": item.sample_interval_minutes,
                "hourly_records": hourly_records,
                "raw": payload.raw,
                "merged_from_existing": bool(existing_row),
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
                    f"wear-device-{uuid4().hex[:16]}",
                    payload.child_id,
                    item.date,
                    worn_hours,
                    prescribed_hours,
                    worn_hours >= prescribed_hours,
                    json.dumps(intervals_payload, ensure_ascii=False),
                ),
            )
            saved_dates.append(item.date.isoformat())

    return {
        "ok": True,
        "saved": len(saved_dates),
        "dates": saved_dates,
    }


@app.get("/api/v1/alerts")
def list_alerts(child_id: str = "demo-child"):
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, child_id, type, level, title, summary, trigger_detail, status, created_at
            FROM alerts
            WHERE child_id = %s
            ORDER BY FIELD(level, 'red', 'yellow', 'green'), created_at DESC
            LIMIT 50
            """,
            (child_id,),
        )
        rows = cur.fetchall()
    return {"items": [iso_row(row) for row in rows]}


@app.post("/api/v1/alerts/{alert_id}/handle")
def handle_alert(alert_id: str):
    with db_cursor(commit=True) as cur:
        cur.execute("SELECT id FROM alerts WHERE id = %s", (alert_id,))
        if not cur.fetchone():
            raise HTTPException(status_code=404, detail="alert not found")
        cur.execute(
            """
            UPDATE alerts
            SET status = 'handled'
            WHERE id = %s
            """,
            (alert_id,),
        )
        cur.execute(
            """
            SELECT id, child_id, type, level, title, summary, trigger_detail, status, created_at
            FROM alerts
            WHERE id = %s
            """,
            (alert_id,),
        )
        row = cur.fetchone()
    return {"item": iso_row(row), "status": "handled"}


@app.post("/api/v1/ai/ask")
def ask_ai(payload: AskRequest):
    question = payload.question.strip()
    emergency = any(keyword in question for keyword in EMERGENCY_KEYWORDS)
    if emergency:
        response = {
            "summary": "出现红线症状时应尽快联系医生或支具师",
            "analysis": "你的问题中包含需要及时处理的风险描述。APP 会按红色预警处理，并建议把症状、持续时间和照片整理给医生。",
            "advice": ["保留症状照片和发生时间", "尽快联系主治医生或支具师", "若症状加重或伴随全身不适，及时就诊"],
            "need_doctor": True,
            "doctor_reason": "命中强制就医关键词，AI 不做诊断或停戴判断，需要由医生或支具师评估。",
            "add_to_visit_list": True,
            "category": "clinical",
            "disclaimer": DISCLAIMER,
        }
    elif "体育" in question or "运动" in question:
        response = {
            "summary": "运动安排需要遵循医生医嘱，APP 可帮助整理复诊问题",
            "analysis": "是否在运动期间脱戴与支具类型、课程强度、医生方案有关，AI 不能替代医生判断。",
            "advice": ["把体育课项目和时长记录下来", "复诊时询问哪些运动需要脱戴", "运动后检查皮肤摩擦点并补足可佩戴时段"],
            "need_doctor": True,
            "doctor_reason": "涉及运动期间是否脱戴或调整佩戴方案，需要主治医生或支具师确认。",
            "add_to_visit_list": True,
            "category": "clinical",
            "disclaimer": DISCLAIMER,
        }
    elif "笑" in question or "不肯" in question or "焦虑" in question:
        response = {
            "summary": "孩子抗拒时，先降低对抗感，再把佩戴拆成可完成的小目标",
            "analysis": "青春期孩子更在意自主感，直接催促容易让佩戴变成冲突。",
            "advice": ["和孩子约定一个可选择的提醒时间", "把下午缺口拆成30分钟一段", "达标后只反馈进步，不反复追问"],
            "need_doctor": False,
            "doctor_reason": "",
            "add_to_visit_list": False,
            "category": "emotion",
            "disclaimer": DISCLAIMER,
        }
    else:
        response = {
            "summary": "可以先从最明显的缺口时段补起，不需要一次改变全部习惯",
            "analysis": "近35天包含达标、轻度不足和连续严重不足样例，主要缺口集中在下午和睡前。",
            "advice": ["下午14点开启一次短提醒", "把放学后第一小时设为固定佩戴段", "睡前用30秒检查皮肤和支具位置"],
            "need_doctor": False,
            "doctor_reason": "",
            "add_to_visit_list": False,
            "category": "education",
            "disclaimer": DISCLAIMER,
        }

    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            INSERT INTO ai_messages
                (id, child_id, role, content, category, need_doctor, created_at)
            VALUES
                (%s, %s, 'user', %s, %s, %s, NOW()),
                (%s, %s, 'assistant', %s, %s, %s, NOW())
            """,
            (
                str(uuid4()),
                payload.child_id,
                question,
                response["category"],
                response["need_doctor"],
                str(uuid4()),
                payload.child_id,
                response["summary"],
                response["category"],
                response["need_doctor"],
            ),
        )
    return response


@app.get("/api/v1/reports")
def list_reports(child_id: str = "demo-child"):
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, child_id, kind, period_start, period_end, payload_json, pdf_url, created_at
            FROM reports
            WHERE child_id = %s
            ORDER BY created_at DESC
            """,
            (child_id,),
        )
        rows = cur.fetchall()
    return {"items": [iso_row(row) for row in rows]}


@app.post("/api/v1/reports")
def archive_report(payload: ReportArchiveRequest):
    item_id = str(uuid4())
    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            INSERT INTO reports (id, child_id, kind, period_start, period_end, payload_json, pdf_url, created_at)
            VALUES (%s, %s, %s, %s, %s, CAST(%s AS JSON), %s, NOW())
            """,
            (
                item_id,
                payload.child_id,
                payload.kind,
                payload.period_start,
                payload.period_end,
                json.dumps(payload.payload_json, ensure_ascii=False),
                payload.pdf_url,
            ),
        )
    return {"id": item_id, "status": "saved"}


@app.post("/api/v1/reports/visit")
def create_visit_report(child_id: str = "demo-child", days: int = 35):
    return {
        "child_id": child_id,
        "days": days,
        "pdf_url": None,
        "qr_url": None,
        "status": "preview_ready",
    }


@app.get("/api/v1/delete-requests/current")
def current_delete_request(child_id: str = "demo-child"):
    with db_cursor() as cur:
        ensure_delete_requests_table(cur)
        cur.execute(
            """
            SELECT id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                   scheduled_delete_at, cancelled_at, completed_at, deleted_counts_json
            FROM delete_requests
            WHERE child_id = %s
            ORDER BY requested_at DESC
            LIMIT 1
            """,
            (child_id,),
        )
        row = cur.fetchone()
    return {"item": iso_row(row) if row else None}


@app.post("/api/v1/delete-requests")
def create_delete_request(payload: DeleteDataRequestCreate):
    if payload.confirmation_text.strip() != DELETE_CONFIRMATION_TEXT:
        raise HTTPException(status_code=400, detail="confirmation text mismatch")
    if not (payload.backup_confirmed and payload.irreversible_confirmed and payload.current_child_confirmed):
        raise HTTPException(status_code=400, detail="all confirmations are required")

    with db_cursor(commit=True) as cur:
        ensure_delete_requests_table(cur)
        ensure_children_profile_columns(cur)
        cur.execute("SELECT id, nickname FROM children WHERE id = %s", (payload.child_id,))
        child_row = cur.fetchone()
        if child_row is None:
            raise HTTPException(status_code=404, detail="child not found")
        if payload.child_nickname.strip() != str(child_row["nickname"]):
            raise HTTPException(status_code=400, detail="child nickname mismatch")

        cur.execute(
            """
            SELECT id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                   scheduled_delete_at, cancelled_at, completed_at, deleted_counts_json
            FROM delete_requests
            WHERE child_id = %s AND status = 'confirmed'
            ORDER BY requested_at DESC
            LIMIT 1
            """,
            (payload.child_id,),
        )
        existing = cur.fetchone()
        if existing:
            return {"item": iso_row(existing), "status": "already_exists"}

        item_id = str(uuid4())
        request_json = {
            "child_nickname": payload.child_nickname,
            "confirmation_text": payload.confirmation_text,
            "cooling_hours": DELETE_COOLING_HOURS,
        }
        cur.execute(
            """
            INSERT INTO delete_requests (
                id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                scheduled_delete_at, request_json
            ) VALUES (
                %s, %s, 'confirmed', %s, UTC_TIMESTAMP(), UTC_TIMESTAMP(),
                DATE_ADD(UTC_TIMESTAMP(), INTERVAL %s HOUR), CAST(%s AS JSON)
            )
            """,
            (
                item_id,
                payload.child_id,
                payload.backup_confirmed,
                DELETE_COOLING_HOURS,
                json.dumps(request_json, ensure_ascii=False),
            ),
        )
        cur.execute(
            """
            SELECT id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                   scheduled_delete_at, cancelled_at, completed_at, deleted_counts_json
            FROM delete_requests
            WHERE id = %s
            """,
            (item_id,),
        )
        row = cur.fetchone()
    return {"item": iso_row(row), "status": "created"}


@app.post("/api/v1/delete-requests/{request_id}/cancel")
def cancel_delete_request(request_id: str):
    with db_cursor(commit=True) as cur:
        ensure_delete_requests_table(cur)
        cur.execute("SELECT id, status FROM delete_requests WHERE id = %s", (request_id,))
        row = cur.fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="delete request not found")
        if row["status"] == "completed":
            raise HTTPException(status_code=409, detail="completed request cannot be cancelled")
        cur.execute(
            """
            UPDATE delete_requests
            SET status = 'cancelled', cancelled_at = UTC_TIMESTAMP()
            WHERE id = %s AND status = 'confirmed'
            """,
            (request_id,),
        )
        cur.execute(
            """
            SELECT id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                   scheduled_delete_at, cancelled_at, completed_at, deleted_counts_json
            FROM delete_requests
            WHERE id = %s
            """,
            (request_id,),
        )
        updated = cur.fetchone()
    return {"item": iso_row(updated), "status": "cancelled"}


@app.post("/api/v1/delete-requests/{request_id}/execute")
def execute_delete_request(request_id: str, payload: DeleteDataRequestExecute):
    if payload.confirmation_text.strip() != DELETE_CONFIRMATION_TEXT:
        raise HTTPException(status_code=400, detail="confirmation text mismatch")

    refs: list[str] = []
    counts: dict = {}
    with db_cursor(commit=True) as cur:
        ensure_delete_requests_table(cur)
        cur.execute(
            """
            SELECT id, child_id, status, scheduled_delete_at
            FROM delete_requests
            WHERE id = %s
            """,
            (request_id,),
        )
        row = cur.fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="delete request not found")
        if row["status"] != "confirmed":
            raise HTTPException(status_code=409, detail="delete request is not executable")
        if row["scheduled_delete_at"] and row["scheduled_delete_at"] > datetime.utcnow():
            raise HTTPException(status_code=409, detail="cooling period is not over")

        child_id = row["child_id"]
        refs = collect_upload_refs_for_child(cur, child_id)
        counts = delete_child_cloud_data(cur, child_id)
        counts["upload_files_referenced"] = len(refs)
        cur.execute(
            """
            UPDATE delete_requests
            SET status = 'completed',
                completed_at = UTC_TIMESTAMP(),
                deleted_counts_json = CAST(%s AS JSON)
            WHERE id = %s
            """,
            (json.dumps(counts, ensure_ascii=False), request_id),
        )

    try:
        counts["upload_files_deleted"] = delete_upload_files(refs)
    except Exception as exc:
        counts["upload_file_delete_error"] = str(exc)
    with db_cursor(commit=True) as cur:
        ensure_delete_requests_table(cur)
        cur.execute(
            """
            UPDATE delete_requests
            SET deleted_counts_json = CAST(%s AS JSON)
            WHERE id = %s
            """,
            (json.dumps(counts, ensure_ascii=False), request_id),
        )
        cur.execute(
            """
            SELECT id, child_id, status, backup_confirmed, requested_at, confirmed_at,
                   scheduled_delete_at, cancelled_at, completed_at, deleted_counts_json
            FROM delete_requests
            WHERE id = %s
            """,
            (request_id,),
        )
        updated = cur.fetchone()
    return {"item": iso_row(updated), "status": "completed"}


@app.get("/api/v1/skin-logs")
def list_skin_logs(child_id: str = "demo-child"):
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, child_id, log_date, region, status, note, photos_json, created_at
            FROM skin_logs
            WHERE child_id = %s
            ORDER BY log_date DESC
            """,
            (child_id,),
        )
        rows = cur.fetchall()
    return {"items": [iso_row(row) for row in rows]}


@app.post("/api/v1/skin-logs")
def create_skin_log(payload: SkinLogRequest):
    item_id = str(uuid4())
    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            INSERT INTO skin_logs (id, child_id, log_date, region, status, note, photos_json, created_at)
            VALUES (%s, %s, CURDATE(), %s, %s, %s, CAST(%s AS JSON), NOW())
            """,
            (
                item_id,
                payload.child_id,
                payload.region,
                payload.status,
                payload.note,
                json.dumps(payload.photos, ensure_ascii=False),
            ),
        )
    return {"id": item_id, "status": "saved"}


@app.get("/api/v1/growth-logs")
def list_growth_logs(child_id: str = "demo-child"):
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, child_id, log_date, height_cm, note, created_at
            FROM growth_logs
            WHERE child_id = %s
            ORDER BY log_date DESC
            """,
            (child_id,),
        )
        rows = cur.fetchall()
    return {"items": [iso_row(row) for row in rows]}


@app.post("/api/v1/growth-logs")
def create_growth_log(payload: GrowthLogRequest):
    item_id = str(uuid4())
    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            INSERT INTO growth_logs (id, child_id, log_date, height_cm, note, created_at)
            VALUES (%s, %s, CURDATE(), %s, %s, NOW())
            """,
            (item_id, payload.child_id, payload.height_cm, payload.note),
        )
    return {"id": item_id, "status": "saved"}


@app.get("/api/v1/imaging")
def list_imaging(child_id: str = "demo-child"):
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, child_id, image_type, file_url, shot_date, note, created_at
            FROM imaging_files
            WHERE child_id = %s
            ORDER BY shot_date DESC
            """,
            (child_id,),
        )
        rows = cur.fetchall()
    return {"items": [iso_row(row) for row in rows]}


@app.post("/api/v1/imaging")
def create_imaging(payload: ImagingRequest):
    item_id = str(uuid4())
    with db_cursor(commit=True) as cur:
        cur.execute(
            """
            INSERT INTO imaging_files (id, child_id, image_type, file_url, shot_date, note, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, NOW())
            """,
            (
                item_id,
                payload.child_id,
                payload.image_type,
                payload.file_url,
                payload.shot_date,
                payload.note,
            ),
        )
    return {"id": item_id, "status": "saved"}


@app.post("/api/v1/uploads")
async def upload_file(file: UploadFile = File(...)):
    suffix = Path(file.filename or "upload.bin").suffix
    file_id = f"{uuid4()}{suffix}"
    upload_root = Path(settings.upload_dir)
    upload_root.mkdir(parents=True, exist_ok=True)
    destination = upload_root / file_id
    with destination.open("wb") as out:
        while chunk := await file.read(1024 * 1024):
            out.write(chunk)
    return {"id": file_id, "filename": file.filename, "size": destination.stat().st_size}


@app.get("/api/v1/uploads/{file_id}")
def get_upload(file_id: str):
    if Path(file_id).name != file_id:
        raise HTTPException(status_code=400, detail="invalid file id")
    path = Path(settings.upload_dir) / file_id
    if not path.is_file():
        raise HTTPException(status_code=404, detail="file not found")
    return FileResponse(path)


@app.exception_handler(Exception)
async def generic_exception_handler(_, exc: Exception):
    return JSONResponse(status_code=500, content={"detail": str(exc)})
