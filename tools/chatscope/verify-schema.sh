#!/usr/bin/env bash
set -Eeuo pipefail

PROFILE="${1:-${SPRING_PROFILES_ACTIVE:-local-consul}}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

main() {
  require_cmd mvn

  if [ "${CHATSCOPE_LOAD_DOTENV:-true}" != "false" ] && [ -f .env ]; then
    set -a
    source .env
    set +a
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local java_25_home
    java_25_home="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    if [ "${java_25_home}" != "" ]; then
      export JAVA_HOME="${java_25_home}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
  fi

  mvn -q spring-boot:run \
    -Dspring-boot.run.profiles="${PROFILE}" \
    -Dspring-boot.run.arguments="--spring.config.additional-location=optional:file:configs/chatscope-backend/application-local.yml --spring.flyway.enabled=false --spring.main.web-application-type=none --spring.main.banner-mode=off --chatscope.schema.verify.enabled=true"
}

main "$@"
