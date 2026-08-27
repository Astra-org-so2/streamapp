# StreamApp — Input Normalization Subsystem

## 1. Unified Input Model (`NormalizedInputEvent`)

All user interaction is normalized before transmission:

```
Android MotionEvent / KeyEvent
               ↓
        InputNormalizer
  (Radial Deadzone / Clamping)
               ↓
      NormalizedInputEvent
  (Touch, Mouse, Gamepad, Key)
               ↓
  WebRTC DataChannel / UDP Pack
```

## 2. Virtual Gamepad
- Custom on-screen controller built in Jetpack Compose.
- Dual analog thumbsticks with deadzone filtering ($10\%$).
- ABXY diamond button cluster, D-Pad, L1/R1 bumpers, and L2/R2 triggers.
- Per-game layout persistence in Room DB (`GamepadLayoutEntity`).

## 3. Physical Controller Mapping
- Standard HID controller listener in `PhysicalGamepadHandler`.
- Normalized axis values ($-1.0\text{f} \dots 1.0\text{f}$) and analog triggers ($0.0\text{f} \dots 1.0\text{f}$).
