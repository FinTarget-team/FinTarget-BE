#!/usr/bin/env bash
# 운영 서버에서 실행하는 배포 스크립트. 지금까지 수동으로 하던 절차를 그대로 자동화한다.
#   1. origin/dev를 fast-forward로 받는다
#   2. 메인 서버 코드가 바뀌었을 때만 앱 컨테이너를 다시 빌드한다 (DB·Redis는 건드리지 않음)
#   3. 앱이 뜰 때까지 기다리고(/actuator/health 200), 실패하면 이전 커밋으로 되돌려 다시 빌드한다
#   4. mock-bank 코드가 바뀌었거나 mock-bank 컨테이너가 떠 있지 않으면 mock-bank만 다시 빌드한다 (#204)
#      시연용 보조 서버라 실패해도 메인 서버는 되돌리지 않고, 실패 사실만 알리고 종료 코드 1로 끝낸다
#   5. .env에 DOMAIN이 있으면 HTTPS 프록시(Caddy)를 띄운다. Caddyfile이 바뀌었거나 떠 있지 않을 때만 다시 띄운다 (#218)
#      실패해도 앱은 되돌리지 않고 종료 코드 1로 알린다
# .env, src/main/resources/application.yml 같은 서버 설정 파일은 git 추적 대상이 아니라 건드리지 않는다.
#
# 사용법 (서버에서): ~/FinTarget-BE/scripts/deploy.sh
#   FORCE_BUILD=1 이면 변경 파일과 상관없이 앱과 mock-bank를 다시 빌드한다.
set -euo pipefail

# 이 스크립트는 실행 중에 git merge로 자기 자신을 갱신할 수 있다. bash가 파일을 읽어 가며 실행하다
# 바뀐 내용을 섞어 읽지 않도록, 전체를 main 함수로 감싸 끝까지 파싱한 뒤 실행한다.
main() {

  APP_DIR="${APP_DIR:-$HOME/FinTarget-BE}"
  BRANCH="${BRANCH:-dev}"
  HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
  LEGACY_HEALTH_URL="http://localhost:8080/v3/api-docs"
  HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"
  MOCK_BANK_HEALTH_URL="${MOCK_BANK_HEALTH_URL:-http://localhost:8081/}"
  # 이 경로가 바뀌었을 때만 이미지를 다시 빌드한다 (문서만 바뀐 경우는 건너뜀)
  BUILD_PATHS=(src build.gradle settings.gradle gradle gradlew Dockerfile .dockerignore docker-compose.yml)
  MOCK_BANK_BUILD_PATHS=(mock-bank/src mock-bank/build.gradle mock-bank/settings.gradle mock-bank/gradle
    mock-bank/gradlew mock-bank/Dockerfile mock-bank/.dockerignore docker-compose.yml)
  CADDY_PATHS=(deploy/Caddyfile docker-compose.yml)

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

  force=0
  [ "${FORCE_BUILD:-0}" = "1" ] && force=1

  app_build=0
  if [ "$force" = "1" ] || ! git diff --quiet "$previous" "$current" -- "${BUILD_PATHS[@]}"; then
    app_build=1
  fi

  # mock-bank는 .env에 계정이 있을 때만 띄운다. 계정이 비어 있으면 앱이 시작되지 않고 재시작을 반복하기 때문이다
  mock_bank_build=0
  if ! mock_bank_configured; then
    echo "주의: .env에 MOCK_BANK_USERNAME·MOCK_BANK_PASSWORD·BANK_WEBHOOK_KEY가 모두 있어야 mock-bank를 띄웁니다. mock-bank는 건너뜁니다." >&2
  elif [ "$force" = "1" ] \
    || ! git diff --quiet "$previous" "$current" -- "${MOCK_BANK_BUILD_PATHS[@]}" \
    || [ -z "$(docker compose ps --status running -q mock-bank 2>/dev/null)" ]; then
    mock_bank_build=1
  fi

  # HTTPS 프록시는 .env에 DOMAIN이 있을 때만 띄운다 (#218)
  caddy_deploy=0
  if [ -n "$(env_value DOMAIN)" ]; then
    if [ "$force" = "1" ] \
      || ! git diff --quiet "$previous" "$current" -- "${CADDY_PATHS[@]}" \
      || [ -z "$(docker compose --profile https ps --status running -q caddy 2>/dev/null)" ]; then
      caddy_deploy=1
    fi
  fi

  if [ "$app_build" = "0" ] && [ "$mock_bank_build" = "0" ] && [ "$caddy_deploy" = "0" ]; then
    if [ "$previous" = "$current" ]; then
      echo "새 커밋이 없어 배포를 건너뜁니다."
    else
      echo "다시 빌드할 코드 변경이 없어(문서 등) 빌드하지 않습니다."
    fi
    exit 0
  fi

  if [ "$app_build" = "1" ]; then
    deploy_app
  else
    echo "메인 서버 코드 변경이 없어 앱은 다시 빌드하지 않습니다."
  fi

  status=0
  if [ "$mock_bank_build" = "1" ]; then
    deploy_mock_bank || status=1
  fi
  if [ "$caddy_deploy" = "1" ]; then
    deploy_caddy || status=1
  fi

  # 서버 디스크가 작아(19GB) 빌드할 때마다 쌓이는 이미지·빌드 캐시를 정리한다. 최근 하루치 캐시는 다음 빌드 속도를 위해 남긴다
  docker image prune -f >/dev/null || true
  docker builder prune -f --filter until=24h >/dev/null || true
  exit "$status"
}

env_value() {
  # .env에서 값을 읽는다 (값을 출력하지 않도록 호출하는 쪽에서 비어 있는지만 확인한다)
  grep -E "^$1=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

mock_bank_configured() {
  [ -n "$(env_value MOCK_BANK_USERNAME)" ] && [ -n "$(env_value MOCK_BANK_PASSWORD)" ] && [ -n "$(env_value BANK_WEBHOOK_KEY)" ]
}

# 지정한 주소가 기대한 상태 코드 중 하나를 응답할 때까지 기다린다
wait_http() {
  local url=$1 timeout=$2
  shift 2
  local deadline=$((SECONDS + timeout)) code
  while [ "$SECONDS" -lt "$deadline" ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)
    for expected in "$@"; do
      [ "$code" = "$expected" ] && return 0
    done
    sleep 3
  done
  return 1
}

deploy_app() {
  echo "앱 컨테이너를 다시 빌드합니다."
  docker compose up -d --build --no-deps app
  # 이전 컨테이너가 아직 응답하는 순간을 성공으로 오인하지 않도록 잠시 기다린다
  sleep 10

  if wait_http "$HEALTH_URL" "$HEALTH_TIMEOUT_SECONDS" 200; then
    echo "앱 배포 성공: $(git log --oneline -1 "$current")"
    return 0
  fi

  echo "앱이 ${HEALTH_TIMEOUT_SECONDS}초 안에 응답하지 않습니다. 최근 로그:" >&2
  docker logs --tail 50 fintarget-app >&2 || true

  echo "이전 버전($(git log --oneline -1 "$previous"))으로 되돌립니다." >&2
  git reset --quiet --hard "$previous"
  docker compose up -d --build --no-deps app
  sleep 10
  # #218 이전 커밋으로 되돌린 경우 /actuator/health가 없으므로 예전 헬스 체크 주소도 확인한다
  if wait_http "$HEALTH_URL" "$HEALTH_TIMEOUT_SECONDS" 200 || wait_http "$LEGACY_HEALTH_URL" 30 200; then
    echo "롤백 완료. 실패한 커밋: $(git log --oneline -1 "$current")" >&2
  else
    echo "롤백 후에도 앱이 응답하지 않습니다. 서버를 직접 확인해 주세요." >&2
  fi
  # 롤백한 경우 mock-bank도 이전 커밋 기준이므로 더 진행하지 않는다
  exit 1
}

deploy_mock_bank() {
  echo "mock-bank 컨테이너를 다시 빌드합니다."
  docker compose up -d --build --no-deps mock-bank
  sleep 10
  # 모든 화면이 Basic Auth로 보호되어 있어, 로그인 없이 401이 오면 정상 기동으로 본다
  if wait_http "$MOCK_BANK_HEALTH_URL" 120 401 200; then
    echo "mock-bank 배포 성공"
    return 0
  fi
  echo "mock-bank가 120초 안에 응답하지 않습니다. 메인 서버 배포는 유지합니다. 최근 로그:" >&2
  docker logs --tail 50 fintarget-mock-bank >&2 || true
  return 1
}

# mock-bank HTTPS 주소(bank.<도메인>) 확인 (#230). 시연용이라 실패해도 배포는 실패로 보지 않고 경고만 남긴다
check_mock_bank_https() {
  local domain=$1 code=""
  [ -n "$(docker compose ps --status running -q mock-bank 2>/dev/null)" ] || return 0
  local deadline=$((SECONDS + 60))
  while [ "$SECONDS" -lt "$deadline" ]; do
    # 로그인 없이 401이 오면 정상이다
    code=$(curl -s -o /dev/null -w '%{http_code}' --resolve "bank.$domain:443:127.0.0.1" "https://bank.$domain/" || true)
    if [ "$code" = "401" ] || [ "$code" = "200" ]; then
      echo "mock-bank HTTPS 정상: https://bank.$domain"
      return 0
    fi
    sleep 5
  done
  echo "주의: https://bank.$domain 이 응답하지 않습니다(마지막 응답 $code). bank.$domain DNS가 이 서버를 가리키는지 확인하세요." >&2
}

deploy_caddy() {
  local domain
  domain=$(env_value DOMAIN)
  echo "HTTPS 프록시(Caddy)를 띄웁니다: $domain"
  docker compose --profile https up -d --no-deps caddy
  # 처음 띄울 때는 인증서 발급에 수십 초가 걸린다. 서버 자신의 공인 IP로 돌아오지 않도록 127.0.0.1로 접속해 확인한다
  local deadline=$((SECONDS + 180)) code
  while [ "$SECONDS" -lt "$deadline" ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --resolve "$domain:443:127.0.0.1" "https://$domain/actuator/health" || true)
    if [ "$code" = "200" ]; then
      echo "HTTPS 프록시 정상: https://$domain"
      check_mock_bank_https "$domain"
      return 0
    fi
    sleep 5
  done
  echo "https://$domain 이 180초 안에 응답하지 않습니다. DNS가 이 서버를 가리키는지, 보안 그룹에서 80·443이 열려 있는지 확인하세요. 최근 로그:" >&2
  docker logs --tail 50 fintarget-caddy >&2 || true
  return 1
}

main "$@"
exit $?
