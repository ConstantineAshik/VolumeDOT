# F-Droid preparation

VolumeDot is licensed under Apache-2.0. Public source: https://github.com/ConstantineAshik/VolumeDOT.

## Published release

Version 3.0.2 (versionCode 10) is tagged v3.0.2 at a75491ee2eacfa2cdc0442adbf4ba3bb08b978b1. The source includes the full Kotlin/Compose implementation, Gradle wrapper, license and Fastlane listing assets.

Release APK and checksum: https://github.com/ConstantineAshik/VolumeDOT/releases/tag/v3.0.2.

Private signing credentials, the keystore, local SDK paths, caches and generated APKs are excluded from Git. The initial publication was checked against the actual local signing passwords before pushing.

## Command-line build

Install FOSS OpenJDK (17 for release compilation; 25 for the test suite), Android SDK platform android-37.1 and build-tools 36.0.0. Set ANDROID_HOME to the SDK. Android Studio is optional.

    ./gradlew assembleRelease -Pfdroid=true
    ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug -Pfdroid=true

Unsigned output: app/build/outputs/apk/release/app-release-unsigned.apk. An unsigned APK needs signing before installation. The fdroid property prevents local private signing credentials from being loaded, even when present.

For personal signed builds, copy signing.properties.example to signing.properties and supply an existing private keystore and credentials locally. Never commit either file containing real credentials or the keystore.

## Metadata and assets

Candidate: fdroid/metadata/com.ashik.floatvol.yml. The filename retains the installed application ID so existing Android installations can update. The build pins the full release source hash.

Fastlane English descriptions, changelogs, a 512px launcher icon and four screenshots are in fastlane/metadata/android/en-US. Screenshots are native Robolectric renders of the real UI, not physical-device captures.

GitHub Actions runs the real fdroid readmeta and fdroid lint commands on Linux with fdroidserver 2.4.5 and official fdroiddata category definitions. These checks passed during publication. This is metadata validation, not a full official F-Droid server build.

## Signing and reproducibility

The candidate uses Binaries and AllowedAPKSigningKeys to request verification of the published developer-signed APK. This preserves the existing developer certificate for Android updates; no private key is shared. F-Droid will only distribute this APK if its own rebuild matches.

The local signed/unsigned builds passed apksigcopier signature-copy verification. Reproducibility on the actual F-Droid server is still pending, so the candidate build remains explicitly disabled. Do not remove that safeguard based on metadata lint alone.

## Dependency review

Direct runtime dependencies are AndroidX, Kotlin and Google Material Components (Apache-2.0 projects). The resolved release dependency report was inspected for Firebase, Google Play Services, advertising and billing integrations; none were found. The app declares no INTERNET permission. Material Symbols retain their Apache-2.0 license. This local review does not replace F-Droid's source/dependency scan.

## Remaining inclusion steps

1. In a supported Linux fdroidserver environment, copy the candidate into fdroiddata/metadata and run fdroid readmeta, fdroid rewritemeta com.ashik.floatvol, fdroid lint com.ashik.floatvol and fdroid build com.ashik.floatvol. For an intentional verification build of the disabled candidate, use the tool's --force option; do not enable automatic publishing until verification passes.
2. Confirm SDK 37.1 / AGP 9.5.1 / Gradle 9.9.1 availability in that server image and signature-copy reproducibility against the published APK.
3. Remove the disable field after successful server validation and submit the reviewed recipe to fdroiddata using an authenticated GitLab account. No inclusion merge request has been submitted from this workspace.
4. Follow maintainer review and any requested changes. Acceptance and repository publication are controlled by F-Droid maintainers.

See QA_REPORT.md for local Android tests and remaining physical-device checks.
