package com.warnomodmaker.combine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runs WARNO's Python mod tools, streaming their output and stopping them on cancellation. */
public class ProcessRunner {
    /**
     * Runs a program to completion and returns its exit code. Each non-blank output line goes to {@code output}
     * (standard output) or {@code errors} (standard error), trimmed.
     */
    public int run(String executable, List<String> arguments, Path workingDirectory,
                   Consumer<String> output, Consumer<String> errors, Cancel token) {
        token.check();
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process;
        try {
            process = builder.start();
            process.getOutputStream().close();
        } catch (IOException e) {
            throw new CombineException("Could not start " + Path.of(executable).getFileName() + ": " + e.getMessage(), e);
        }
        var fatal = new java.util.concurrent.atomic.AtomicReference<String>();
        Consumer<String> detect = line -> {
            if (line.startsWith("[Fatal error]") && fatal.compareAndSet(null, line)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        };
        Thread out = pump(process.getInputStream(), detect.andThen(output));
        Thread err = pump(process.getErrorStream(), detect.andThen(errors));
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (token.isCancelled()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor();
                    throw new CancellationException();
                }
            }
            out.join();
            err.join();
            if (fatal.get() != null) throw new CombineException(fatal.get());
            return process.exitValue();
        } catch (InterruptedException e) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancellationException();
        }
    }

    private static Thread pump(InputStream stream, Consumer<String> sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) if (!line.trim().isEmpty()) sink.accept(line.trim());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "sdk-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
