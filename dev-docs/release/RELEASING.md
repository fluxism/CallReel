# Releasing CallReel

## One-time setup

1. **Signing key.** Create a keystore and keep it, with its password, somewhere safe and backed up, outside the repository folder. Every later release must be signed with the same key, or users cannot update.
   ```sh
   keytool -genkeypair -v -keystore callreel-release.jks -alias callreel -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=fluxism"
   ```
   - The `-dname` value is public. It is embedded in every APK and cannot be changed after the first release. Without `-dname`, keytool asks for your first and last name and stores the answer there.
   - The keystore has a single password. keytool does not ask for a separate key password, so `SIGNING_KEY_PASSWORD` has the same value as `SIGNING_KEYSTORE_PASSWORD`.
2. **Repository secrets** (Settings, Secrets and variables, Actions, section **Repository secrets**):
   - `SIGNING_KEYSTORE`: the keystore, base64 encoded (`base64 -w0 callreel-release.jks`)
   - `SIGNING_KEYSTORE_PASSWORD` and `SIGNING_KEY_PASSWORD`: the keystore password
   - `SIGNING_KEY_ALIAS`: `callreel`

   They must be repository secrets. The workflow does not use an environment, so values stored under "Environment secrets" are empty during the build. The build then fails with `Tag number over 30 is not supported`, which is what Java reports for an empty keystore file.

   With the GitHub CLI (the last two ask for the value):
   ```sh
   base64 -w0 callreel-release.jks | gh secret set SIGNING_KEYSTORE -R fluxism/CallReel
   gh secret set SIGNING_KEY_ALIAS -R fluxism/CallReel --body callreel
   gh secret set SIGNING_KEYSTORE_PASSWORD -R fluxism/CallReel
   gh secret set SIGNING_KEY_PASSWORD -R fluxism/CallReel
   ```
3. **Enable workflows.** GitHub disables workflows on a fork until the owner enables them once in the Actions tab.

The release certificate has the SHA-256 fingerprint `b46154325555f1fb504ea3f7af072588d4b03bdfca6ea4f9345fa42e4d82ed9a`.

## Tags

Release tags are `v<versionName>`. The original project uses the same tag names for its own releases, so its tags must not exist in this repository: a release would attach to the original project's commit instead of CallReel's. Fetch the upstream remote without tags:

```sh
git config remote.upstream.tagOpt --no-tags
```

## Each release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`. Keep them hard-coded: F-Droid reads them from that file.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Merge to `main`.
4. Run the **Build App** workflow with release type **Release**. It builds and signs `CallReel-<versionName>.apk`, attests it, and creates a **draft** release named `v<versionName>`, pinned to the commit that was built.
5. Review the draft release and publish it. Publishing creates the tag.
6. F-Droid and IzzyOnDroid pick up the new tag and APK by themselves once the app is listed.

To check a downloaded APK:

```sh
apksigner verify --print-certs CallReel-<versionName>.apk
gh attestation verify CallReel-<versionName>.apk -R fluxism/CallReel
```

## Listing rules that come from the license

The additional terms at the end of `LICENSE` do not allow the name of the original project, its author's name or its logo to be used for publicity, "specifically in Google Play Store or other app store listings", without the permission of the original author. For that reason `fastlane/metadata` describes CallReel as a fork without naming the original. The app itself and the README name and link the original, as the same terms require. If you want to name the original in a store listing, ask its author first.

## IzzyOnDroid

Requirements: a signed APK attached to GitHub releases, and the `fastlane/metadata` in this repository. Submit an inclusion request at https://gitlab.com/IzzyOnDroid/repo/-/issues with the text in [izzyondroid-request.md](izzyondroid-request.md).

## F-Droid

F-Droid builds the app from source on its own servers. The recipe in [io.github.fluxism.callreel.yml](io.github.fluxism.callreel.yml) also asks it to compare its build with the APK on the GitHub release (`Binaries`) and, when the two match, to publish that APK with the CallReel signature (`AllowedAPKSigningKeys`). Users can then move between the GitHub and F-Droid builds without reinstalling. This only works while the build stays reproducible; a rebuild of 1.0.0 on a second machine matched the released APK file for file.

Read the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) first. Two of its quality criteria matter for CallReel:

- **Age.** An app should have been actively developed for at least three months, unless it is shown to have users or its author already maintains apps on F-Droid. CallReel's own history starts on 2026-11-21. Reviewers may ask to wait or ask for evidence of users, such as a listing on IzzyOnDroid or download counts on the GitHub releases.
- **Unique value.** A fork must offer more than a rebrand over the app it comes from. Say what CallReel adds: video call recording.

Steps:

1. Publish the GitHub release. The recipe downloads the APK from it.
2. Create an account on https://gitlab.com and fork https://gitlab.com/fdroid/fdroiddata. The fork must stay public.
3. In the fork, create a branch named `io.github.fluxism.callreel`. Do not commit to `master`.
4. On that branch, add the file `metadata/io.github.fluxism.callreel.yml` with the exact contents of [io.github.fluxism.callreel.yml](io.github.fluxism.callreel.yml). The GitLab website is enough for this: open the `metadata` directory on the branch and choose "New file". Add nothing else.
5. Wait for the pipeline of that commit (Build, Pipelines in the fork). It checks the recipe, builds the app and compares the result with the release APK. If GitLab asks for a phone number or a credit card before it runs pipelines, do not give one: say so in the merge request and the F-Droid team starts the pipeline.
6. Open a merge request from the branch to `master` of `fdroid/fdroiddata`, with the title `New app: CallReel`. GitLab fills in a template with questions and a checklist; [fdroid-merge-request.md](fdroid-merge-request.md) has the answers.
7. Answer the reviewers' questions in the merge request. They usually reply within days; requests for changes to the recipe are normal.
8. After the merge, the app appears in the F-Droid client with the next build cycle, usually within a few days.

Later releases need no merge request. With `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`, F-Droid finds each new `v<versionName>` tag by itself, as long as `versionCode` and `versionName` stay hard-coded in `app/build.gradle.kts` and the release has the APK attached as `CallReel-<versionName>.apk`.
