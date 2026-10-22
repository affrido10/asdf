# PaperScan — Android document scanner

Android application for scanning paper documents to PDF and exporting pages to Word with OCR.

## Features

- Document capture and image import through Google ML Kit Document Scanner.
- Automatic page detection, cropping, and perspective correction in the scanner UI.
- Local PDF library with open, export, share, and delete actions.
- Russian and English OCR via Tesseract 5 (`tesseract4android` 4.9.0). Language models are downloaded over HTTPS on first use and cached locally.
- Two Word export modes:
  - **Editable Word** — recognized lines become real text, positioned to approximate their locations on the original page.
  - **Exact copy** — each rendered PDF page is embedded as an image, with OCR text in a hidden layer for search/copy in compatible editors.
- OCR text preview before sharing the generated Word file.

## Build APK on GitHub

The repository includes GitHub Actions at `.github/workflows/build.yml`. It uses the official Android SDK setup action and builds with JDK 17 and Gradle 8.9.

1. Create a new GitHub repository.
2. Upload the contents of this folder to the **repository root** (so `.github/workflows/build.yml` is at the top level).
3. Open **Actions** in the GitHub repository. A debug APK is built on each push, pull request, or manual run.
4. Open a workflow run and download the `PaperScan-debug-apk` artifact. It contains `app-debug.apk`.
5. To publish an APK under **Releases**, push a version tag, for example:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

The Release job attaches the debug-signed APK automatically. This is suitable for testing and manual installation, not for Google Play publication. A Play Store release should use a separately configured release signing key stored in GitHub Secrets.

## Local build

Open the project root in Android Studio and sync Gradle. The packaged `gradlew` script requires the standard Gradle Wrapper JAR; this archive uses the GitHub Actions workflow to provision Gradle in CI. If Android Studio reports a missing Wrapper JAR, choose a locally installed Gradle distribution or regenerate the wrapper using `gradle wrapper --gradle-version 8.9`. The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 17, and Android SDK 35. Internet access is needed to download Gradle dependencies and the OCR language models on the first export.

## Limitations

- Exact-copy mode preserves appearance by using full-page images; page text is not fully reconstructed as native Word paragraphs or tables.
- Editable mode reconstructs recognized text lines, but does not preserve photos, seals, illustrations, or original table semantics. OCR results need review.
- Export currently supports PDFs with up to 40 pages to limit memory use.
- GitHub Actions builds the APK after the project is pushed to GitHub. No APK can be prebuilt in this package when the build environment has no network access to download Gradle/Android dependencies.
