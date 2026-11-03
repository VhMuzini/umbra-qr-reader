#!/usr/bin/env bash
# Builds Umbra inside Docker, so the host only needs Docker.
#
# Usage:
#   docker/build.sh                  # run tests and lint, then build debug and release APKs
#   docker/build.sh <gradle tasks>   # run specific Gradle tasks, e.g. docker/build.sh assembleDebug
#
# APKs land in app/build/outputs/apk/, owned by the current user.
set -euo pipefail

IMAGE="umbra-builder"
# Android build-tools only ship x86_64 Linux binaries; ARM hosts (e.g. Apple Silicon) emulate.
PLATFORM="linux/amd64"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${ROOT}/.docker-cache"

DEFAULT_TASKS=(
    :core:test
    :app:testDebugUnitTest
    :app:lintDebug
    :app:assembleDebug
    :app:assembleRelease
)
if [[ $# -gt 0 ]]; then
    TASKS=("$@")
else
    TASKS=("${DEFAULT_TASKS[@]}")
fi

echo "==> Building toolchain image '${IMAGE}'"
docker build --platform "${PLATFORM}" --tag "${IMAGE}" "${ROOT}/docker"

mkdir -p "${CACHE}/gradle" "${CACHE}/android"

MOUNTS=(--volume "${ROOT}:/workspace")
# A host local.properties points sdk.dir at a host path; hide it so the container SDK is used.
if [[ -f "${ROOT}/local.properties" ]]; then
    MOUNTS+=(--volume /dev/null:/workspace/local.properties:ro)
fi

TTY_FLAGS=()
if [[ -t 1 ]]; then
    TTY_FLAGS=(--tty)
fi

echo "==> Running: gradlew ${TASKS[*]}"
docker run --rm "${TTY_FLAGS[@]}" \
    --platform "${PLATFORM}" \
    --user "$(id -u):$(id -g)" \
    "${MOUNTS[@]}" \
    --env GRADLE_USER_HOME=/workspace/.docker-cache/gradle \
    --env ANDROID_USER_HOME=/workspace/.docker-cache/android \
    --env HOME=/workspace/.docker-cache \
    "${IMAGE}" \
    ./gradlew --no-daemon "${TASKS[@]}"

echo "==> Done. APKs:"
find "${ROOT}/app/build/outputs/apk" -name '*.apk' 2>/dev/null | sed "s|^${ROOT}/||" || true
