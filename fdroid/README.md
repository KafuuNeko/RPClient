# F-Droid Submission Notes

This directory contains a draft fdroiddata metadata file for RPClient.

## Submit

1. Fork and clone `https://gitlab.com/fdroid/fdroiddata`.
2. Create a branch named `me.kafuuneko.rpclient`.
3. Copy `fdroid/me.kafuuneko.rpclient.yml` to `fdroiddata/metadata/me.kafuuneko.rpclient.yml`.
4. In the fdroiddata checkout, run:

```bash
fdroid readmeta
fdroid rewritemeta me.kafuuneko.rpclient
fdroid checkupdates --allow-dirty me.kafuuneko.rpclient
fdroid lint me.kafuuneko.rpclient
fdroid build me.kafuuneko.rpclient
```

5. Commit with `New App: RPClient` and open a merge request against fdroiddata.

## Current Release

- Application ID: `me.kafuuneko.rpclient`
- Version name: `2026.1.1`
- Version code: `20260101`
- Git tag: `V2026.1.1`

## Notes

- The app builds locally with `./gradlew :app:assembleRelease`.
- Unit tests pass locally with `./gradlew test`.
- The build uses Android Gradle Plugin 9.0.1, Gradle 9.5.0, and compile SDK 36.1. If fdroiddata CI does not yet provide the required Android SDK/build tools or Java runtime, the build metadata may need a follow-up adjustment.
