# Meta AI Glasses Developer Platform: Wearables Device Access Toolkit (DAT) and Web Apps on Meta Ray-Ban Display

Research date: 2026-11-13. Primary source is Meta's official docs at wearables.developer.meta.com, queried through the Wearables documentation MCP server (DAT docs are tagged "DAT SDK documentation version: v1.0"). Latest SDK: **DAT 1.0.1 (2026-10-09)**, after 1.0.0 (2026-09-24). The GitHub repos are now under `github.com/meta/` (meta-wearables-dat-ios, meta-wearables-dat-android). The old `facebook/meta-wearables-dat-ios` releases page shows no releases.

Short URLs used below:
- DAT overview: https://wearables.developer.meta.com/docs/develop/dat/build-overview
- Streaming: https://wearables.developer.meta.com/docs/develop/dat/streaming
- Changelog: https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md
- Known issues: https://wearables.developer.meta.com/docs/knownissues

---

## Q1. What does the DAT SDK (iOS and Android) expose, and which features work on which device?

### Takeaway
DAT is a native iOS/Android phone SDK. Through Bluetooth it gives your phone app:
- camera streaming (up to 720x1280 at 30 fps, usually lower in practice) and photo capture
- microphone audio and speaker output
- the Ray-Ban Display screen, through a Swift/Kotlin UI DSL
- inputs (touchpad, buttons, Neural Band), IMU motion data, on-glasses speech recognition, and "Hey Meta, start <app>" voice launch

Camera, audio, and display are the stable core. **Speech, Inputs, Motion, Voice invocations, standalone photo capture, and in-stream audio are all "dev and beta only".** They work in Developer Mode and private release channels, which is fine for a hackathon, but they cannot ship to production. Only Meta Ray-Ban Display (MRBD) has the display, and the Neural Band comes with it. Ray-Ban Meta Gen 2 gets everything except display access.

### Cited Findings

**Supported devices**
- DAT supports these glasses: Ray-Ban Meta (Gen 1 and Gen 2), Ray-Ban Meta Optics, Meta glasses, Meta Ray-Ban Display, Oakley Meta HSTN, and Oakley Meta Vanguard. — [Getting started](https://wearables.developer.meta.com/docs/develop/dat/getting-started-toolkit)
- The `DeviceType` enum (RAYBAN_META, OAKLEY_META_HSTN, OAKLEY_META_VANGUARD, META_RAYBAN_DISPLAY, RAYBAN_META_OPTICS, META_GLASSES) has an `isDisplayCapable` / `supportsDisplay` flag. Only META_RAYBAN_DISPLAY has a display. — [DeviceType Android ref v1.0](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_core_types_devicetype)

**Modules**
- `MWDATCore` handles registration, device discovery, permissions, and telemetry.
- `MWDATCamera` handles resolution/fps, start/pause/resume/stop, frames, single-frame capture during a stream, and photo format.
- `MWDATDisplay` drives MRBD with FlexBox, Text, Image, Button, Icon components and MP4 playback.
- Source: [DAT overview](https://wearables.developer.meta.com/docs/develop/dat/build-overview)

**Camera streaming**
- Valid `frameRate` values are 2, 7, 15, 24, or 30 fps. Resolutions are high 720x1280, medium 504x896, and low 360x640 (9:16 portrait).
- Bluetooth Classic bandwidth limits the stream. An automatic ladder first drops resolution one step, then fps, but "never below 15 fps".
- Per-frame compression adapts to bandwidth, so a stream reporting "high" may still look poor. "Requesting a lower resolution, a lower frame rate, or both can yield higher visual quality."
- Source: [Streaming](https://wearables.developer.meta.com/docs/develop/dat/streaming)
- Defaults differ by platform:
  - Android `StreamConfiguration`: MEDIUM, 24 fps. `compressVideo=true` delivers raw HEVC buffers; otherwise you get decoded YUV. — [Android StreamConfiguration ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_camera_types_streamconfiguration)
  - iOS default: raw codec, medium, 30 fps. — [iOS StreamConfiguration ref](https://wearables.developer.meta.com/docs/reference/ios_swift/dat/1.0/mwdatcamera_streamconfiguration)
- Frame processing on the glasses: crop to 9:16, correct lens distortion, stabilize, then HEVC (H.265) compression. Stream frames therefore have a narrower field of view than standalone photos. — [Camera stream processing](https://wearables.developer.meta.com/docs/develop/dat/camera-stream-processing)

**Photo capture**
- During a stream, `capturePhoto(format: .jpeg/.heic)` returns a photo. — [Streaming](https://wearables.developer.meta.com/docs/develop/dat/streaming)
- Standalone photo capture is beta. It offers SMALL 480p, MEDIUM 720p (default), LARGE 1080p, and FULL 4032x3024. — [PhotoResolution ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_camera_photo_types_photoresolution)
- Apps that use standalone capture cannot be published. Stream and Photo cannot use the camera at the same time. — [Camera stream processing](https://wearables.developer.meta.com/docs/develop/dat/camera-stream-processing)

**Audio**
There are three paths. [Microphones and speakers](https://wearables.developer.meta.com/docs/develop/dat/microphones-and-speakers):
- **A2DP** is output only, 44.1/48 kHz stereo. Use it for TTS and music.
- **HFP** is two-way mono voice at 16 or 8 kHz. Mics are beamformed to the wearer's voice, which suppresses ambient sound and other speakers.
- **In-stream camera audio** (beta) delivers 16-bit PCM at 16k, 44.1k, or 48k with timestamps synced to video. It is not beamformed, and output stays on A2DP.

A2DP and HFP are mutually exclusive: turning on HFP drops output to mono voice quality. The DAT overview also mentions "8 kHz mono" for HFP. — [DAT overview](https://wearables.developer.meta.com/docs/develop/dat/build-overview)

**Speech (beta)**
- Automatic speech recognition runs on the glasses. It delivers `TranscriptionResult` objects with text, isFinal, and confidence (-1 when unavailable), and reports the device locale (e.g., en_US). — [Speech](https://wearables.developer.meta.com/docs/develop/dat/speech)
- Per the changelog, it requires the microphone permission. — [Changelog](https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md)

**Inputs (beta)**
Event types: Nav (up/down/left/right), Select, Back, Button(ACTION), Capture (SHORT_PRESS/HOLD/DOUBLE_PRESS), and Drag (x, y, dx, dy). Sources [Inputs](https://wearables.developer.meta.com/docs/develop/dat/inputs):

| Source | Produces |
|---|---|
| CAPTOUCH (temple touchpad) | Nav, Select |
| NEURAL_BAND | Nav, Select |
| CAPTURE_BUTTON | Capture |
| ACTION_BUTTON | Button |
| NEURAL_BAND_DRAG | Drag |

- You get semantic events, not raw touch coordinates or EMG. — [Inputs](https://wearables.developer.meta.com/docs/develop/dat/inputs)
- The device reserves two touchpad gestures during a session (when no interactive display content is showing): single tap pauses/resumes, and tap-and-hold stops the session. Your app never receives these. Use double-tap (arrives as Select) or capture-button HOLD instead. — [Inputs](https://wearables.developer.meta.com/docs/develop/dat/inputs)

**Motion (beta)**
- `MotionSample` carries accelerometer (m/s²), gyroscope (rad/s), magnetometer (µT), orientation quaternion, and a source (glasses or Neural Band). Each field is nullable. — [Motion](https://wearables.developer.meta.com/docs/develop/dat/motion)
- Default sampling rate is 10 Hz. — [MotionConfiguration ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_motion_types_motionconfiguration)
- No user permission prompt is needed. — [Motion](https://wearables.developer.meta.com/docs/develop/dat/motion)
- The magnetometer is currently null on Ray-Ban Meta. — [MotionSample ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_motion_types_motionsample)

**Voice invocations (beta)**
- When the wearer says "Hey Meta, start {YOUR APP NAME}", Meta AI cold-launches or foregrounds your app and delivers a `LaunchApp` action. Your app must acknowledge exactly once through its response handle.
- No device session is needed; it is a `Wearables`-level stream.
- A WebMCP Web App that is open in the foreground takes priority over your app.
- Avoid "AI" in your app name, because it lowers recognition.
- Source: [Voice invocations](https://wearables.developer.meta.com/docs/develop/dat/voice-invocations)

**Device state** (no session needed)
- Battery %, charging, worn/not worn (don/doff), hinge open/closed, and an 8-level thermal level. — [Device state](https://wearables.developer.meta.com/docs/develop/dat/device-state)

**Display access (MRBD only)**
- Display renders 600x600 with FlexBox, Text (heading/body/meta presets), Image (from a URL or a local bitmap), Button, Icon, and Video. — [Display overview](https://wearables.developer.meta.com/docs/develop/dat/display-overview); [Changelog 0.9.0](https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md)
- Video limits: MP4 only, over HTTPS, at most 400 px per side and 70,000 total pixels, one video at a time. — [Display Android](https://wearables.developer.meta.com/docs/develop/dat/display-android)
- The display dims after 20 s of inactivity and sleeps at 25 s. Sleep does not end the session. — [Display Android](https://wearables.developer.meta.com/docs/develop/dat/display-android)
- "Complex views with large images may introduce lag due to Bluetooth bandwidth constraints." — [Display overview](https://wearables.developer.meta.com/docs/develop/dat/display-overview)
- The "DAT Wearables App" must be installed on MRBD for display to work. To install: enable Dev Mode, put the glasses on, press install. Takes about 5–10 s. — [Display overview](https://wearables.developer.meta.com/docs/develop/dat/display-overview)

**Version history**
- 0.7.0 (2026-05-14) added Display. 0.9.0 (2026-08-03) consolidated the `Camera` capability (`addStream` became `addCamera`). 1.0.0 (2026-09-24) added experimental Inputs, Motion, Speech, Voice invocations, photo capture, camera audio, device state, and MRBD simulation in MockDeviceKit. 1.0.1 (2026-10-09) fixed HEVC frame freezes and stream lag/stutter. — [Changelog](https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md)
- An earlier (developer-preview, ~v0.4.0) commentary said display output was not part of the toolkit. **That is outdated** now that Display shipped in 0.7.0. — [Nervegna Substack](https://nervegna.substack.com/p/metas-wearables-developer-center)

### Inferences

**Device capability matrix**

| Capability | Ray-Ban Meta Gen 2 (no display) | Meta Ray-Ban Display |
|---|---|---|
| Camera stream and photo | Yes | Yes |
| Mic (HFP or in-stream) and speakers | Yes | Yes |
| Speech | Yes, presumably | Yes, presumably |
| Motion | Yes | Yes |
| Voice invocations | Yes | Yes |
| Captouch, capture button, action button inputs | Yes | Yes |
| Display DSL | No | Yes |
| Neural Band inputs | No | Yes |
| Web Apps | No | Yes |

Neural Band inputs are effectively MRBD-only because the band ships with MRBD. The docs do not publish an explicit per-capability matrix (see Gaps).

**What to build on each device**
- On Gen 2, the output channel is audio only (TTS over A2DP or HFP).
- On MRBD you can add glanceable cards, lists, and buttons. Keep content to text and small images.
- In Dev Mode, beta features are usable for a hackathon demo.

### Gaps
- No official per-device support matrix for Speech, Motion, and Inputs. The docs only flag device-specific quirks (magnetometer null on Ray-Ban Meta; motion rotated 90° on displayless glasses, per known issues).
- No published supported-language list for on-glasses speech.
- No documented display content update rate or round-trip display latency.

---

## Q2. What are glasses Web Apps: how are they built and deployed, and what APIs, inputs, and display constraints do they have?

### Takeaway
Web Apps are ordinary HTTPS websites that run in a Chromium-based browser on **Meta Ray-Ban Display only**. You build them with HTML, CSS, and JS (optionally with Meta's React UI Toolkit). For development you load one by pasting its URL into the Meta AI app in Developer Mode. This is the fastest route to something on the display.

They cannot access the camera or raw mic audio. Spoken input arrives only as dictated text in a text field, or as WebMCP tool calls from Meta AI. WebMCP is off by default, and Developer Mode turns it on.

### Cited Findings

**Web Apps vs. DAT**
- "Only the Device Access Toolkit can use the glasses' camera or capture microphone audio. Web Apps get spoken input only as text dictated into a text field or, on glasses with WebMCP turned on (off by default), as calls Meta AI makes to tools the page registers." — [llms.txt](https://wearables.developer.meta.com/llms.txt)

**Deploy for development**
1. Enable Developer Mode.
2. In the Meta AI app: App Settings > Apps > Web Apps > Connect Web App.
3. Enter the HTTPS URL and tap Save.
4. The app appears at the bottom of the glasses app grid.

Source: [Web Apps setup](https://wearables.developer.meta.com/docs/develop/webapps/setup/)

- Wearers install via QR code or an on-page install control, and must confirm on the glasses. — [Web Apps install docs](https://wearables.developer.meta.com/docs/develop/webapps/)
- You need a public HTTPS URL with a valid TLS certificate; HTTP is not supported. Suggested hosts: Vercel, Netlify, GitHub Pages, Cloudflare Pages, Replit, Lovable. — [Web Apps hosting](https://wearables.developer.meta.com/docs/develop/webapps/setup/)

**Capabilities** ([Web Apps Build overview](https://wearables.developer.meta.com/docs/develop/webapps/build/overview/)):
- 600x600 additive-light display
- Browser-managed spatial focus navigation driven by arrow keys and Enter
- Opt-in pointer-event drag
- Text fields that open the system composer (handwriting, dictation, keyboard)
- History-based Back
- Permission-gated `DeviceMotionEvent` and `DeviceOrientationEvent`
- `navigator.geolocation`, sourced through the paired phone (the glasses have no GNSS)
- `navigator.getBattery()`
- localStorage and sessionStorage
- Service Workers and Cache Storage for offline use
- A subset of `speechSynthesis`
- A wearables manifest for the app icon

**Input**
- The touchpad and Neural Band arrive as standard `keydown` events for ArrowUp/Down/Left/Right and Enter. "The runtime has no pointer or touch surface." — [Input and navigation](https://wearables.developer.meta.com/docs/develop/webapps/build/input-and-navigation/)
- Neural Band wrist orientation is available through `RelativeOrientationSensor`, marked by `__imuQuatWrapped === true`. The marker is injected a few hundred ms after load, so poll for it. — [Web Apps sensors](https://wearables.developer.meta.com/docs/develop/webapps/build/sensors-and-location/)

**Text to speech**
- One voice ("Default", en-US). `volume` is ignored, and boundary/mark events do not fire.
- `pause()` followed by `resume()` restarts the utterance from the beginning.
- Listeners must attach to the utterance, not to `speechSynthesis`.
- Source: [Text to speech](https://wearables.developer.meta.com/docs/develop/webapps/build/text-to-speech/)

**Display rules**
- Black pixels are transparent. Use a black page background and dark gray only on bounded surfaces.
- Budgets: HUD text at least 16 px, 30 fps, JS under 500 KB gzipped, runtime memory under 128 MB.
- Source: [Web Apps game guide limitations](https://wearables.developer.meta.com/docs/develop/webapps/)

**WebMCP**
- The page calls `document.modelContext.registerTool()`. Meta AI calls these tools by voice while the app is in the foreground.
- Tool calls time out after 10 s. Tools are unavailable in the background. "Voice adds latency."
- It is off by default; it is on in Developer Mode or for users in the rollout. You may need to restart the glasses and the Meta AI app.
- Examples: github.com/meta/meta_glasses_webmcp.
- Sources: [WebMCP](https://wearables.developer.meta.com/docs/develop/webapps/); [WebMCP limitations](https://wearables.developer.meta.com/docs/develop/webapps/)

**Tooling**
- A React UI toolkit (`@wearables-ui-toolkit/mrbd`; source at github.com/meta/meta-ray-ban-display-ui-toolkit-web).
- A Chrome "Meta Ray-Ban Display Simulator" extension with a 600x600 frame, background scenes or webcam, D-pad and Select, a WebM recorder, a "View on Glasses QR" deep link, and a quality checklist.
- Source: [Web Apps design docs / simulator](https://wearables.developer.meta.com/docs/develop/webapps/)

**Debugging**
- The glasses cannot be tethered, so there is no console on device.
- Use on-display stats and logs (`?stats`, `?log=debug&logview`) or remote logging.
- Source: [Web Apps game guide](https://wearables.developer.meta.com/docs/develop/webapps/)

### Inferences
- A Web App can call any cloud API with `fetch`, such as an LLM via your own proxy. It just cannot see through the camera.
- For a vision use case on MRBD, a hybrid is plausible: a DAT phone app does the vision, and a web app or the DAT display shows the results. The simplest path is DAT alone with its Display capability.
- WebMCP is the fastest route to a voice-driven app on MRBD without writing native code.

### Gaps
- The docs I searched did not state CORS, mixed-content, WebSocket, or WebRTC support specifics. Standard Chromium behavior is likely but not confirmed.
- Production distribution and review for Web Apps beyond Dev Mode and QR install were not detailed in what I found.

---

## Q3. Constraints: latency, battery, thermal, bandwidth, session lifecycle, permissions and registration, Developer Mode, MockDeviceKit

### Takeaway
The main constraints:
- **Bandwidth.** Everything travels over phone Bluetooth Classic, so video quality degrades under load. Lower settings often look better.
- **One session at a time.**
- **User or system interruptions.** Taps, hinge closing, removing the glasses, and thermal or battery limits all pause or stop sessions.
- **Registration through the Meta AI app.** Developer Mode skips attestation, but only one third-party app can be registered at a time.

MockDeviceKit (MDK) lets you develop without glasses, including using the phone camera as a fake glasses camera and simulating MRBD.

### Cited Findings

**Sessions**
- "Only one session can run on a device at a time." Users can pause, resume, or stop by closing the hinges, taking the glasses off, or tapping. — [DAT overview](https://wearables.developer.meta.com/docs/develop/dat/build-overview)
- Stream states (Android): STARTING, STARTED, STREAMING, PAUSED, STOPPING, STOPPED, CLOSED. CLOSED is final. — [StreamState ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_camera_types_streamstate)
- Stream errors include HINGE_CLOSED, THERMAL_HOT, BATTERY_LOW, PEAK_POWER_LIMIT, and TIMEOUT. — [StreamError ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_camera_types_streamerror)
- Session errors include THERMAL_CRITICAL, THERMAL_EMERGENCY, BATTERY_CRITICAL, DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED, and DWA_UNAVAILABLE. — [DeviceSessionError ref](https://wearables.developer.meta.com/docs/reference/android/dat/1.0/com_meta_wearable_dat_core_types_devicesessionerror)

**Registration and permissions**
- Registration is a one-time deep link into the Meta AI app.
- Camera permission is granted in the Meta AI app (allow once / always / deny).
- The mic uses HFP, so it uses OS permission dialogs instead.
- Without registration, permission requests fail.
- Source: [DAT overview](https://wearables.developer.meta.com/docs/develop/dat/build-overview); [Permissions](https://wearables.developer.meta.com/docs/develop/dat/permissions-requests)

**Developer Mode**
- To enable: Meta AI app > Settings > App Info > tap the App version 5 times > toggle.
- Unpublished apps can register without review.
- Source: [Getting started](https://wearables.developer.meta.com/docs/develop/dat/getting-started-toolkit)
- In Dev Mode, set APPLICATION_ID and CLIENT_TOKEN to "0", because there is no attestation. — [Build integration Android](https://wearables.developer.meta.com/docs/develop/dat/build-integration-android)
- "Only one 3rd party app can remain registered at a time in Developer Mode. Registering a new app will automatically unregister any previously registered app." — [Permissions](https://wearables.developer.meta.com/docs/develop/dat/permissions-requests)

**Beta features and release channels**
- Beta features cannot go to production. Distribute to testers through a private "Beta" release channel in the Wearables Developer Center, with email invites tied to Meta accounts. Managed Meta Accounts are not supported. — [Release channels](https://wearables.developer.meta.com/docs/develop/dat/set-up-release-channels)

**MockDeviceKit**
- Simulates registration, permissions, streaming, photo, device state, and (since 1.0) Display, Speech, Motion, Inputs, and voice invocations.
- You can pair up to 3 mock devices.
- The camera feed can be the phone's front or back camera or a video file (H.265 required; the Android sample does not auto-convert).
- Source: [Mock Device Kit](https://wearables.developer.meta.com/docs/develop/dat/mock-device-kit); [MockGlassesServices ref](https://wearables.developer.meta.com/docs/reference/ios_swift/dat/1.0/mwdatmockdevice_mockglassesservices)
- MDK "does not model Stream and Photo hardware contention, and its transfer progress is not representative". Validate on hardware. — [Camera capture](https://wearables.developer.meta.com/docs/develop/dat/camera-capture)
- The MRBD display can be previewed in-app or in the Chrome simulator extension (600x600, D-pad and Select). — [Display simulation](https://wearables.developer.meta.com/docs/develop/dat/display-simulation)

**Battery**
- DAT exposes battery level and thermal level so apps can back off. — [Device state](https://wearables.developer.meta.com/docs/develop/dat/device-state)
- Installing the DAT glasses app fails below 10% battery. — [Known issues](https://wearables.developer.meta.com/docs/knownissues)

### Inferences
- Budget for thermal and battery cutoffs during long continuous 720p/30 streams. For AI use, 2–7 fps or on-demand photos is more sustainable. This is an inference from the error states and bandwidth notes, not measured data.
- In a team, only one phone/app pairing can be registered per glasses in Dev Mode. Coordinate who owns which glasses.

### Gaps
- No official numbers for streaming battery drain, minutes until thermal throttle, or Bluetooth throughput.

---

## Q4. Typical AI architecture (glasses → phone → cloud LLM → audio/display back) and known latencies

### Takeaway
The standard pattern:
1. Glasses camera and mic go over Bluetooth to a DAT phone app.
2. The phone sends JPEG frames (often about 1 fps) plus 16 kHz PCM to a realtime multimodal API over WebSocket or WebRTC (e.g., Gemini Live via LiveKit, OpenAI Realtime).
3. The answer comes back as audio played over the glasses speakers, and on MRBD optionally as text through the Display DSL.

No source publishes measured end-to-end latency.

### Cited Findings

**Gemini Live via LiveKit (Google AI)**
- Google AI's reference build: glasses connect over Bluetooth to an Android phone, which connects over WebRTC to LiveKit Cloud as a proxy to the Gemini Live API.
- The agent receives a **1 FPS** video track and uses `gemini-2.5-flash-native-audio-preview-12-2025`.
- The latency claim is only qualitative ("near real-time"). No numbers are given.
- Client code: github.com/thorwebdev/meta-wearables-dat-android, branch `thor/gemini-live-livekit`.
- Source: [DEV / Google AI (Thor Schaeff)](https://dev.to/googleai/realtime-multimodal-ai-on-ray-ban-meta-glasses-with-gemini-live-livekit-45mn)

**VisionClaw** (open source, sseanliu/VisionClaw)
- Streams JPEG frames at about 1 fps plus 16 kHz PCM to Gemini Live over WebSocket. — [Suganthan blog](https://suganthan.com/blog/visionclaw-openclaw-meta-glasses/); [gentic.news](https://www.gentic.news/article/openclaw-ai-agent-adds-real-time-vision-to-meta-ray-ban-smart-glasses-via-gemini)
- These are secondary write-ups; verify against the repo.

**Other community builds**
- A hackathon project ("Clutch") streams 720p/30 fps from DAT to an iOS app with a Cloud Run backend. It reports Gemini Live stream-size limits as a hard problem. — [DEV Community](https://dev.to/brandikinard/i-built-an-ai-agent-that-sees-through-smart-glasses-and-tells-you-how-to-fix-anything-1a51)
- OpenVision offers OpenClaw or Gemini Live backends, and FirstSight accepts JPEG frames over WebSocket. — [search results; repos via mirrors, unverified](https://github.xiaohuzi123231346.workers.dev/rayl15/OpenVision)

**Audio guidance from Meta's docs**
- For audio to transcription, use 16 kHz in-stream PCM.
- With HFP, start HFP and let it settle (the sample waits about 2 s) BEFORE starting the camera stream, or the audio route "can fail silently".
- Source: [Microphones and speakers](https://wearables.developer.meta.com/docs/develop/dat/microphones-and-speakers)

**On-device speech as a cheap STT front end**
- On-glasses Speech gives partial and final transcripts. — [Speech](https://wearables.developer.meta.com/docs/develop/dat/speech)
- But it silently stops after 1 minute. — [Known issues](https://wearables.developer.meta.com/docs/knownissues)

### Inferences
- A pragmatic 36 h pipeline:
  - DAT stream at LOW or MEDIUM, 2–7 fps (or a frame grab on a capture-button press) → JPEG.
  - Send to Claude, Gemini, or GPT vision over HTTPS from the phone. Use realtime APIs for conversational use.
  - Speak the reply with phone TTS routed over A2DP. Mirror short text to the MRBD display.
- Expect roughly 1–3 s per round trip for a single-shot vision question. This is my estimate, not sourced; measure it yourself.
- Sending about 1 fps to realtime models is the community norm, and it keeps cost and bandwidth low.

### Gaps
- No measured glasses-to-phone frame latency and no measured end-to-end LLM round trip from any primary source.

---

## Q5. Sample apps and what Meta's examples demonstrate

### Takeaway
Start from **CameraAccess**: streaming, photo capture, and a built-in MockDeviceKit debug UI. Use **DisplayAccess** for MRBD UI. Both are in the meta/meta-wearables-dat-ios and meta/meta-wearables-dat-android repos, which also ship AI-coding plugins (Claude Code, Cursor, Codex, Copilot, AGENTS.md).

### Cited Findings
- **DisplayAccess sample (iOS and Android):** session and display lifecycle, a scrollable list with images and tap handlers, detail views, back/next step navigation, and video clip playback. — [Display overview](https://wearables.developer.meta.com/docs/develop/dat/display-overview)
- **CameraAccess sample:** a ladybug debug icon enables MockDeviceKit and auto-completes registration. "Pair Ray-Ban Meta" toggles power, donned, and unfolded state. The camera source can be the phone camera or a video file. Credentials are read from `local.properties`. — [Mock Device Kit](https://wearables.developer.meta.com/docs/develop/dat/mock-device-kit); [Build integration Android](https://wearables.developer.meta.com/docs/develop/dat/build-integration-android)
- **AI-coding integrations:** the SDK repos ship `.claude-plugin/marketplace.json`, Cursor rules, Codex plugin, Copilot instructions, and AGENTS.md. — [AI-assisted dev](https://wearables.developer.meta.com/docs/develop/dat/ai-assisted)
- **Web Apps:** Meta provides a WebMCP examples repo (github.com/meta/meta_glasses_webmcp), a UI toolkit repo, and a game scaffold plugin (Vite + Three.js, deployed to Vercel). — [Web Apps docs](https://wearables.developer.meta.com/docs/develop/webapps/)

### Inferences
- Fastest path:
  - Gen 2 / any glasses: fork CameraAccess.
  - Add a "send frame to LLM" button and capture-button trigger.
  - Speak the reply over A2DP.
- For MRBD, add DisplayAccess-style FlexBox cards.
- Prototype everything in MockDeviceKit with the phone camera before touching hardware.

### Gaps
- I did not review the sample code directly. Some samples may require a GitHub token for package access: the Google article mentions needing one in `local.properties` for the Android SDK packages.

---

## Q6. Known gotchas reported by developers and Meta

### Takeaway
Many beta features are flaky. Plan workarounds for:
- speech stopping at 1 minute
- inputs dying after re-donning
- capability resume failures
- duplicate double-press events
- Back events never arriving
- HFP/stream ordering
- the one-registered-app limit in Dev Mode
- registration needing internet

### Cited Findings

**From Meta's known-issues page**
- Speech silently stops reporting after 1 minute. Workaround: restart it.
- A double press emits SHORT_PRESS then DOUBLE_PRESS. Workaround: buffer SHORT_PRESS briefly.
- Beta capabilities do not resume properly after pause. Workaround: restart them from saved state.
- Inputs stop after the glasses are removed and put back on. Workaround: re-add the capability on `donState`.
- Motion data is rotated 90° on displayless glasses.
- Display updates are batched during capture transfer on MRBD. Workaround: don't combine capture with display.
- Camera capture breaks after firmware v128. Workaround: reboot the glasses.
- iPhone 17 on iOS 26 has HFP with no audio. Workaround: iOS 27.
- Installing the DAT glasses app fails below 10% battery.
- Logging into developers.meta.com conflicts with Wearables Developer Center links. Workaround: log out first.
- Source: [Known issues](https://wearables.developer.meta.com/docs/knownissues)

**From the Inputs docs**
- `Back` events are not delivered by real glasses yet.
- Volume is not exposed.
- The action button is not validated on all hardware.
- Source: [Inputs](https://wearables.developer.meta.com/docs/develop/dat/inputs)

**Other gotchas**
- iOS-only: on MRBD, captouch pause/resume does not play the "Experience paused/started" audio cues. — [Known issues, via search snippet](https://wearables.developer.meta.com/docs/knownissues)
- Registration needs internet; without it, Dev Mode registration can fail. — [Known issues, via search snippet](https://wearables.developer.meta.com/docs/knownissues)
- "High" resolution can look worse than lower settings because of adaptive compression. Meta itself recommends lower settings. — [Streaming](https://wearables.developer.meta.com/docs/develop/dat/streaming); echoed by [Mixed News](https://mixed-news.com/en/?p=36489)
- Stream and standalone Photo cannot run at the same time. — [Camera stream processing](https://wearables.developer.meta.com/docs/develop/dat/camera-stream-processing)
- 1.0.1 fixed HEVC buffer corruption (frame freezes) and stream lag/stutter, so use 1.0.1 or later. — [Changelog](https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md)
- Web Apps: no device console, desktop preview is not the device, the WebMCP 10 s tool timeout, and WebMCP eligibility is fixed at app launch. — [Web Apps docs](https://wearables.developer.meta.com/docs/develop/webapps/)
- Voice-invocation name tip: avoid "AI" in the app name. — [Voice invocations](https://wearables.developer.meta.com/docs/develop/dat/voice-invocations)

### Inferences

**Fastest 36-hour path**
- **Hours 0–3:** set up hardware.
  - Update the Meta AI app and firmware to the 1.0.x version dependencies.
  - Enable Dev Mode, then build and run CameraAccess on one phone per glasses.
  - Verify registration with internet connected.
  - On MRBD, install the DAT glasses app with battery above 10%.
- **In parallel:** teammates develop against MockDeviceKit with the phone camera, plus the Chrome display simulator for MRBD UI.
- **Trigger UX:** use the capture button (Capture SHORT_PRESS / HOLD) or "Hey Meta, start <app>". Avoid single tap, which is reserved for pause.
- **Display-only MRBD ideas with no camera:** a Web App on Vercel plus WebMCP is the quickest path. Hours, not a day.

### Gaps
- Few independent developer forum reports were found. Most gotchas come from Meta's own known-issues page.
- No independent latency or battery benchmarks were found.
