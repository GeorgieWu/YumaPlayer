"""Package exactly the requested Gradle variant for the fork release workflow."""

import json
import os
from pathlib import Path
import re
import shutil


def main():
    candidates = []
    for path in Path("app/build/outputs/apk").rglob("output-metadata.json"):
        metadata = json.loads(path.read_text(encoding="utf-8"))
        if metadata.get("variantName") == "gmsMobileUniversalRelease":
            candidates.append((path, metadata))
    if len(candidates) != 1:
        raise SystemExit(f"Expected one universal release metadata file, found {len(candidates)}")

    metadata_path, metadata = candidates[0]
    elements = metadata["elements"]
    if len(elements) != 1:
        raise SystemExit("Expected one universal APK")
    element = elements[0]
    version = element["versionName"]
    expected_code = 100000000 + int(os.environ["GITHUB_RUN_NUMBER"]) * 100 + int(os.environ["GITHUB_RUN_ATTEMPT"])
    if element["versionCode"] != expected_code or not version.endswith(f"-fork.{expected_code}"):
        raise SystemExit("APK version does not match this workflow run")
    if metadata["applicationId"] != "re.muwmix.yumaplayer":
        raise SystemExit("Unexpected application ID")
    if not re.fullmatch(r"[0-9A-Za-z.+-]+", version):
        raise SystemExit("Unexpected characters in APK version")

    apk = metadata_path.parent / element["outputFile"]
    destination = Path("dist")
    destination.mkdir(exist_ok=True)
    shutil.copyfile(apk, destination / "app-gms-mobile-universal-release.apk")
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    run_id = os.environ["GITHUB_RUN_ID"]
    (destination / "release-notes.md").write_text(
        f"Personal fork build of YumaPlayer: **{version}**.\n\n"
        f"- Source: https://github.com/{repository}/commit/{commit}\n"
        f"- Build: https://github.com/{repository}/actions/runs/{run_id}\n"
        f"- Variant: GMS / mobile / universal\n"
        f"- Version code: {expected_code}\n"
        "- Signed with this fork's existing release key.\n"
        "- SHA-256 checksum: see SHA256SUMS.txt.\n\n"
        "This is an automated prerelease from `ci-build`, not an upstream release.\n",
        encoding="utf-8",
    )
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"tag=fork-v{version}\nversion={version}\n")


if __name__ == "__main__":
    main()
