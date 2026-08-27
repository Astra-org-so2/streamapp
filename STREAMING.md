# StreamApp — Real-Time Streaming Subsystem

## 1. WebRTC & Media Pipeline

```
Signaling (WebSocket) ──► PeerConnection ──► Video Track ──► Hardware MediaCodec ──► SurfaceView
                                         ──► Audio Track ──► StreamAudioManager ──► AudioTrack
                                         ──► DataChannel ◄── InputNormalizer ◄── Gamepad/Touch
```

## 2. Hardware Decoder Selection
Before initializing a stream, `CodecCapabilityDetector` queries `MediaCodecList` for hardware decoder capabilities:
1. **AV1** (`video/av01`) if hardware accelerated.
2. **HEVC / H.265** (`video/hevc`) with `FEATURE_LowLatency`.
3. **H.264 / AVC** (`video/avc`) baseline fallback.
4. **VP9** (`video/x-vnd.on2.vp9`).

## 3. Adaptive Quality with Hysteresis
To protect against rapid quality oscillation (ping-ponging), `AdaptiveQualityEngine` enforces:
- **Downshift Trigger:** 3 consecutive degraded metrics (Packet Loss $> 2.5\%$, RTT $> 75\text{ms}$, Jitter $> 15\text{ms}$).
- **Upshift Trigger:** 15 consecutive clean metrics (over 7.5 seconds) before increasing resolution or target FPS.
