"""Require and package all four mobile variants for one fork release."""

import json
import os
from pathlib import Path
import re
import shutil


VARIANTS = {
    "gmsMobileArm64Release": "app-gms-mobile-arm64-release.apk",
    "gmsMobileUniversalRelease": "app-gms-mobile-universal-release.apk",
    "fossMobileArm64Release": "app-foss-mobile-arm64-release.apk",
    "fossMobileUniversalRelease": "app-foss-mobile-universal-release.apk",
}


def main():
    candidates = {variant: [] for variant in VARIANTS}
    for path in Path("app/build/outputs/apk").rglob("output-metadata.json"):
        metadata = json.loads(path.read_text(encoding="utf-8"))
        variant = metadata.get("variantName")
        if variant in candidates:
            candidates[variant].append((path, metadata))

    expected_code = 100000000 + int(os.environ["GITHUB_RUN_NUMBER"]) * 100 + int(os.environ["GITHUB_RUN_ATTEMPT"])
    packages = []
    versions = set()
    for variant, matches in candidates.items():
        if len(matches) != 1:
            raise SystemExit(f"Expected one metadata file for {variant}, found {len(matches)}")
        metadata_path, metadata = matches[0]
        elements = metadata["elements"]
        if len(elements) != 1:
            raise SystemExit(f"Expected one APK for {variant}")
        element = elements[0]
        version = element["versionName"]
        if element["versionCode"] != expected_code or not version.endswith(f"-fork.{expected_code}"):
            raise SystemExit(f"APK version for {variant} does not match this workflow run")
        if metadata["applicationId"] != "re.muwmix.yumaplayer":
            raise SystemExit(f"Unexpected application ID for {variant}")
        if not re.fullmatch(r"[0-9A-Za-z.+-]+", version):
            raise SystemExit(f"Unexpected characters in APK version for {variant}")
        versions.add(version)
        packages.append((metadata_path.parent / element["outputFile"], VARIANTS[variant]))
    if len(versions) != 1:
        raise SystemExit("All four APKs must have the same version name")
    version = versions.pop()
    destination = Path("dist")
    destination.mkdir(exist_ok=True)
    for apk, name in packages:
        shutil.copyfile(apk, destination / name)
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    run_id = os.environ["GITHUB_RUN_ID"]
    (destination / "release-notes.md").write_text(
        f"Personal fork build of YumaPlayer: **{version}**.\n\n"
        f"- Source: https://github.com/{repository}/commit/{commit}\n"
        f"- Build: https://github.com/{repository}/actions/runs/{run_id}\n"
        "- Variants: GMS arm64, GMS universal, FOSS arm64, FOSS universal (mobile).\n"
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
