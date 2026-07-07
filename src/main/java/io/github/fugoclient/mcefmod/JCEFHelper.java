package io.github.fugoclient.mcefmod;

public class JCEFHelper {
    public static void registerSandboxBypass() {
        org.cef.CefApp.addAppHandler(new org.cef.handler.CefAppHandlerAdapter(new String[]{}) {
            @Override
            public void onBeforeCommandLineProcessing(String processType, org.cef.callback.CefCommandLine commandLine) {
                // Aggressive performance flags for 300 FPS
                commandLine.appendSwitch("no-sandbox");
                commandLine.appendSwitch("disable-gpu");
                commandLine.appendSwitch("disable-gpu-compositing");
                commandLine.appendSwitch("disable-gpu-sandbox");
                commandLine.appendSwitch("disable-extensions");
                commandLine.appendSwitch("disable-pdf-extension");
                commandLine.appendSwitch("disable-plugins-discovery");
                commandLine.appendSwitch("disable-background-networking");
                commandLine.appendSwitch("disable-sync");
                commandLine.appendSwitch("disable-default-apps");
                commandLine.appendSwitch("disable-accelerated-2d-canvas");
                commandLine.appendSwitch("disable-accelerated-video-decode");
                commandLine.appendSwitch("disable-software-rasterizer");
                commandLine.appendSwitch("disable-smooth-scrolling");
                commandLine.appendSwitch("disable-threaded-scrolling");
                commandLine.appendSwitch("disable-composited-antialiasing");
                commandLine.appendSwitch("disable-2d-canvas-clip-aa");
                commandLine.appendSwitch("disable-2d-canvas-image-chromium");
                commandLine.appendSwitch("disable-canvas-aa");
                commandLine.appendSwitch("disable-3d-apis");
                commandLine.appendSwitch("disable-oopr-debug-crash-dump");
                commandLine.appendSwitch("in-process-gpu");
                commandLine.appendSwitchWithValue("renderer-process-limit", "1");
                commandLine.appendSwitchWithValue("js-flags", "--max-old-space-size=32 --gc-interval=50");
                commandLine.appendSwitchWithValue("purge-memory-after-idle-delay-ms", "500");
                commandLine.appendSwitchWithValue("max-unused-resource-memory-usage-percentage", "5");
                commandLine.appendSwitchWithValue("memory-pressure-offload-threshold-mb", "50");
                commandLine.appendSwitchWithValue("renderer-will-send-idle-message-interval-ms", "5000");
                commandLine.appendSwitch("disable-web-security");
                commandLine.appendSwitchWithValue("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            }
        });
    }
}