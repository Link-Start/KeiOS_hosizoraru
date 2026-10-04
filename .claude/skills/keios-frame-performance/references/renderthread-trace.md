# RenderThread tracing

Load this only after framestats shows a RenderThread cost that needs attribution to a specific operation.

Capture an asynchronous trace around one interaction:

    adb -s <serial> shell "atrace --async_start -b 32000 gfx view sched"
    # Drive the selected interaction.
    adb -s <serial> shell "atrace --async_stop" > trace.txt

The output is ftrace text. Pair tracing_mark_write B|<pid>|<name> and E events per thread, aggregate each slice by total, maximum, and count, then filter to the app's RenderThread to distinguish it from SurfaceFlinger.

Useful slices:

- flush layers: layer capture and rasterization
- Drawing x y w h: a draw, often full-screen
- CreateGraphicsPipeline: Vulkan pipeline creation
- HWUI RAM cache: shader count added during a capture
- drawLayersInternal for <name>: SurfaceFlinger compositing another screen copy, which can reveal mirroring
