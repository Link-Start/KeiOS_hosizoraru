#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "Usage: $0 <variant> <target-path> <secret-env-name> <expected-package>" >&2
  exit 2
fi

VARIANT="$1"
TARGET_PATH="$2"
SECRET_ENV_NAME="$3"
EXPECTED_PACKAGE="$4"
SECRET_VALUE="${!SECRET_ENV_NAME:-}"
ALLOW_STUB="${ALLOW_GOOGLE_SERVICES_STUB:-false}"

if [[ -s "${TARGET_PATH}" ]]; then
  echo "Firebase config for ${VARIANT} already exists at ${TARGET_PATH}."
else
  if [[ -z "${SECRET_VALUE}" ]]; then
    if [[ "${ALLOW_STUB}" == "true" ]]; then
      echo "::warning title=Firebase config stub::${SECRET_ENV_NAME} is unavailable; writing a local CI stub for ${VARIANT}."
      mkdir -p "$(dirname "${TARGET_PATH}")"
      python3 - "${TARGET_PATH}" "${EXPECTED_PACKAGE}" "${VARIANT}" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
package_name = sys.argv[2]
variant = sys.argv[3]
safe_variant = "".join(ch if ch.isalnum() else "-" for ch in variant.lower()).strip("-") or "local"

data = {
    "project_info": {
        "project_number": "0",
        "project_id": f"keios-{safe_variant}-ci",
        "storage_bucket": f"keios-{safe_variant}-ci.appspot.com",
    },
    "client": [
        {
            "client_info": {
                "mobilesdk_app_id": "1:0:android:0000000000000000000000",
                "android_client_info": {
                    "package_name": package_name,
                },
            },
            "api_key": [
                {
                    "current_key": "AIzaSyDUMMY_LOCAL_CI_ONLY_000000000000000",
                }
            ],
            "services": {
                "analytics_service": {
                    "status": 1,
                },
                "appinvite_service": {
                    "status": 1,
                },
                "ads_service": {
                    "status": 1,
                },
            },
        }
    ],
    "configuration_version": "1",
}
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
    else
      echo "::error title=Missing Firebase config::Set ${SECRET_ENV_NAME} as a base64-encoded google-services.json secret." >&2
      exit 1
    fi
  else
    mkdir -p "$(dirname "${TARGET_PATH}")"
    TMP_FILE="$(mktemp)"
    trap 'rm -f "${TMP_FILE}"' EXIT

    if printf '%s' "${SECRET_VALUE}" | base64 --decode > "${TMP_FILE}" 2>/dev/null; then
      cp "${TMP_FILE}" "${TARGET_PATH}"
    else
      printf '%s' "${SECRET_VALUE}" > "${TARGET_PATH}"
    fi
  fi
fi

python3 - "${TARGET_PATH}" "${EXPECTED_PACKAGE}" "${VARIANT}" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
expected_package = sys.argv[2]
variant = sys.argv[3]

data = json.loads(path.read_text(encoding="utf-8"))
packages = {
    client.get("client_info", {})
    .get("android_client_info", {})
    .get("package_name")
    for client in data.get("client", [])
}
packages.discard(None)

if expected_package not in packages:
    joined = ", ".join(sorted(packages)) or "<none>"
    raise SystemExit(
        f"Firebase config for {variant} must contain {expected_package}; found {joined}"
    )

print(f"Firebase config for {variant} contains {expected_package}.")
PY
