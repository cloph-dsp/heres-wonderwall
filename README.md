# Here's Wonderwall 🎸

Android app: upload or record a song → ML chord recognition → chord diagrams → offline cache.

A native WebView wrapper around [ChordMini](https://chordmini.me) with a JS bridge for:

- Native audio file picker (system file manager, Google Drive, etc.)
- Native audio recording (mic → AAC)
- Audio share intents (receive audio from WhatsApp, recorder, etc.)
- Offline chord storage (Room DB)
- Native share sheet
- Background processing notifications

## Build

Requires Android SDK 35, Java 17, Gradle 8.11.

```bash
git clone https://github.com/cloph-dsp/heres-wonderwall.git
cd heres-wonderwall
./gradlew assembleDebug
```

## License

MIT
