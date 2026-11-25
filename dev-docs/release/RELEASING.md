# Releasing CallReel

## One-time setup

1. **Signing key.** Create a keystore and keep it, with its passwords, somewhere safe and backed up. Every later release must be signed with the same key, or users cannot update.
   ```sh
   keytool -genkeypair -v -keystore callreel-release.jks -alias callreel -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Repository secrets** (Settings, Secrets and variables, Actions):
   - `SIGNING_KEYSTORE`: the keystore, base64 encoded (`base64 -w0 callreel-release.jks`)
   - `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
3. **Enable workflows.** GitHub disables workflows on a fork until the owner enables them once in the Actions tab.

## Each release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`. Keep them hard-coded: F-Droid reads them from that file.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Merge to `main`.
4. Run the **Build App** workflow with release type **Release**. It builds and signs `CallReel-<versionName>.apk`, attests it, and creates a **draft** release tagged `v<versionName>`.
5. Review the draft release and publish it.
6. For F-Droid, add a build entry for the new version to the recipe (see below) if automatic updates are not enabled in it.

## Listing rules that come from the license

The additional terms at the end of `LICENSE` do not allow the name of the original project, its author's name or its logo to be used for publicity, "specifically in Google Play Store or other app store listings", without the permission of the original author. For that reason `fastlane/metadata` describes CallReel as a fork without naming the original. The app itself and the README name and link the original, as the same terms require. If you want to name the original in a store listing, ask its author first.

## IzzyOnDroid

Requirements: a signed APK attached to GitHub releases, and the `fastlane/metadata` in this repository. Submit an inclusion request at https://gitlab.com/IzzyOnDroid/repo/-/issues with the text in [izzyondroid-request.md](izzyondroid-request.md).

## F-Droid

Submit a merge request to https://gitlab.com/fdroid/fdroiddata that adds [com.fluxism.callreel.yml](com.fluxism.callreel.yml) as `metadata/com.fluxism.callreel.yml`. Fill in the commit of the release and, for reproducible builds, the SHA-256 of the signing certificate. The recipe follows the one F-Droid uses for the original app.
