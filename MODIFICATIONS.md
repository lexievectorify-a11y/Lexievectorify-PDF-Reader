# Modification notice

This is a **modified version** of Episteme Reader, copyright the original authors, distributed
under the GNU Affero General Public License v3.0 (see [LICENSE](LICENSE)).

- Upstream project: https://github.com/Aryan-Raj3112/episteme
- Modified by: lexievectorify (https://github.com/lexievectorify-a11y)
- Date of modifications: 2026-11-22
- Source for this build: https://github.com/lexievectorify-a11y/Lexievectorify-PDF-Reader

All original copyright notices and license headers have been preserved. The changes below are
additional modifications by lexievectorify; they are not claims of authorship of the original work.

| Area / files | Change | Date | Commit |
|---|---|---|---|
| `shared/src/commonMain/kotlin/com/aryan/reader/shared/pdf/PdfTtsBreakAnalysis.kt`, `PdfTextProcessing.kt`, `PdfTtsSession.kt`, `ReaderExtrasModels.kt`; `app/src/main/java/com/aryan/reader/tts/TtsUtils.kt`, `pdf/PdfViewerScreen.kt`, `pdf/UniversalDocument.kt`, `pdf/PdfHelper.kt`; associated tests | PDF TTS sentence-boundary fix: preserve hard breaks after terminal punctuation, detect headings from font size/weight, gate behaviour to PDF via `respectLineBreaks` | 2026-11-22 | `d682a71` |
| `gradlew`, directly executed `*.sh` scripts | Set executable bit (`100755`) so Linux CI can run them | 2026-11-22 | `4fb7b89` |
| `app/build.gradle.kts`, `.github/workflows/build-personal-apk.yml`, `keystore/` | Add signed, minified `ossPersonal` build, per-run versioning, and release publishing | 2026-11-22 | this fork |
| `shared/.../SharedUtilityScreens.kt`, `app/.../SharedComposables.kt`, `app/.../HomeScreen.kt`, string resources | Add "Updated by lexievectorify" credit in About dialog and OSS drawer header | 2026-11-22 | this fork |
| `app/src/main/java/com/aryan/reader/update/`, `MainActivity.kt`, `SharedComposables.kt`, manifest and FileProvider paths | In-app GitHub release updater for personal builds | 2026-11-22 | this fork |
| `CHANGELOG.md`, `MODIFICATIONS.md`, `README.md` | Fork documentation | 2026-11-22 | this fork |

Detailed descriptions are in [CHANGELOG.md](CHANGELOG.md).
