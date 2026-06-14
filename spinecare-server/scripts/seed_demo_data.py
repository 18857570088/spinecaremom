from __future__ import annotations

import json
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.db import db_cursor  # noqa: E402


CHILD_ID = "demo-child"
PRESCRIBED = 20.0
BASE_DATE = date(2026, 5, 11)


WEAR_HOURS = [
    21.0,
    20.6,
    19.2,
    22.1,
    17.4,
    20.2,
    0.0,
    18.8,
    21.5,
    20.0,
    15.2,
    16.1,
    19.8,
    20.7,
    21.2,
    12.5,
    14.8,
    18.0,
    20.3,
    20.9,
    19.5,
    21.8,
    22.4,
    20.6,
    18.6,
    9.8,
    10.5,
    11.3,
    8.6,
    11.7,
    16.3,
    18.7,
    20.4,
    21.0,
    20.6,
]


def scenario_for(hours: float) -> dict:
    if hours == 0:
        return {
            "scenario": "device_off_or_absent",
            "gap_slots": ["08:00-22:00"],
            "notes": "当天有同步记录但几乎未佩戴，用于测试极端低值。",
        }
    if hours < 12:
        return {
            "scenario": "severe_low",
            "gap_slots": ["14:00-17:00", "21:00-22:00"],
            "notes": "连续严重不足样例，用于触发红色预警。",
        }
    if hours < 16:
        return {
            "scenario": "travel_or_skin_discomfort",
            "gap_slots": ["10:00-12:00", "14:00-17:00"],
            "notes": "外出或皮肤不适导致佩戴明显不足。",
        }
    if hours < PRESCRIBED:
        return {
            "scenario": "mild_low",
            "gap_slots": ["21:00-22:00"],
            "notes": "轻度不足，用于趋势和黄警测试。",
        }
    return {
        "scenario": "compliant",
        "gap_slots": [],
        "notes": "达到医嘱佩戴时长。",
    }


def insert_wear_records(cur):
    for offset, hours in enumerate(WEAR_HOURS):
        record_date = BASE_DATE + timedelta(days=offset)
        scenario = scenario_for(hours)
        intervals = {
            **scenario,
            "effective_intervals": [
                {"start": "07:00", "end": "12:00"},
                {"start": "13:00", "end": "18:00"},
                {"start": "19:30", "end": "23:00"},
            ]
            if hours >= PRESCRIBED
            else [
                {"start": "07:30", "end": "11:30"},
                {"start": "18:00", "end": "22:00"},
            ],
        }
        cur.execute(
            """
            INSERT INTO wear_records
                (id, child_id, record_date, worn_hours, prescribed_hours, is_compliant, intervals_json, synced_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                f"wear-{record_date.isoformat()}",
                CHILD_ID,
                record_date,
                hours,
                PRESCRIBED,
                hours >= PRESCRIBED,
                json.dumps(intervals, ensure_ascii=False),
                datetime.combine(record_date, datetime.min.time()).replace(hour=23, minute=10),
            ),
        )


def main() -> None:
    with db_cursor(commit=True) as cur:
        for table in [
            "wear_records",
            "skin_logs",
            "growth_logs",
            "imaging_files",
            "reports",
            "alerts",
            "ai_messages",
            "devices",
        ]:
            cur.execute(f"DELETE FROM {table} WHERE child_id = %s", (CHILD_ID,))

        cur.execute(
            """
            INSERT INTO children (
                id, nickname, gender, birth_date, cobb_initial, curve_type, risser,
                prescribed_hours, brace_type, first_visit_date
            ) VALUES (
                %s, '朵朵', 'female', '2014-03-18', 25, 'thoracolumbar', '2',
                %s, 'rigid', '2025-10-20'
            )
            ON DUPLICATE KEY UPDATE
                nickname = VALUES(nickname),
                prescribed_hours = VALUES(prescribed_hours),
                cobb_initial = VALUES(cobb_initial),
                curve_type = VALUES(curve_type),
                risser = VALUES(risser),
                brace_type = VALUES(brace_type)
            """,
            (CHILD_ID, PRESCRIBED),
        )

        cur.execute(
            """
            INSERT INTO devices (id, child_id, serial_no, battery, last_sync_at)
            VALUES (%s, %s, %s, %s, %s)
            """,
            ("device-spinesensor-a12", CHILD_ID, "SCM-2026-0614", 82, datetime(2026, 6, 14, 8, 12)),
        )

        insert_wear_records(cur)

        skin_rows = [
            ("skin-20260512-normal", date(2026, 5, 12), "back", "normal", "背部皮肤正常。"),
            ("skin-20260521-red", date(2026, 5, 21), "waist_left", "red", "左腰部轻度发红，疑似支具边缘摩擦。"),
            ("skin-20260522-itch", date(2026, 5, 22), "waist_left", "itch", "左腰部发红伴轻微瘙痒。"),
            ("skin-20260606-broken", date(2026, 6, 6), "waist_left", "broken", "左腰部局部破皮，用于红色预警测试。"),
            ("skin-20260607-blister", date(2026, 6, 7), "waist_left", "blister", "左腰部小水泡，已记录照片占位。"),
            ("skin-20260612-normal", date(2026, 6, 12), "back", "normal", "调整内衬后皮肤恢复正常。"),
        ]
        for row in skin_rows:
            cur.execute(
                """
                INSERT INTO skin_logs (id, child_id, log_date, region, status, note, photos_json, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
                """,
                (row[0], CHILD_ID, row[1], row[2], row[3], row[4], json.dumps(["demo-photo-placeholder"], ensure_ascii=False)),
            )

        growth_rows = [
            ("growth-20260511", date(2026, 5, 11), 153.0, "5月例行身高记录。"),
            ("growth-20260525", date(2026, 5, 25), 153.4, "月中复测。"),
            ("growth-20260614", date(2026, 6, 14), 154.3, "近一个月增长1.3cm，用于身高突增提醒测试。"),
        ]
        for row in growth_rows:
            cur.execute(
                """
                INSERT INTO growth_logs (id, child_id, log_date, height_cm, note, created_at)
                VALUES (%s, %s, %s, %s, %s, NOW())
                """,
                (row[0], CHILD_ID, row[1], row[2], row[3]),
            )

        imaging_rows = [
            ("image-xray-20260601", "xray", date(2026, 6, 1), "Cobb 25°，医生建议继续支具治疗。"),
            ("image-posture-20260501", "posture", date(2026, 5, 1), "站立体态照，肩线较4月更稳定。"),
            ("image-adams-20260402", "adams", date(2026, 4, 2), "Adams前屈照，家庭记录，不用于诊断。"),
        ]
        for row in imaging_rows:
            cur.execute(
                """
                INSERT INTO imaging_files (id, child_id, image_type, file_url, shot_date, note, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, NOW())
                """,
                (row[0], CHILD_ID, row[1], None, row[2], row[3]),
            )

        alert_rows = [
            ("alert-red-wear-low", "wear_low", "red", "连续5天佩戴严重不足", "6月5日至6月9日连续5天低于医嘱60%，建议查看缺口并联系医生或支具师。", "连续5天 < 医嘱60%"),
            ("alert-red-skin-broken", "skin_broken", "red", "左腰部出现破皮/水泡", "6月6日至6月7日记录破皮和水泡，请尽快联系支具师或医生。", "任何破损/水泡记录"),
            ("alert-yellow-growth", "growth_spurt", "yellow", "近一个月身高增长1.3cm", "支具可能需要复查调整，建议复诊时重点询问。", "1个月内身高增长 > 1cm"),
            ("alert-yellow-mild-low", "wear_low_yellow", "yellow", "多次下午和睡前缺口", "近35天多次出现14-17点和21-22点缺口，建议开启提醒。", "缺口时段聚集"),
            ("alert-green-visit", "visit_reminder", "green", "复诊提醒", "距上次影像检查已6个月，可预约复查。", "距上次影像检查 > 6个月"),
        ]
        for row in alert_rows:
            cur.execute(
                """
                INSERT INTO alerts (id, child_id, type, level, title, summary, trigger_detail, status, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, 'new', NOW())
                """,
                (row[0], CHILD_ID, row[1], row[2], row[3], row[4], row[5]),
            )

        report_payloads = [
            ("report-week-20260614", "ai_weekly", date(2026, 6, 8), date(2026, 6, 14), {"avg_hours": 16.8, "compliance_rate": 43, "gap_slots": ["14:00-17:00", "21:00-22:00"]}),
            ("report-month-202606", "ai_monthly", date(2026, 5, 16), date(2026, 6, 14), {"avg_hours": 17.5, "compliance_rate": 47, "skin_events": 4, "growth_delta_cm": 1.3}),
            ("report-visit-35d", "visit", date(2026, 5, 11), date(2026, 6, 14), {"period_days": 35, "doctor_questions": ["是否需要调整支具边缘", "体育课是否需要临时脱戴", "身高增长后支具是否适配"]}),
        ]
        for row in report_payloads:
            cur.execute(
                """
                INSERT INTO reports (id, child_id, kind, period_start, period_end, payload_json, pdf_url, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, NULL, NOW())
                """,
                (row[0], CHILD_ID, row[1], row[2], row[3], json.dumps(row[4], ensure_ascii=False)),
            )

        ai_rows = [
            ("user", "少戴2小时有影响吗？", "education", False),
            ("assistant", "少戴会影响矫正效果，建议从下午缺口时段补起。", "education", False),
            ("user", "皮肤破溃怎么办？", "clinical", True),
            ("assistant", "出现破皮或水泡需要尽快联系医生或支具师，不建议自行判断是否继续压迫该部位。", "clinical", True),
            ("user", "孩子被同学笑话不愿意戴怎么办？", "emotion", False),
            ("assistant", "先降低对抗感，把佩戴拆成可完成的小目标，并把进步反馈给孩子。", "emotion", False),
        ]
        for role, content, category, need_doctor in ai_rows:
            cur.execute(
                """
                INSERT INTO ai_messages (id, child_id, role, content, category, need_doctor, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, NOW())
                """,
                (str(uuid4()), CHILD_ID, role, content, category, need_doctor),
            )

    print("Seeded demo data for demo-child: 35 wear records plus skin, growth, imaging, alerts, reports, AI messages.")


if __name__ == "__main__":
    main()
