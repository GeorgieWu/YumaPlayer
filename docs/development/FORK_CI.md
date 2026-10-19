# Personal fork builds

The `ci-build` branch owns the build and release adaptations for
`GeorgieWu/YumaPlayer`. Keep `main` for upstream merges and functional fixes;
merge `main` into `ci-build`, never the other way around.

## Trigger and outputs

Every push to `ci-build` runs `.github/workflows/build.yml`. The workflow builds
one GMS mobile universal release APK with JDK 21 and the project's Android SDK
and Gradle versions. It uses the existing release signing key through Gradle,
verifies the APK signature, and publishes a GitHub prerelease with the APK,
SHA-256 checksum and source commit link. An Actions artifact is retained for
14 days. Release assets are retained until explicitly removed.

The workflow deliberately has no `workflow_dispatch` trigger: it lives only on
`ci-build`, while GitHub requires that trigger's workflow to exist on the default
branch. To rebuild, use Actions' rerun controls on an existing run, or push a new
commit to `ci-build`.

The build job has read-only repository permissions. Only the publishing job
gets `contents: write`. Neither job sends Telegram messages or comments on
issues. Upstream's other workflow files are removed on this branch. The default
branch's existing workflows are unchanged; scheduled/default-branch workflows
are not disabled repository-wide by removing their files here.

## Existing repository secrets

Required under **Settings → Secrets and variables → Actions**:

| Secret | Content |
| --- | --- |
| `ANDROID_SIGNING_KEY` | Base64-encoded existing release keystore |
| `ALIAS` | Existing signing alias |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Signing key password |

The workflow fails if the signing configuration is missing or invalid. The
keystore is restored only on the runner and removed in an `always()` cleanup
step. Do not replace this key between releases or commit it to Git.

`LASTFM_API_KEY`, `LASTFM_SECRET`, `TOGETHER_BEARER_TOKEN`,
`CANVAS_BEARER_TOKEN`, and `EXTRACTOR_BEARER` remain optional. Without them,
their associated integrations may be unavailable; they are not required to
compile the APK. No upstream credentials are copied into this fork.

## Versions and installation

The application ID remains `re.muwmix.yumaplayer`. Updating an existing install
requires that it was signed with the same key. This build does not install
alongside another app with that application ID.

CI passes `forkVersionCode = 100000000 + GITHUB_RUN_NUMBER * 100 +
GITHUB_RUN_ATTEMPT` to Gradle. The APK version name appends `-fork.<code>` to
the upstream version. The release tag is `fork-v<APK version name>`. The commit
SHA and build URL are recorded in release notes. Keep the workflow file's
identity and version code scheme stable; resetting the workflow run counter
requires explicitly planning a higher version code range. After 99 attempts,
push a new commit instead of rerunning the old one.

In this branch, both existing app update channels point to the same fork build
stream and select `fork-v` releases, including prereleases. There is no separate
canary package. Asset names retain the existing updater convention:
`app-gms-mobile-universal-release.apk`. Update cache keys are namespaced for this
fork so an earlier installation's cached upstream releases are not reused.

## Syncing new code

Starting from a clean tracked working tree:

```powershell
git switch ci-build
git merge main
git push georgiewu ci-build
```

Review workflow and Gradle conflicts when merging upstream changes. Keep the
fork workflow, signing checks, version configuration and update source. Git may
report modify/delete conflicts for upstream workflow files intentionally removed
on this branch; resolve them explicitly after reviewing upstream changes.

Download the resulting APK from
<https://github.com/GeorgieWu/YumaPlayer/releases>. Build logs and short-lived
artifacts are at <https://github.com/GeorgieWu/YumaPlayer/actions>.
