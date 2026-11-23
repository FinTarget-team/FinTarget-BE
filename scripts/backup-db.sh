#!/usr/bin/env bash
# 운영 DB(PostgreSQL) 백업 스크립트 (#219). 서버의 cron으로 매일 실행한다.
#   - fintarget-db 컨테이너 안에서 pg_dump(custom 형식, 압축)를 실행해 BACKUP_DIR에 저장한다
#   - RETENTION_DAYS일보다 오래된 백업은 지운다 (서버 디스크가 19GB로 작음)
#   - 덤프가 실패하거나 비어 있으면 만들던 파일을 지우고 종료 코드 1로 끝낸다
#
# 사용법 (서버에서): ~/FinTarget-BE/scripts/backup-db.sh
# cron 예시 (매일 새벽 4시, 한국 시간 서버 기준):
#   0 4 * * * ~/FinTarget-BE/scripts/backup-db.sh >> ~/backups/backup.log 2>&1
# 복원 예시:
#   docker exec -i fintarget-db pg_restore -U "$DB_USERNAME" -d "$DB_NAME" --clean --if-exists < ~/backups/fintarget-<날짜>.dump
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/FinTarget-BE}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
CONTAINER="${DB_CONTAINER:-fintarget-db}"

env_value() {
  grep -E "^$1=" "$APP_DIR/.env" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

DB_NAME="${DB_NAME:-$(env_value DB_NAME)}"
DB_USERNAME="${DB_USERNAME:-$(env_value DB_USERNAME)}"
DB_NAME="${DB_NAME:-fintarget}"
DB_USERNAME="${DB_USERNAME:-moon}"

install -m 700 -d "$BACKUP_DIR"
target="$BACKUP_DIR/fintarget-$(date +%Y%m%d-%H%M%S).dump"

# 백업 파일에는 개인정보가 들어 있으므로 본인만 읽을 수 있게 만든다
umask 077
if ! docker exec "$CONTAINER" pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc > "$target" || [ ! -s "$target" ]; then
  rm -f "$target"
  echo "$(date '+%F %T') 백업 실패: $CONTAINER / $DB_NAME" >&2
  exit 1
fi

echo "$(date '+%F %T') 백업 완료: $target ($(du -h "$target" | cut -f1))"

find "$BACKUP_DIR" -maxdepth 1 -name 'fintarget-*.dump' -type f -mtime +"$RETENTION_DAYS" -print -delete \
  | sed 's/^/오래된 백업 삭제: /'
