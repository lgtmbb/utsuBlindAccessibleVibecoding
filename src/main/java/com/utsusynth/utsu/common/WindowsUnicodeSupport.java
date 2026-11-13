package com.utsusynth.utsu.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Checks, and offers to fix, the Windows setting "Beta: Use Unicode UTF-8 for worldwide language
 * support" (registry values ACP/OEMCP under HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage).
 * Without it, folder and file names with Japanese (or other non-Latin) characters reach
 * non-Unicode programs mangled.
 *
 * <p>Runs at every start while the setting is off (unless turned off in Preferences > Utsu2
 * Settings) and on demand from that tab. It used to run only on a "first launch" detected by a
 * missing preferences file, which never happens for anyone who already used any Utsu build, so
 * the check silently never ran.
 */
public final class WindowsUnicodeSupport {
    private WindowsUnicodeSupport() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("windows");
    }

    /**
     * Checks, on Windows only, whether the OS is configured to correctly translate non-Latin
     * (e.g. Japanese) file names for non-Unicode-aware programs. When it is not, Java (and
     * therefore this program) receives already-mangled versions of such file names -- this is
     * the same setting responsible for garbled Japanese voicebank folder/file names in general,
     * not something specific to this program. If misconfigured, offers to fix it directly via
     * the same registry values Windows' own "Beta: Use Unicode UTF-8" checkbox controls.
     *
     * <p>Two things about this fix cannot be made invisible, because they are enforced by
     * Windows itself and not by this program: changing this value is a machine-wide setting, so
     * Windows will show its own "Do you want to allow this app to make changes" permission
     * prompt (this is the standard, documented way for a program to request that kind of
     * change -- not something being bypassed); and the new value only takes effect after a
     * restart.
     */
    public static void checkAndOfferFix(boolean reportEvenIfFine) {
        if (!isWindows()) {
            if (reportEvenIfFine) {
                com.utsusynth.utsu.common.AccessibleDialogs.showMessage(
                        null, "Windows Unicode file name support",
                        "This check only applies to Windows.");
            }
            return;
        }
        if (isUtf8CodePageEnabled()) {
            if (reportEvenIfFine) {
                com.utsusynth.utsu.common.AccessibleDialogs.showMessage(
                        null, "Windows Unicode file name support",
                        "Windows is already set to use Unicode UTF-8 for worldwide language "
                                + "support. Nothing needs to be changed.");
            }
            return;
        }
        boolean shouldFix = com.utsusynth.utsu.common.AccessibleDialogs.confirm(
                null,
                "Windows Unicode file name support",
                "Windows is currently set to translate file names using a non-Unicode character "
                        + "set. This means voicebank folder and file names containing Japanese "
                        + "(or other non-Latin) characters may appear as unreadable garbled "
                        + "text, both in this program and in File Explorer.\n\n"
                        + "This program can fix that by changing the relevant Windows system "
                        + "setting (the same one controlled by \"Beta: Use Unicode UTF-8 for "
                        + "worldwide language support\" in Windows Settings). Two things will "
                        + "happen that this program cannot skip, because Windows itself enforces "
                        + "them: a Windows permission prompt will appear, asking to allow this "
                        + "change (choose Yes); and a restart is needed afterward for the change "
                        + "to take effect.\n\n"
                        + "This message appears at every start until the setting is on; the startup check can be turned off in Preferences, Utsu2 Settings.",
                "Fix now",
                "Not now");
        if (shouldFix) {
            applyWindowsUtf8CodePageFix();
        }
    }

    /**
     * Reads the two registry values that "Beta: Use Unicode UTF-8 for worldwide language
     * support" controls (HKLM\SYSTEM\CurrentControlSet\Control\Nls\CodePage, values ACP and
     * OEMCP). Reading the registry does not require administrator rights. Returns true only if
     * both are already set to 65001 (UTF-8).
     */
    public static boolean isUtf8CodePageEnabled() {
        try {
            String acp = readRegistryValue("ACP");
            String oemcp = readRegistryValue("OEMCP");
            return "65001".equals(acp) && "65001".equals(oemcp);
        } catch (IOException | InterruptedException e) {
            // If this can't be determined, don't offer a fix for a problem that could not be
            // confirmed to exist.
            return true;
        }
    }

    private static String readRegistryValue(String valueName) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage",
                "/v", valueName)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        process.waitFor();
        for (String line : output.split("\\R")) {
            if (line.trim().startsWith(valueName)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0) {
                    return parts[parts.length - 1];
                }
            }
        }
        return "";
    }

    /**
     * Writes ACP and OEMCP to 65001 (UTF-8) via a single elevated helper process, so Windows
     * only shows one permission prompt for both values. This is done by writing a small,
     * temporary PowerShell script and asking Windows to run it elevated (Start-Process -Verb
     * RunAs), which is the standard, documented mechanism for a program to request elevation for
     * one specific action -- the program itself is not, and does not become, elevated.
     */
    private static void applyWindowsUtf8CodePageFix() {
        try {
            File script = File.createTempFile("utsu2-utf8-codepage-fix", ".ps1");
            script.deleteOnExit();
            String scriptContents =
                    "reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage\" "
                            + "/v ACP /t REG_SZ /d 65001 /f\r\n"
                            + "reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage\" "
                            + "/v OEMCP /t REG_SZ /d 65001 /f\r\n"
                            + "exit $LASTEXITCODE\r\n";
            Files.writeString(script.toPath(), scriptContents,
                    java.nio.charset.StandardCharsets.UTF_8);

            String launcherCommand = String.format(
                    "$p = Start-Process -FilePath powershell.exe -ArgumentList "
                            + "'-NoProfile','-ExecutionPolicy','Bypass','-File','%s' "
                            + "-Verb RunAs -Wait -PassThru; exit $p.ExitCode",
                    script.getAbsolutePath().replace("'", "''"));
            Process elevated = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command", launcherCommand)
                    .inheritIO()
                    .start();
            int exitCode = elevated.waitFor();

            if (exitCode == 0 && isUtf8CodePageEnabled()) {
                boolean restartNow = com.utsusynth.utsu.common.AccessibleDialogs.confirm(
                        null,
                        "Setting updated",
                        "The Windows setting was updated successfully. A restart is required "
                                + "before file names display correctly.",
                        "Restart now",
                        "Restart later");
                if (restartNow) {
                    try {
                        new ProcessBuilder("shutdown", "/r", "/t", "5").start();
                    } catch (IOException e) {
                        // Not fatal; the user can still restart manually.
                    }
                }
            } else {
                showFixFailedAlert(null);
            }
        } catch (IOException | InterruptedException e) {
            showFixFailedAlert(e);
        }
    }

    private static void showFixFailedAlert(Exception e) {
        String message = "Could not change the Windows setting automatically (the permission "
                + "prompt may have been declined, or something else went wrong"
                + (e == null ? "" : ": " + e.getMessage()) + ").\n\n"
                + "You can still do it manually:\n"
                + "1. Open Settings, then \"Time & language\", then \"Language & region\".\n"
                + "2. Under \"Related settings\", choose \"Administrative language settings\".\n"
                + "3. Click \"Change system locale...\".\n"
                + "4. Check \"Beta: Use Unicode UTF-8 for worldwide language support\".\n"
                + "5. Restart Windows.";
        com.utsusynth.utsu.common.AccessibleDialogs.showMessage(
                null, "Windows Unicode file name support", message);
    }
}
