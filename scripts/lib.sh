# Shared helpers for the jcraft scripts. Source, don't run.

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# setting NAME DEFAULT: value from the environment, else from .env, else DEFAULT.
setting() {
  local value="${!1:-}"
  if [[ -z "$value" && -f .env ]]; then
    value="$(grep -E "^$1=" .env | tail -n1 | cut -d= -f2-)"
  fi
  printf '%s' "${value:-$2}"
}

confirm() {
  local answer
  read -r -p "$1 Type 'yes' to continue: " answer
  [[ "$answer" == yes ]] || { echo "Aborted."; exit 1; }
}

# Docker Compose where Docker is installed (Pi), podman-compose otherwise (desktop).
if command -v docker >/dev/null; then
  compose_cmd="docker compose"
else
  compose_cmd="podman-compose"
fi
compose() { $compose_cmd "$@"; }
