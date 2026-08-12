# 星标知识库方案 2 — Design QA

- Source visual truth: `C:\Users\Administrator\.codex\generated_images\019fd72a-db7d-77f3-8a18-d2b7d7d49bbf\exec-8fb20b9e-5e65-4726-9e87-48056096ccc8.png`
- Implementation target: `app/src/main/java/me/ash/reader/ui/page/home/knowledge/KnowledgeQaPage.kt`
- Intended viewport: Android mobile, 390 × 844 dp-class viewport
- Source pixels: 390 × 844 target composition (generated result is a higher-density raster)
- Implementation pixels/CSS size/density: unavailable; Android emulator could not boot
- State: active knowledge-base question and answer, light theme

## Full-view comparison evidence

The source visual was opened and used as the selected implementation target. A matching implementation screenshot could not be captured because the available Windows host does not expose virtualization extensions to the Android emulator. The emulator exits with: `x86_64 emulation currently requires hardware acceleration`.

## Focused-region comparison evidence

Blocked for the same reason. Code-level checks confirm the intended regions exist (right-aligned prompt, left-aligned answer identity and body, expandable sources, fixed composer), but code inspection is not accepted as visual comparison evidence.

## Findings

- [P1] Rendered fidelity remains unverified.
  - Location: full knowledge Q&A screen.
  - Evidence: source visual is available; implementation screenshot is unavailable.
  - Impact: typography wrapping, viewport density, bottom composer height, and long-answer spacing may still differ on a real device.
  - Fix: install the generated debug APK on a physical Android device or a virtualization-enabled emulator, capture the active Q&A state at a 390 × 844-class viewport, and compare it together with the source visual.

## Required fidelity surfaces

- Fonts and typography: implemented with the app's Material 3 typography; rendered weight, wrapping, and optical hierarchy remain unverified.
- Spacing and layout rhythm: structure follows the selected source; real-device vertical rhythm and persistent composer clearance remain unverified.
- Colors and visual tokens: uses the app's semantic Material color scheme and dark-theme-aware logo treatment; rendered contrast remains unverified.
- Image quality and asset fidelity: no raster imagery is required; icons use the existing Material icon set.
- Copy and content: selected source hierarchy is represented with Chinese app copy and realistic knowledge-base states.

## Comparison history

- Initial implementation: source visual resolved and Compose implementation completed.
- Build verification: `testGithubDebugUnitTest` and `assembleGithubDebug` passed.
- Visual capture attempt: Android 35 emulator and system image installed; emulator boot blocked by unavailable hardware virtualization.
- No visual iteration could be performed because implementation capture remains unavailable.

## Implementation checklist

- Capture the empty, loading, success, error, expanded-source, and dark-theme states on a physical device.
- Compare the active success state with the source image at matching crop and density.
- Fix any P0/P1/P2 typography, spacing, color, or persistent-control mismatch before release.

final result: blocked
