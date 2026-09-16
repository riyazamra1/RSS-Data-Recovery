# RSS Data Recovery — RSS Core / RAY Integration

Project key: `rss-data-recovery`
RSS Core production base: `https://rsscore.cv`

## Rules
- Android never contains the RSS Core admin/API token.
- Customer registration and licensing use the RSS Core customer/license APIs through a safe client-facing integration layer.
- Engineering tasks are dispatched through the RSS Core RAY control plane.
- RAY remains server-side execution; credentials never ship in the APK.
- GitHub `main` is the source of truth.

## Continuous development queue
1. Professional UI and navigation
2. Persistent color theme + Light/Dark appearance
3. RSS Licensing Service registration and license state
4. Storage/media permission flow
5. Deep Recovery scan engine
6. Recoverability result model and previews
7. Premium recovery gate
8. Original filename/metadata preservation
9. RSS Data Recovery destination folder
10. Error handling, offline queue, telemetry-safe diagnostics
11. Automated build/test/fix loop
12. Debug APK artifact
