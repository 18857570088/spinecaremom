CREATE TABLE IF NOT EXISTS children (
    id VARCHAR(64) PRIMARY KEY,
    nickname VARCHAR(32) NOT NULL,
    gender ENUM('male', 'female') NOT NULL,
    birth_date DATE NULL,
    cobb_initial INT NULL,
    curve_type VARCHAR(32) NULL,
    risser VARCHAR(16) NULL,
    prescribed_hours DECIMAL(4,1) NOT NULL DEFAULT 20.0,
    brace_type VARCHAR(32) NULL,
    first_visit_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    serial_no VARCHAR(64) NOT NULL,
    battery INT NULL,
    last_sync_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_devices_child (child_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wear_records (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    record_date DATE NOT NULL,
    worn_hours DECIMAL(4,1) NOT NULL,
    prescribed_hours DECIMAL(4,1) NOT NULL,
    is_compliant BOOLEAN NOT NULL,
    intervals_json JSON NULL,
    synced_at DATETIME NULL,
    UNIQUE KEY uq_wear_child_date (child_id, record_date),
    INDEX idx_wear_child_date (child_id, record_date)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS skin_logs (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    log_date DATE NOT NULL,
    region VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    note TEXT NULL,
    photos_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skin_child_date (child_id, log_date)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS growth_logs (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    log_date DATE NOT NULL,
    height_cm DECIMAL(5,1) NOT NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_growth_child_date (child_id, log_date)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alerts (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    level ENUM('green', 'yellow', 'red') NOT NULL,
    title VARCHAR(128) NOT NULL,
    summary TEXT NOT NULL,
    trigger_detail TEXT NULL,
    status ENUM('new', 'handled', 'recovered') NOT NULL DEFAULT 'new',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_child_status (child_id, status),
    INDEX idx_alert_child_created (child_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reports (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    period_start DATE NULL,
    period_end DATE NULL,
    payload_json JSON NULL,
    pdf_url VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reports_child_created (child_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS imaging_files (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    image_type VARCHAR(32) NOT NULL,
    file_url VARCHAR(512) NULL,
    shot_date DATE NOT NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_imaging_child_date (child_id, shot_date)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_messages (
    id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) NOT NULL,
    role ENUM('user', 'assistant') NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NULL,
    need_doctor BOOLEAN NOT NULL DEFAULT FALSE,
    feedback ENUM('up', 'down') NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_child_created (child_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO children (
    id, nickname, gender, birth_date, cobb_initial, curve_type, risser,
    prescribed_hours, brace_type, first_visit_date
) VALUES (
    'demo-child', '朵朵', 'female', '2014-03-18', 25, 'thoracolumbar', '2',
    20.0, 'rigid', '2025-10-20'
) ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO alerts (id, child_id, type, level, title, summary, trigger_detail, status)
VALUES
    ('alert-red-wear-low', 'demo-child', 'wear_low', 'red', '连续5天佩戴严重不足', '近5天低于医嘱60%，建议查看缺口并联系医生或支具师。', '连续5天 < 医嘱60%', 'new'),
    ('alert-yellow-skin-red', 'demo-child', 'skin_red', 'yellow', '左腰部连续2天发红', '请观察摩擦点，持续不适时及时咨询支具师。', '同部位连续2天发红', 'new'),
    ('alert-green-visit', 'demo-child', 'visit_reminder', 'green', '复诊提醒', '距上次影像检查已6个月，可预约复查。', '距上次影像检查 > 6个月', 'new')
ON DUPLICATE KEY UPDATE summary = VALUES(summary), status = VALUES(status);
