Drop the cat-egg sound file here as one of:

  bleh.mp3
  bleh.wav
  bleh.m4a
  bleh.ogg

(Alternative names supported: bleh_sound, cat_bleh.)

Android raw-resource rules:
  - All-lowercase filename.
  - No spaces, no dashes — underscores only.
  - The file must be present at build time so the resource compiler
    generates the matching R.raw.<name> id.

Once the file is in place, rebuild and the Settings → About OffPay
easter egg will play the sound alongside the cat overlay.
