package io.github.fugoclient.mcefmod;

public class JCEFHelper {
    public static void registerSandboxBypass() {
        org.cef.CefApp.addAppHandler(new org.cef.handler.CefAppHandlerAdapter(new String[]{}) {
            @Override
            public void onBeforeCommandLineProcessing(String processType, org.cef.callback.CefCommandLine commandLine) {
                System.out.println("[Fugo Client] Appending sandbox bypass and performance switches");
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
                commandLine.appendSwitchWithValue("renderer-process-limit", "1");
                commandLine.appendSwitchWithValue("js-flags", "--max-old-space-size=64 --gc-interval=100");
                commandLine.appendSwitchWithValue("purge-memory-after-idle-delay-ms", "1000");
                commandLine.appendSwitch("disable-web-security");
                commandLine.appendSwitchWithValue("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            }
        });
        System.out.println("[Fugo Client] Sandbox bypass handler successfully registered with CefApp!");
    }
}
