# Spinecare Mom Server

Independent backend service for Spinecare Mom.

## Planned Names

- Local source directory: `D:\2026\202606\SpinecareMom\spinecare-server`
- Remote service directory: `/opt/spinecaremom-server`
- Remote config file: `/etc/spinecaremom/spinecaremom.env`
- Remote upload directory: `/var/lib/spinecaremom/uploads`
- Remote log directory: `/var/log/spinecaremom`
- systemd service: `spinecaremom.service`
- Nginx location snippet: `/etc/nginx/snippets/spinecaremom-api-location.conf`
- Public API entry: `http://152.136.62.157/spinecaremom-api/`
- Health check: `http://152.136.62.157/spinecaremom-api/health`

## Local Layout

```text
spinecare-server/
  app/                  FastAPI application
  config/               Environment template only
  deploy/               systemd and Nginx templates
  sql/                  MySQL schema
  uploads/.gitkeep      Local placeholder
  logs/.gitkeep         Local placeholder
```

## API Examples

```text
GET  /health
GET  /api/v1/wear/summary?child_id=demo-child&range=week
GET  /api/v1/alerts?child_id=demo-child
POST /api/v1/ai/ask
GET  /api/v1/reports?child_id=demo-child
POST /api/v1/skin-logs
POST /api/v1/growth-logs
POST /api/v1/uploads
```
