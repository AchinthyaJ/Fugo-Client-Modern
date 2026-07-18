package io.github.fugoclient.mcefmod;

public class JCEFHelper {
    public static void registerSandboxBypass() {
        org.cef.CefApp.addAppHandler(new org.cef.handler.CefAppHandlerAdapter(new String[]{}) {
            @Override
            public void onBeforeCommandLineProcessing(String processType, org.cef.callback.CefCommandLine commandLine) {
                // === CRITICAL: Limit CEF internal render rate to 30 FPS ===
                // Without this, CEF tries to paint at unlimited FPS, burning CPU/GPU on texture uploads
                commandLine.appendSwitchWithValue("windowless-frame-rate", "30");

                // Sandbox / security
                commandLine.appendSwitch("no-sandbox");
                commandLine.appendSwitch("disable-web-security");

                // GPU: use software rendering for offscreen mode (avoids GPU readback overhead)
                commandLine.appendSwitch("disable-gpu");
                commandLine.appendSwitch("disable-gpu-compositing");
                commandLine.appendSwitch("disable-gpu-sandbox");
                commandLine.appendSwitch("disable-gpu-vsync");

                // Disable unused features to save memory/CPU
                commandLine.appendSwitch("disable-extensions");
                commandLine.appendSwitch("disable-plugins-discovery");
                commandLine.appendSwitch("disable-background-networking");
                commandLine.appendSwitch("disable-sync");
                commandLine.appendSwitch("disable-default-apps");
                commandLine.appendSwitch("disable-smooth-scrolling");
                commandLine.appendSwitch("disable-threaded-scrolling");

                // Canvas/2D optimizations
                commandLine.appendSwitch("disable-accelerated-2d-canvas");
                commandLine.appendSwitch("disable-canvas-aa");
                commandLine.appendSwitch("disable-2d-canvas-clip-aa");
                commandLine.appendSwitch("disable-composited-antialiasing");

                // Reduce CEF repaint triggers
                commandLine.appendSwitch("disable-image-animation-resample");

                // Memory constraints
                commandLine.appendSwitchWithValue("renderer-process-limit", "1");
                commandLine.appendSwitchWithValue("js-flags", "--max-old-space-size=32 --gc-interval=50");
                commandLine.appendSwitchWithValue("purge-memory-after-idle-delay-ms", "500");
                commandLine.appendSwitchWithValue("max-unused-resource-memory-usage-percentage", "5");
                commandLine.appendSwitchWithValue("memory-pressure-offload-threshold-mb", "50");

                commandLine.appendSwitchWithValue("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            }
        });
    }
}
