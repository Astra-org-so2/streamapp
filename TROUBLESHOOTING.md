# StreamApp — Troubleshooting Guide

## 1. Streaming Connection Drops
- **Symptom:** App enters `Reconnecting` state.
- **Cause:** Network handover (Wi-Fi $\rightarrow$ Cellular) or temporary packet loss.
- **Solution:** StreamApp automatically initiates ICE restart. If reconnection fails after 5 attempts, a retry button is provided.

## 2. Low Frame Rate or High Latency
- **Check Hardware Decoding:** Ensure `Hardware Accelerated Decoding` is enabled in Settings $\rightarrow$ Streaming Quality.
- **Check Codec:** On older chipsets without AV1/HEVC hardware decoders, switch to H.264 Baseline in Settings.

## 3. Gamepad Input Drift
- **Solution:** `InputNormalizer` applies a $10\%$ radial deadzone. If analog stick drift persists, increase deadzone threshold in Settings $\rightarrow$ Controls.
