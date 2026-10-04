# Frame-time measurement setup

Read this only when the task needs new timing data. Current journey scripts and historical caveats are indexed in [the frame-budget record](../../../../docs/planning/hwui-frame-budget.md).

## Target and build

- Identify the intended device and bind ADB commands to its current serial. KeiOS performance conclusions may depend on a physical LTPO phone; do not assume a target from an earlier session.
- Use the releaseDiagnostic build and package os.kei.diag. Do not force-stop or drive os.kei for this workflow: it is the user's normal installation and may refresh real tracked repositories. A debug build is not comparable to release.
- Enable Developer options > Profile HWUI rendering > In adb shell dumpsys gfxinfo when the selected procedure uses gfxinfo.

## Capture conditions

Check for mirroring before every capture. scrcpy or another virtual display adds full-screen composition work and can alter both timing and GPU clocks.

    adb -s <serial> shell dumpsys SurfaceFlinger --display-id
    pgrep -lf scrcpy

Use settled samples and compare the same panel refresh regime. The phone can move between 120Hz and 60Hz after interaction, changing the frame deadline even when the app's work is similar.

Do not use the legacy janky-frame percentage or the 99th GPU percentile from gfxinfo for A/B acceptance; prior unchanged-build runs showed those values to be unstable. Use the CPU/GPU percentiles and per-frame stage breakdown described in the current frame-budget record.

## Existing capture commands

Run only the script that matches the requested journey:

    cd scripts/perf && D=<serial> PKG=os.kei.diag DWELL=3 ./idle_dwell.sh home
    cd scripts/perf && D=<serial> ./hwui_journey.sh home_scroll home_scroll

For a direct framestats capture:

    adb -s <serial> shell dumpsys gfxinfo os.kei.diag reset
    sleep 3
    adb -s <serial> shell dumpsys gfxinfo os.kei.diag framestats > /tmp/fs.txt
    cd scripts/perf && python3 frame_stages.py /tmp/fs.txt

For a single page switch, force-stop and relaunch the diagnostic package, settle, reset the counter, make one switch, and capture that switch. Pool comparable passes when the expected effect is near the instrument's noise floor.

An A17 AVD varied by roughly ±40% across repeated captures of an unchanged build in a 2026-09-15 investigation. Treat that as historical evidence against non-interleaved emulator A/Bs for RenderThread/GPU changes. Prefer a suitable phone; if an emulator comparison is unavoidable, put both paths in one build and interleave runs.

Record the source revision and local changes, build variant, device/API, refresh regime, mirroring state, journey, and capture outputs. Distinguish collected frames and packaging from a user-perceived performance result.
