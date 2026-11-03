#!/usr/bin/env bash
# 운영 서버에서 실행하는 배포 스크립트. 지금까지 수동으로 하던 절차를 그대로 자동화한다.
#   1. origin/dev를 fast-forward로 받는다
#   2. 메인 서버 코드가 바뀌었을 때만 앱 컨테이너를 다시 빌드한다 (DB·Redis는 건드리지 않음)
#   3. 앱이 뜰 때까지 기다리고(/v3/api-docs 200), 실패하면 이전 커밋으로 되돌려 다시 빌드한다
# .env, src/main/resources/application.yml 같은 서버 설정 파일은 git 추적 대상이 아니라 건드리지 않는다.
#
# 사용법 (서버에서): ~/FinTarget-BE/scripts/deploy.sh
#   FORCE_BUILD=1 이면 변경 파일과 상관없이 다시 빌드한다.
set -euo pipefail

# 이 스크립트는 실행 중에 git merge로 자기 자신을 갱신할 수 있다. bash가 파일을 읽어 가며 실행하다
# 바뀐 내용을 섞어 읽지 않도록, 전체를 main 함수로 감싸 끝까지 파싱한 뒤 실행한다.
main() {

  APP_DIR="${APP_DIR:-$HOME/FinTarget-BE}"
  BRANCH="${BRANCH:-dev}"
  HEALTH_URL="${HEALTH_URL:-http://localhost:8080/v3/api-docs}"
  HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"
  # 이 경로가 바뀌었을 때만 이미지를 다시 빌드한다 (문서, mock-bank만 바뀐 경우는 건너뜀)
  BUILD_PATHS=(src build.gradle settings.gradle gradle gradlew Dockerfile .dockerignore docker-compose.yml)

  cd "$APP_DIR"

  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "서버 작업 트리에 커밋되지 않은 변경이 있어 배포를 중단합니다." >&2
    git status --short --untracked-files=no >&2
    exit 1
  fi

  previous=$(git rev-parse HEAD)
  git fetch --quiet origin "$BRANCH"
  git merge --ff-only --quiet "origin/$BRANCH"
  current=$(git rev-parse HEAD)
  echo "배포 대상: $(git log --oneline -1 "$current")"

  if [ "$previous" = "$current" ] && [ "${FORCE_BUILD:-0}" != "1" ]; then
    echo "새 커밋이 없어 배포를 건너뜁니다."
    exit 0
  fi

  if [ "${FORCE_BUILD:-0}" != "1" ] && git diff --quiet "$previous" "$current" -- "${BUILD_PATHS[@]}"; then
    echo "메인 서버 코드 변경이 없어(문서·mock-bank 등) 다시 빌드하지 않습니다."
    exit 0
  fi

  wait_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
    while [ "$SECONDS" -lt "$deadline" ]; do
      if [ "$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)" = "200" ]; then
        return 0
      fi
      sleep 3
    done
    return 1
  }

  build_and_start() {
    docker compose up -d --build --no-deps app
  }

  echo "앱 컨테이너를 다시 빌드합니다."
  build_and_start
  # 이전 컨테이너가 아직 응답하는 순간을 성공으로 오인하지 않도록 잠시 기다린다
  sleep 10

  if wait_healthy; then
    echo "배포 성공: $(git log --oneline -1 "$current")"
    # 서버 디스크가 작아(19GB) 빌드할 때마다 쌓이는 이미지·빌드 캐시를 정리한다. 최근 하루치 캐시는 다음 빌드 속도를 위해 남긴다
    docker image prune -f >/dev/null || true
    docker builder prune -f --filter until=24h >/dev/null || true
    exit 0
  fi

  echo "앱이 ${HEALTH_TIMEOUT_SECONDS}초 안에 응답하지 않습니다. 최근 로그:" >&2
  docker logs --tail 50 fintarget-app >&2 || true

  echo "이전 버전($(git log --oneline -1 "$previous"))으로 되돌립니다." >&2
  git reset --quiet --hard "$previous"
  build_and_start
  sleep 10
  if wait_healthy; then
    echo "롤백 완료. 실패한 커밋: $(git log --oneline -1 "$current")" >&2
  else
    echo "롤백 후에도 앱이 응답하지 않습니다. 서버를 직접 확인해 주세요." >&2
  fi
  exit 1
}

main "$@"
exit $?
