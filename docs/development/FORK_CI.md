# Build the fork APK

Push to `ci-build` to build and publish one signed **GMS mobile arm64 Release APK**
in `GeorgieWu/YumaPlayer`. The workflow also supports manual runs with `ci-build`
selected. Download the APK from the run's artifact or its GitHub prerelease.

## Configure signing once

Add these repository Actions secrets, using the same release key for every build:

| Secret | Value |
| --- | --- |
| `ANDROID_SIGNING_KEY` | Base64-encoded release keystore |
| `ALIAS` | Signing key alias |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Signing key password |

CI restores the keystore to `app/keystore/release.keystore`, lets Gradle sign the
APK, and removes the restored file even if the build fails. Missing signing
configuration fails the fork build instead of falling back to a debug key.

Optional integration secrets are passed through unchanged: `LASTFM_API_KEY`,
`LASTFM_SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`,
`CANVAS_TIDAL_TOKEN`, and `EXTRACTOR_BEARER`.

## Build and release behavior

`.github/workflows/build.yml` uses one job for setup, signing, building, artifact
upload, and prerelease publication. Its Gradle cache is writable on `ci-build`;
the build cache is enabled. Kotlin compilation runs in the Gradle process with
one worker to bound CI memory usage.

The only build task is `:app:assembleGmsMobileArm64Release`, with:

- `-PforkCi=true`: enables only this variant and restricts native ABI filters to
  `arm64-v8a` in Gradle configuration. It also enables the fork update source.
- `-PforkVersionCode=<code>`: sets the installable version code and appends
  `-fork.<code>` to the upstream version name.

The version code is `100000000 + GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT`,
continuing the fork's existing numbering. Rerunning a build gets a new version;
after 99 attempts, start a new run with a new commit. Keep the workflow file at
the same path to preserve its run-number sequence.

The output is
`app/build/outputs/apk/gmsMobileArm64/release/app-gms-mobile-arm64-release.apk`.
Actions retains the uncompressed APK artifact for 14 days. The prerelease tag is
`fork-v<versionName>`; release notes link to the source commit and workflow run.
Only the APK is uploaded. The workflow does not scan APK contents, run extra
signature checks, or rebuild for reproducibility checks.

## Update installed fork builds

Fork CI builds check and download updates from `GeorgieWu/YumaPlayer`. Install
the first APK containing this change manually from the fork's Releases page;
older installed APKs still check the upstream repository.

The updater accepts the workflow's `fork-v<versionName>` prereleases with a
matching APK asset and orders them by the numeric `-fork.<code>` suffix. It
offers only higher codes, even when the upstream version name changes. It uses
the selected release's asset URL rather than GitHub's stable-only `/latest`
endpoint. Releases without a matching APK are excluded.

The update page has one fork feed; saved upstream channel preferences do not
redirect it to upstream. Release notes, download links and commit history use
the fork (commit history follows `ci-build`). Fork release caches are separate
from upstream caches, so upgrading cannot reuse an old upstream download URL.
Keep using the same signing key and workflow version-code sequence for updates.

## Scope

Application features and upstream updates are integrated on `main`, then merged
forward into `ci-build`. `ci-build` adds the fork build, signing, release and
update-source integration. This updater integration is exclusive to `ci-build`;
do not merge this branch back into `main`.
See [the development guide](DEVELOPMENT_GUIDE.md#6-keep-fork-features-in-main)
for the branch flow.

This branch keeps only the fork build workflow. It removes the upstream
multi-variant, Debug/PR APK, Telegram, issue-closing, license-header, contributor
update, and separate release automation. This branch-specific CI configuration
does not replace the upstream workflows retained on `main`; its scheduled
workflows remain configured there.

Without `forkCi`, local builds retain the upstream variants, ABI settings,
debug-signing fallback and upstream update sources. Other application changes
come from `main`.
