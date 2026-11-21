# F-Droid preparation

VolumeDot is licensed under Apache-2.0. Its public upstream is https://github.com/ConstantineAshik/VolumeDOT. Release source and tags are maintained in that repository. F-Droid inclusion is pending the signing and server-validation steps below.

## Prepared locally

- Full Kotlin/Compose app source and Gradle wrapper, with static versionName 3.0.2 / versionCode 10.
- Apache-2.0 LICENSE, first-party Kotlin SPDX notices, and NOTICE retaining the Google Material Symbols attribution.
- Fastlane English listing, changelogs, launcher icon and four screenshots under fastlane/metadata/android/en-US. Screenshots are native Robolectric renders of the real app UI, not physical-device captures.
- Candidate metadata: fdroid/metadata/com.ashik.floatvol.yml. The filename retains the installed application ID; changing that ID would create a different Android app.
- Optional local signing.properties, excluded with keystores, local.properties, build caches and APKs from source control. The example contains placeholders only.
- Release builds with -Pfdroid=true never load private signing credentials. CI produces an unsigned release for packaging, alongside a normally debug-signed debug build.

## Command-line build

Install a FOSS OpenJDK (17 for release compilation; 25 for the test suite), Android SDK platform android-37.1 and build-tools 36.0.0. Set ANDROID_HOME to the SDK. Android Studio is optional.

    ./gradlew assembleRelease -Pfdroid=true
    ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug -Pfdroid=true

Unsigned output: app/build/outputs/apk/release/app-release-unsigned.apk. An unsigned APK cannot be installed until signed.

For personal signed builds, copy signing.properties.example to signing.properties and supply an existing private keystore and credentials locally. Never commit either file containing real credentials or the keystore.

## Dependency review

Direct runtime dependencies are AndroidX, Kotlin and Google Material Components (Apache-2.0 projects). The resolved release dependency report was inspected for Firebase, Google Play Services, advertising and billing integrations; none were found. The app declares no INTERNET permission. Material Symbols are distributed with their Apache-2.0 license. This local review does not replace F-Droid's source/dependency scan.

## Required before submission

1. Review and publish the actual source, license and Fastlane files to the upstream repository. Commit the release source and create the v3.0.2 tag at that commit. Do not move an existing published tag.
2. Replace the candidate recipe's commit with that release's full commit hash. Remove its disable field only after the public source and build have been checked.
3. Decide signing before first inclusion. The candidate uses F-Droid signing, which cannot update an APK signed with the existing developer key. To preserve updates across distribution channels, first establish reproducible builds, publish a developer-signed reference APK, and configure Binaries and AllowedAPKSigningKeys. Do not publish the private key. Reproducibility is not established by this preparation.
4. On a supported Linux fdroidserver environment, copy the candidate to fdroiddata/metadata, then run fdroid readmeta, fdroid rewritemeta com.ashik.floatvol, fdroid lint com.ashik.floatvol and fdroid build com.ashik.floatvol. Verify SDK 37.1 / AGP 9.5.1 / Gradle 9.9.1 support on the actual server image.
5. Submit the reviewed metadata in a new-app merge request to fdroiddata and follow maintainer feedback.

No F-Droid server build or inclusion merge request has been performed. Windows has no configured Linux container/WSL build environment here. See QA_REPORT.md for local Android verification and remaining physical-device checks.
