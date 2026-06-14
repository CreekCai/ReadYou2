# Self-hosted APK Updates

This project can publish APK updates through GitHub Releases. A tag such as `v0.17.4` triggers `.github/workflows/release-apk.yml`, builds the `githubRelease` APK, and uploads two files to the release:

- `ReadYou-<version>-<commit>.apk`
- `latest.json`

The app-side updater can fetch:

```text
https://github.com/<owner>/<repo>/releases/latest/download/latest.json
```

## GitHub Secrets

Add these repository secrets before creating a release tag:

- `SIGNING_KEY_BASE64`: base64 encoded release keystore file.
- `SIGNING_KEY_ALIAS`: keystore alias.
- `SIGNING_KEY_PASSWORD`: key password.
- `SIGNING_STORE_PASSWORD`: store password.

On Windows PowerShell, create the base64 value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("signature\reader.keystore"))
```

Keep the keystore and passwords private. Android upgrades only work when the new APK is signed with the same key as the installed APK.

## Release Flow

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit the code.
3. Push a version tag:

```bash
git tag v0.17.4
git push origin v0.17.4
```

4. GitHub Actions builds and attaches the APK plus `latest.json` to the GitHub Release.

## Update Metadata

`latest.json` has this shape:

```json
{
  "versionName": "0.17.4",
  "versionCode": 59,
  "tagName": "v0.17.4",
  "apkName": "ReadYou-0.17.4-abcdef0.apk",
  "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v0.17.4/ReadYou-0.17.4-abcdef0.apk",
  "sha256": "<apk sha256>",
  "releaseNotesUrl": "https://github.com/<owner>/<repo>/releases/tag/v0.17.4",
  "publishedAt": "2026-06-14T00:00:00Z"
}
```

The app should compare `versionCode` with `BuildConfig.VERSION_CODE`, download `apkUrl`, verify `sha256`, and launch the Android package installer.
