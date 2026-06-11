# MixedMeter

MixedMeter is an Android metronome app built for musicians who want something clean and practical.
Many metronome apps feel clunky, are packed with ads, or only support simple patterns. MixedMeter is designed to stay focused, responsive, and useful for real practice:

- no ad clutter
- mixed and changing time signatures
- sequence-based practice flows

## Why MixedMeter

Use MixedMeter when you want to:

- practice odd or changing meters without fighting the UI
- build structured tempo/time-signature routines
- keep timing practice in one app (base metronome + sequence player)

## Core Features

- **Main metronome screen**
  - Set BPM with a circular dial
  - Choose time signature and note value
  - Set each beat box to lead, beat, or inactive for custom accent patterns (lead beats show a dot)
  - Optional subdivisions (2–5 per beat) from the dial, with a separate subdivision tone
  - Change beat accents or tones during playback without interrupting timing
  - Beat boxes highlight in sync with playback
  - Start/stop playback quickly; the screen stays on while playing
  - Add the current meter to a sequence from the bottom bar
  - Two-finger swipe up for Sequence, left for Settings

- **Sequence mode**
  - Build a sequence of metronome steps
  - Set repeats per step
  - Use tempo percent scaling for sequence playback
  - Optional loop mode to restart from the beginning
  - Scroll map for long sequences; tap the thumb to jump to and highlight an item
  - Per-step subdivisions
  - Save and load named sequences
  - Two-finger swipe down to return to the main metronome

- **Settings**
  - Choose separate lead, beat, and subdivision tones (preview plays when you pick one)
  - Built-in clicks plus bundled WAV samples
  - Choose from multiple visual themes (Gray, Slate, Sand, Moss, Dusk, Lava, Dark, Light)
  - Two-finger swipe left for Information, right to go back

- **Information**
  - Email the creator, join Discord, or open the GitHub repo

Playback stops when the app loses focus or moves to the background.

## Basic Usage

1. Open the app to the main metronome.
2. Set your BPM and time signature.
3. Tap beat boxes to cycle through lead, beat, and inactive (new time signatures start with the first beat as lead).
4. Optionally set subdivisions from the control below the BPM dial.
5. Press play and practice.
6. Open Sequence mode to build multi-step practice routines, or swipe up with two fingers.
7. Save sequences you want to reuse; enable loop mode to repeat the whole sequence.
8. Open Settings to pick tones and theme, or swipe left with two fingers from the main screen.

## Tech Notes

- Kotlin + Jetpack Compose
- Material 3 UI
- DataStore-backed persistence for sequence/settings data
- Portrait-only activities

## License

Copyright 2026 John Asmuth

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full
license text and [NOTICE](NOTICE) for artwork and third-party attribution.
