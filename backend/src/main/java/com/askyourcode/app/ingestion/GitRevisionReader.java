package com.askyourcode.app.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Reads the repository HEAD without invoking a shell. Non-Git directories return null. */
public final class GitRevisionReader {
    private GitRevisionReader() {}

    public static String readHead(Path repositoryRoot) {
        if (!Files.exists(repositoryRoot.resolve(".git"))) return null;
        Process process;
        try {
            process = new ProcessBuilder("git", "-C", repositoryRoot.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out reading Git HEAD for " + repositoryRoot);
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (process.exitValue() != 0 || !output.matches("[0-9a-fA-F]{40,64}")) {
                throw new IllegalStateException("Unable to read Git HEAD for " + repositoryRoot + ": " + output);
            }
            return output.toLowerCase(java.util.Locale.ROOT);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute git while reading repository HEAD.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading Git HEAD.", e);
        }
    }
}
