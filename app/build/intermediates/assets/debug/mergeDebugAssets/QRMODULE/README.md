# NODE8 — Android (Kotlin) native port

A single native app that combines the Send and Receive tools from `tx.html` /
`rx.html` into one UI with a Send tab and a Receive tab, targeting **Android
9 (API 28) and up**.

## Important: this is a clean-room reimplementation, not a byte-exact port

`rx.html` bundles an ~800 KB minified/obfuscated JS build with a fully custom
optical transfer protocol (custom framing, a fountain/erasure code, gzip,
SHA-256 verification, WASM-assisted decode, multi-worker QR decoding).
Reverse-engineering that bundle down to the exact bit-level wire format was
out of scope for a reliable one-shot port, so instead this app **re-implements
the same idea from scratch in Kotlin**:

- Files/text are packed into a small envelope (name, type, size, SHA-256).
- The envelope is gzip-compressed, then fountain-coded (LT codes with a
  robust soliton degree distribution) into an endless stream of coded blocks.
- Each block is rendered as a QR code (ZXing) and animated on screen at a
  configurable fps; 1/2/4/6-up grid layouts are supported, like the original.
- The receiver uses CameraX + ZXing to decode one or more QR codes per camera
  frame, feeds them into a peeling decoder, and reconstructs + verifies the
  file once enough distinct blocks have been seen — no fixed frame order or
  ACKs required, so the sender can just loop forever.

**This app only talks to itself** (Android app ↔ Android app). It is not
wire-compatible with the original `feinlabs.de` web app's protocol.

## Project layout

```
app/src/main/java/com/node8/transfer/
  protocol/Container.kt    envelope: name/type/size + gzip + SHA-256
  protocol/Soliton.kt      robust soliton degree distribution
  protocol/Fountain.kt     LT fountain encoder + peeling decoder
  protocol/FrameCodec.kt   packs a fountain symbol into one QR's bytes
  protocol/QrCodec.kt      QR encode (ZXing), raw-byte safe (ISO-8859-1)
  camera/QrAnalyzer.kt     CameraX analyzer, decodes multiple QRs/frame
  ui/MainActivity.kt       tabs (Send / Receive) via ViewPager2
  ui/SendFragment.kt       file/text picker, settings, animated QR stage
  ui/ReceiveFragment.kt    camera preview, progress HUD, save/share result
```

## Building

1. Open the `Node8Android` folder in Android Studio (Koala/Ladybug or newer).
2. Let Gradle sync — it will pull CameraX, ZXing `core`, Material, and
   coroutines from Google/Maven Central.
3. Run on a device/emulator running Android 9+ (a real device is strongly
   recommended for the camera side).

No API keys, backend, or network access is required — everything is
on-device, matching the original's "nothing leaves your device" design.

## Known simplifications vs. the original UI

- The receive-side "capture width / fps / decode workers" and the "no
  signal" help dialog were left out to keep scope manageable; CameraX picks
  a sensible default preview/analysis resolution automatically.
- The send-side "display size" slider was omitted — QR bitmaps render at a
  fixed size that fills the stage.
- Demo payload buttons were omitted (only "pick a real file" / "text
  snippet" are implemented).
- Saved files go to the app's own external-files "received" folder and can
  be opened/shared via the "Open / share file" button (uses a FileProvider),
  rather than a browser-style download.

These are all straightforward to extend if you want closer parity — the
protocol/UI layers are separated so you can flesh out settings without
touching the codec code.
