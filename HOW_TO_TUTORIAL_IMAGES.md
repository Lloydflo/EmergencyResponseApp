# Image-based “How to use this app” guide

`HowToUseScreen.kt` now displays tutorial artwork supplied by the project owner. No source-code edit is needed when replacing the artwork.

## Recommended: multiple tutorial pages

1. In Android Studio, create this folder when it does not exist:

   `app/src/main/res/drawable-nodpi/`

2. Add the images using these exact lowercase filenames:

   - `how_to_01.webp`
   - `how_to_02.webp`
   - `how_to_03.webp`
   - and so on, up to `how_to_30.webp`

The app automatically sorts and displays the numbered images. PNG and JPG also work, but WebP is recommended for a smaller APK.

## Alternative: one long tutorial image

Use this exact filename:

`how_to_tutorial.webp`

When this file exists, it takes priority over the numbered pages. A very tall image consumes more memory, so multiple pages are safer for older phones.

## Image preparation

- Use lowercase letters, numbers, and underscores only.
- Do not use spaces or hyphens in Android resource filenames.
- Recommended width: 1080 pixels.
- Keep important text away from the outer 32 pixels.
- Use RGB or RGBA color mode.
- Export at about 75–85% WebP quality when the artwork contains screenshots.
- Test the guide in both light and dark app themes; the image itself is not recolored.

Users can tap any tutorial image to open a full-screen viewer, then pinch to zoom and drag to inspect labels.
