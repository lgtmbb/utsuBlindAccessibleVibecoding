package com.utsusynth.utsu.files.voicebank;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts a UTAU/Utsu voicebank .zip file to a destination folder, working around a very common
 * problem with older Japanese voicebanks: their file names inside the .zip are encoded as
 * Shift-JIS (a.k.a. MS932/Windows-31J), not UTF-8, but the standard .zip format does not record
 * which charset was used. If the wrong charset is assumed, folder and file names turn into
 * unreadable garbage ("mojibake") on extraction -- and once that happens, there is no way to
 * recover the original names from the already-extracted files, since the wrong Unicode characters
 * are permanently the ones stored on disk.
 *
 * <p>This class tries decoding the .zip's entry names as UTF-8 first (the modern standard), and
 * as Shift-JIS as a fallback, then picks whichever decoding produces more valid-looking text
 * (fewer replacement/control characters, more recognizable Japanese characters). This choice is
 * made automatically so that a blind user is never required to visually judge which decoding
 * "looks right" -- something that is not possible to do by ear either, since a mis-decoded name
 * and a correctly-decoded one are both just unfamiliar text until read letter by letter.
 *
 * <p>A plain-text extraction-log.txt is written into the destination folder recording exactly
 * what was extracted and which charset was chosen, so the result can be reviewed later with a
 * screen reader or text editor without needing to re-run the extraction.
 */
public class VoicebankZipExtractor {

    /** Result of a single extraction, for reporting back to the UI and to the log file. */
    public static class ExtractionResult {
        public final String charsetUsed;
        public final int fileCount;
        public final File destination;
        public final List<String> warnings;

        ExtractionResult(String charsetUsed, int fileCount, File destination,
                List<String> warnings) {
            this.charsetUsed = charsetUsed;
            this.fileCount = fileCount;
            this.destination = destination;
            this.warnings = warnings;
        }
    }

    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    public ExtractionResult extract(File zipFile, File destinationDir) throws IOException {
        Charset chosenCharset = pickBestCharset(zipFile);
        List<String> warnings = new ArrayList<>();
        int fileCount = 0;

        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Could not create destination folder: " + destinationDir);
        }

        try (ZipFile zip = new ZipFile(zipFile, chosenCharset)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outFile = new File(destinationDir, entry.getName());

                // Guard against "zip slip": an entry name trying to escape the destination
                // folder via "../" sequences.
                String destPath = destinationDir.getCanonicalPath();
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(destPath + File.separator) && !outPath.equals(destPath)) {
                    warnings.add("Skipped unsafe entry: " + entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (InputStream in = zip.getInputStream(entry);
                        FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                fileCount++;
            }
        }

        writeExtractionLog(destinationDir, zipFile, chosenCharset, fileCount, warnings);
        return new ExtractionResult(chosenCharset.name(), fileCount, destinationDir, warnings);
    }

    /**
     * Reads just the entry names (cheaply, without extracting any file contents) using both
     * UTF-8 and Shift-JIS, scores each set of names for how "valid" it looks, and returns
     * whichever charset scored better. UTF-8 is preferred on a tie, since it is the modern
     * standard and is what recent archiving tools use by default.
     */
    private Charset pickBestCharset(File zipFile) throws IOException {
        int utf8Score;
        int sjisScore;
        try (ZipFile zip = new ZipFile(zipFile, java.nio.charset.StandardCharsets.UTF_8)) {
            utf8Score = scoreEntryNames(zip);
        }
        try (ZipFile zip = new ZipFile(zipFile, SHIFT_JIS)) {
            sjisScore = scoreEntryNames(zip);
        }
        return sjisScore > utf8Score
                ? SHIFT_JIS
                : java.nio.charset.StandardCharsets.UTF_8;
    }

    /**
     * Higher score = more plausible text. Penalizes the Unicode replacement character (a sure
     * sign of a decoding mismatch) and other control characters; rewards common Japanese
     * character ranges (hiragana, katakana, and CJK ideographs) as well as plain ASCII, both of
     * which are legitimate in voicebank file names.
     */
    private int scoreEntryNames(ZipFile zip) {
        int score = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '\uFFFD') {
                    score -= 10; // Replacement character: definite decoding failure.
                } else if (c < 0x20) {
                    score -= 10; // Control character: should never appear in a real file name.
                } else if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0x4E00 && c <= 0x9FFF)) {
                    score += 3; // Hiragana, katakana, or CJK ideograph: plausible for a Japanese
                                // voicebank.
                } else if (c < 0x80) {
                    score += 1; // Plain ASCII: always plausible.
                }
            }
        }
        return score;
    }

    private void writeExtractionLog(File destinationDir, File zipFile, Charset charsetUsed,
            int fileCount, List<String> warnings) {
        File logFile = new File(destinationDir, "extraction-log.txt");
        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(logFile.toPath(), java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("Utsu2 voicebank extraction log");
            writer.println("Source zip: " + zipFile.getAbsolutePath());
            writer.println("Destination: " + destinationDir.getAbsolutePath());
            writer.println("File name charset used: " + charsetUsed.name());
            writer.println("Files extracted: " + fileCount);
            if (warnings.isEmpty()) {
                writer.println("Warnings: none");
            } else {
                writer.println("Warnings:");
                for (String warning : warnings) {
                    writer.println("  - " + warning);
                }
            }
        } catch (IOException e) {
            // The extraction itself already succeeded; a failure to write the log is reported
            // as a warning to the caller rather than failing the whole operation.
            warnings.add("Could not write extraction-log.txt: " + e.getMessage());
        }
    }
}
