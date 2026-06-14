from __future__ import annotations

import json
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
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


class AskRequest(BaseModel):
    child_id: str = Field(default="demo-child")
    question: str = Field(min_length=1, max_length=500)


class SkinLogRequest(BaseModel):
    child_id: str = "demo-child"
    region: str
    status: str
    note: str | None = None


class GrowthLogRequest(BaseModel):
    child_id: str = "demo-child"
    height_cm: float
    note: str | None = None


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


def range_days(range_name: str) -> int:
    mapping = {
        "week": 7,
        "month": 30,
        "35d": 35,
        "all": 35,
    }
    return mapping.get(range_name, 7)


def longest_streak(rows: list[dict]) -> int:
    longest = 0
    current = 0
    for row in sorted(rows, key=lambda item: item["record_date"]):
        if row["is_compliant"]:
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


@app.get("/api/v1/wear/summary")
def wear_summary(child_id: str = "demo-child", range: str = "week"):
    rows = query_wear_rows(child_id, range_days(range))
    if not rows:
        return {
            "child_id": child_id,
            "range": range,
            "avg_hours": 0,
            "prescribed": 0,
            "compliance_rate": 0,
            "days_counted": 0,
            "longest_streak": 0,
            "gap_slots": [],
            "trend_vs_last": 0,
        }

    avg_hours = round(sum(float(row["worn_hours"]) for row in rows) / len(rows), 1)
    prescribed = round(float(rows[-1]["prescribed_hours"]), 1)
    compliance_rate = round(sum(1 for row in rows if row["is_compliant"]) / len(rows) * 100)
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
        "longest_streak": longest_streak(rows),
        "gap_slots": top_gap_slots(rows),
        "trend_vs_last": round(current_avg - previous_avg, 1),
        "period_start": rows[0]["record_date"].isoformat(),
        "period_end": rows[-1]["record_date"].isoformat(),
    }


@app.get("/api/v1/wear/records")
def wear_records(child_id: str = "demo-child", days: int = 35):
    rows = query_wear_rows(child_id, max(1, min(days, 120)))
    return {"items": [iso_row(row) for row in rows]}


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


@app.post("/api/v1/reports/visit")
def create_visit_report(child_id: str = "demo-child", days: int = 35):
    return {
        "child_id": child_id,
        "days": days,
        "pdf_url": None,
        "qr_url": None,
        "status": "preview_ready",
    }


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
            INSERT INTO skin_logs (id, child_id, log_date, region, status, note, created_at)
            VALUES (%s, %s, CURDATE(), %s, %s, %s, NOW())
            """,
            (item_id, payload.child_id, payload.region, payload.status, payload.note),
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


@app.exception_handler(Exception)
async def generic_exception_handler(_, exc: Exception):
    return JSONResponse(status_code=500, content={"detail": str(exc)})
