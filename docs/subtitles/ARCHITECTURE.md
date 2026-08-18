# Subtitle Engine Architecture

## 1. Overview

The Subtitle Engine in V-Downloader is a fault-tolerant, resilient subsystem designed to discover, download, validate, and convert subtitles across multilingual video sources (with primary optimization for YouTube).

Instead of treating subtitle extraction as a single brittle CLI call, the engine employs a multi-stage pipeline:

```text
                         SubtitleManager (Facade)
                               │
                               ▼
                         SubtitleUseCase
                               │
                               ▼
                      SubtitleOrchestrator (State Machine)
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
     Discovery            Download              Recovery
          │                    │                    │
          ▼                    ▼                    ▼
   SubtitleDiscovery     SubtitleDownloader    RecoveryManager
   LanguageMatcher       RequestCoordinator    RetryPolicy
          │                    │
          ▼                    ▼
   YouTubeProvider       Validator
   ClientStrategy             │
   PoTokenProvider            ▼
   YoutubeCompat         Converter
                               │
                               ▼
                         Atomic Writer → Storage
```

---

## 2. Core Modules

### 2.1 Models (`model/`)
- `SubtitleTrack`: Holds language code, name, source (MANUAL, AUTO_GENERATED, TRANSLATED), formats, and availability.
- `SubtitleInventory`: Aggregates all manual, auto-generated, and translated tracks discovered for a media item.
- `SubtitleFailure`: Rich sealed hierarchy classifying 25+ failure conditions (Http403, Http429, PoTokenRequired, JsChallengeFailed, NetworkError, etc.).
- `SubtitleOutputFormat`: Supported formats (SRT, VTT, ASS, TTML, LRC).

### 2.2 Discovery (`discovery/`)
- `SubtitleDiscovery`: Extracts structured track metadata from JSON without console regex scraping.
- `LanguageMatcher`: Performs exact matching, base code matching, regional fallback, and user preference ordering.

### 2.3 Provider Abstraction (`provider/`)
- `SubtitleProvider`: Generic interface for platform-independent subtitle extraction.
- `YouTubeSubtitleProvider`: YouTube-specific implementation wrapping yt-dlp with dynamic client strategy and rate limiting.

### 2.4 YouTube Strategy (`youtube/`)
- `YoutubeClientStrategy`: Dynamically chooses and rotates player clients (`android`, `ios`, `mweb`, `web`, `default`) upon 403 or challenge detection.
- `PoTokenProvider`: Modular interface and stub for video-bound Proof-of-Origin tokens.
- `YoutubeCompatibility`: Encapsulates YouTube-specific URL extraction, stream formats, and extractor arguments.

### 2.5 Validation & Conversion (`validation/`, `conversion/`)
- `SubtitleValidator`: Verifies syntax, non-empty content, UTF-8 encoding, timestamp consistency, and rejects HTML error or 403 pages.
- `SubtitleConverter`: Memory-efficient, stream-based conversion for VTT ↔ SRT, TTML → SRT, and VTT → ASS, plus millisecond timestamp shifting with full Arabic/RTL preservation.

### 2.6 Resilience (`resilience/`)
- `RetryPolicy`: Maps specific failures to exponential backoff delays with jitter.
- `RequestCoordinator`: Manages concurrency semaphore, anti-ban pacing delays, and 429 cooldowns.
- `SubtitleRecoveryManager`: Handles client rotation and retry budgets.

### 2.7 Runtime & Cache (`runtime/`, `cache/`)
- `YtDlpRuntimeManager`: Handles version detection, compatibility checks, and smoke tests.
- `SubtitleCache`: In-memory LRU cache with TTL for `SubtitleInventory`.
