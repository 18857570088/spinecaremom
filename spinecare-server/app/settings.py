import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("APP_NAME", "Spinecare Mom API")
    app_env: str = os.getenv("APP_ENV", "development")
    public_base_url: str = os.getenv("PUBLIC_BASE_URL", "http://127.0.0.1:8098")
    mysql_host: str = os.getenv("MYSQL_HOST", "127.0.0.1")
    mysql_port: int = int(os.getenv("MYSQL_PORT", "3306"))
    mysql_database: str = os.getenv("MYSQL_DATABASE", "spinecaremom")
    mysql_user: str = os.getenv("MYSQL_USER", "wm")
    mysql_password: str = os.getenv("MYSQL_PASSWORD", "")
    upload_dir: str = os.getenv("UPLOAD_DIR", "/var/lib/spinecaremom/uploads")
    log_dir: str = os.getenv("LOG_DIR", "/var/log/spinecaremom")
    cors_origins: str = os.getenv("CORS_ORIGINS", "*")

    @property
    def cors_origin_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [item.strip() for item in self.cors_origins.split(",") if item.strip()]


settings = Settings()
