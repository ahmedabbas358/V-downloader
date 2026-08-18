# Subtitle Engine Troubleshooting Guide

## Common Issues & Diagnoses

### 1. HTTP 429 (Too Many Requests / Bot Detection)
- **Symptom**: `SubtitleFailure.Http429` reported.
- **Cause**: YouTube rate limit triggered by excessive sequential requests.
- **Engine Response**: Triggers `RequestCoordinator.triggerRateLimitCooldown(6000ms)` and retries with exponential backoff + jitter.
- **User Action**: Reduce playlist download batch size or enable Proxy in Settings.

### 2. HTTP 403 Forbidden
- **Symptom**: `SubtitleFailure.Http403` reported.
- **Cause**: Video stream blocked for the current player client.
- **Engine Response**: `YoutubeClientStrategy` automatically rotates to alternative client chains (e.g., iOS or Web) and retries.

### 3. PO Token Required
- **Symptom**: `SubtitleFailure.PoTokenRequired` reported.
- **Cause**: YouTube requires Proof of Origin token for the video stream.
- **Engine Response**: `PoTokenProvider` is requested to provide video-bound visitor/PO token.

### 4. HTML Error Page Downloaded
- **Symptom**: `SubtitleFailure.InvalidSubtitle` reported during validation.
- **Cause**: CDN returned an HTML error or redirect rather than raw subtitle text.
- **Engine Response**: `SubtitleValidator` rejects the file, cleans the temporary file, and triggers a retry with fallback format.

### 5. Missing Subtitles
- **Symptom**: `SubtitleFailure.NoSubtitles` or `LanguageUnavailable`.
- **Cause**: The video does not have subtitles in the requested language.
- **Engine Response**: If auto-generated subtitles are permitted in preferences (`autoSubtitle = true`), the engine automatically attempts to match auto-captions or translated tracks.
