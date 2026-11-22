# Changelog (personal fork)

This changelog covers changes made in the personal fork of Episteme Reader maintained by
lexievectorify. Upstream project: [Aryan-Raj3112/episteme](https://github.com/Aryan-Raj3112/episteme).
The base build is upstream `1.0.59` (versionCode `65`). See [MODIFICATIONS.md](MODIFICATIONS.md)
for the per-file modification notice.

## [1.0.59-personal.1] - 2026-11-22

### Fixed

- **PDF read-aloud: last sentence of a paragraph was glued to the next heading or definition term.**
  Symptom: Text-to-speech read straight through the period at the end of a paragraph, merging the
  final sentence with the following heading with no pause. Cause: `preprocessForTts` replaced the
  newline after `.`/`?`/`!` with a space, producing text like `End.Next`, while the sentence splitter
  only breaks on whitespace after terminal punctuation. Fix: hard line breaks are now preserved when
  they follow terminal punctuation or a blank line, using PDFium font size/weight offsets
  (`PdfTtsBreakAnalysis`) to distinguish headings from soft-wrapped body text; soft wraps still
  become spaces, and the source-offset map is trimmed in lockstep. The new `respectLineBreaks` flag
  gates this behaviour to PDF call sites, so EPUB, audiobook, and AI TTS callers are unchanged.
  Tests added/updated in `PdfTextProcessingTest`, `PdfTtsBreakAnalysisTest`, `PdfTtsSessionTest`,
  `TtsChunkSourceOffsetsTest`, `ReaderExtrasModelsTest`, and `PdfHelperTest`.
  Commit: `d682a71`.

- **CI build failed with `./gradlew: Permission denied` (exit code 126).** `gradlew` and the directly
  executed shell scripts were committed from Windows without the executable bit. They are now
  tracked as mode `100755`. Commit: `4fb7b89`.

### Added

- **Personal release-quality build.** An `ossPersonal` variant that uses release R8 shrinking and
  resource shrinking, signed with a stable debug-grade key committed in `keystore/`. It is smaller
  than the debug build, has no debug-only TensorFlow Lite natives, and does not display the
  debug-only FPS overlay.
- **Automatic build versioning.** CI passes `-PpersonalBuildNumber=<github.run_number>`; each build
  gets `versionCode = 1000 + run_number` and `versionName 1.0.59-oss-b<run_number>`.
- **In-app updater.** The OSS edition checks GitHub Releases for a newer personal build, offers an
  Update dialog with release notes, downloads the APK, verifies its package name and signing
  certificate, and opens the system installer.
- **Modification credit.** "Updated by lexievectorify" is shown in the About dialog and the OSS
  drawer header.

### Changed

- The personal workflow publishes a normal GitHub release with the APK attached directly, so the
  file can be downloaded and installed without extracting an artifact archive.
- Release notes are taken from the newest section of this changelog.
