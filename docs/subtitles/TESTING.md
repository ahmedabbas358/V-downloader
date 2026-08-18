# Subtitle Engine Testing Guide

## 1. Automated Unit Tests

Unit tests are located in `app/src/test/java/com/junkfood/seal/engine/`:

- `SubtitleDiscoveryTest.kt`: Tests structured metadata parsing from `VideoInfo`.
- `LanguageMatcherTest.kt`: Tests exact, base, and regional fallback matching.
- `SubtitleValidatorTest.kt`: Tests format syntax validation and HTML error rejection.
- `SubtitleConverterTest.kt`: Tests stream conversions and timing shifts.
- `RetryPolicyTest.kt`: Tests failure-specific backoff and retry rules.
- `YoutubeClientStrategyTest.kt`: Tests client chain rotation on 403 / PO token.
- `SubtitleOptionBuilderTest.kt`: Tests CLI argument generation.

### Running Unit Tests
```bash
./gradlew test --tests "com.junkfood.seal.engine.*"
```

---

## 2. Real YouTube Smoke Test Suite

To verify real-world YouTube compatibility against active endpoints:

1. **Manual Subtitle Video**: Test video with creator-uploaded Arabic and English subtitles.
2. **Auto-generated Captions**: Test video with only automated captions.
3. **Multilingual Translated Captions**: Test video with auto-translated tracks.
4. **Shorts / Long Form**: Test YouTube Shorts (`/shorts/`) and long lecture videos (>2 hours).
5. **No Subtitles Video**: Verify clean error handling without endless retries.
6. **Playlist Subtitle Download**: Verify sequential pacing and directory indexing (`[Subtitles] PlaylistTitle`).
