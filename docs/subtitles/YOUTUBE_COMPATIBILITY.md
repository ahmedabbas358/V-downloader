# YouTube Subtitle Compatibility Matrix

## 1. Client Strategies & Extractor Arguments

yt-dlp utilizes player clients to access YouTube streams and captions. The Subtitle Engine dynamically supplies `--extractor-args youtube:player_client=...` using the following hierarchy:

| Client | Primary Use Case | PO Token Sensitivity | Subtitle Availability |
|---|---|---|---|
| `android` | Default / General videos | Low | High (Original + Auto-captions) |
| `ios` | Fallback on Android 403 | Low | High |
| `mweb` | Lightweight mobile web stream | Medium | High |
| `web` | Desktop fallback | High (requires PO token) | Full (including auto-translated) |
| `default` | Standard yt-dlp internal fallback | Medium | Variable |

---

## 2. JavaScript / EJS Challenge Requirements

yt-dlp requires an external JS runtime to solve n-token and signature challenges on modern YouTube clients.
- `YtDlpRuntimeManager` provides runtime diagnostics and checks for challenge solving capabilities.
- When challenge failures occur, `SubtitleFailure.JsChallengeFailed` is triggered and recovered with adaptive client rotation.

---

## 3. Subtitle Format Support

YouTube delivers captions in various wire formats. The engine processes and normalizes them into standard output formats:

| YouTube Source Format | Converted Format | Converter Engine |
|---|---|---|
| WebVTT (`.vtt`) | SRT (`.srt`) | `SubtitleConverter` (Stream) |
| WebVTT (`.vtt`) | ASS (`.ass`) | `SubtitleConverter` (Stream) |
| TTML / XML (`.ttml`) | SRT (`.srt`) | `SubtitleConverter` (Stream) |
| SRT (`.srt`) | WebVTT (`.vtt`) | `SubtitleConverter` (Stream) |
| JSON3 (`.json3`) | SRT / VTT | yt-dlp backend |
