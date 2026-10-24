# Shared development signing key

`development.keystore` is a public, project-specific development key intended to
be committed to Git. Both `debug` and `demo` use it so local and CI builds have
the same signing certificate.

- Store password: `android`
- Key alias: `androiddebugkey`
- Key password: `android`

Build with `./gradlew :app:assembleDebug :app:assembleDemo`.
Both variants use the `.dev` application ID suffix and can replace each other
when Android's version requirements are met. An app installed with a different
development certificate requires a one-time uninstall before installing these
builds.

Keep this key stable; regenerating it changes the signing certificate.
It is only for development builds. Release builds use the separate signing
configuration supplied through `RELEASE_*` properties or environment variables.
