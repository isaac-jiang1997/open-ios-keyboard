# Layout Calibration

The first prototype uses normalized iPhone-like geometry. Pixel-perfect matching needs measured reference data.

## Reference Matrix

Track every calibration against:

- iPhone model.
- iOS version.
- Keyboard locale.
- Orientation.
- Display Zoom setting.
- Safe-area and bottom inset.

## Current Landscape References

The Android prototype now contains a first screenshot-driven landscape profile based on private local reference screenshots that are not committed to this repository:

- `IMG_2113.jpg`: English 26-key letters.
- `IMG_2112.jpg`, `IMG_2117.jpg`: Chinese 9-key pinyin.
- `IMG_2118.jpg`: Chinese 9-key number mode.
- `IMG_2115.jpg`: English number/symbol mode.
- `IMG_2119.jpg`: symbol-more layout reference.
- `IMG_2116.jpg`: emoji panel reference.

The measured key rectangles are normalized against the 1320px-wide screenshots and applied in `IosKeyboardView`. This replaces the previous weight-only approximation for the most important landscape layouts.

## Measurement Format

Use a JSON fixture per reference keyboard:

```json
{
  "device": "iPhone reference model",
  "iosVersion": "reference version",
  "locale": "en_US",
  "orientation": "portrait",
  "keyboardBounds": { "x": 0, "y": 0, "width": 390, "height": 291 },
  "keys": [
    { "id": "q", "x": 3, "y": 8, "width": 34.5, "height": 55 },
    { "id": "w", "x": 43, "y": 8, "width": 34.5, "height": 55 }
  ]
}
```

## Validation

For each target Android width:

1. Render `IosKeyboardView`.
2. Export key rectangles from the layout engine.
3. Scale the reference fixture to the same width.
4. Fail the check when any key exceeds the allowed position or size delta.

The goal for a calibrated profile should be less than 1 dp drift for key position and size.
