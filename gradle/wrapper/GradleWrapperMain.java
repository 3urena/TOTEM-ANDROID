package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small, project-local Gradle bootstrap launcher.
 * It deliberately implements only the wrapper behavior needed by this repository.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() { }

    public static void main(String[] args) {
        int result = run(args);
        System.exit(result);
    }

    private static int run(String[] args) {
        try {
            Path project = Paths.get("").toAbsolutePath().normalize();
            Path propertiesFile = project.resolve("gradle/wrapper/gradle-wrapper.properties");
            if (!Files.isRegularFile(propertiesFile)) {
                throw new IOException("Missing " + propertiesFile);
            }

            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(propertiesFile)) {
                properties.load(in);
            }

            String distributionUrl = required(properties, "distributionUrl");
            String checksum = required(properties, "distributionSha256Sum").toLowerCase();
            URL url = URI.create(distributionUrl).toURL();
            String filename = Paths.get(url.getPath()).getFileName().toString();
            if (!filename.matches("gradle-[A-Za-z0-9._-]+-bin\\.zip")) {
                throw new IOException("Unexpected Gradle distribution filename: " + filename);
            }
            String distributionName = filename.substring(0, filename.length() - "-bin.zip".length());

            String gradleUserHome = System.getenv("GRADLE_USER_HOME");
            Path userHome = gradleUserHome == null || gradleUserHome.trim().isEmpty()
                    ? Paths.get(System.getProperty("user.home"), ".gradle")
                    : Paths.get(gradleUserHome);
            Path cache = userHome.resolve("totem-wrapper");
            Path install = cache.resolve(distributionName);
            String executableName = isWindows() ? "gradle.bat" : "gradle";
            Path executable = install.resolve(distributionName).resolve("bin").resolve(executableName);

            if (!Files.isRegularFile(executable)) {
                Files.createDirectories(cache);
                Path archive = cache.resolve(filename + ".part");
                Path staging = cache.resolve(distributionName + ".staging");
                deleteTree(staging);
                Files.deleteIfExists(archive);
                System.out.println("Downloading verified Gradle distribution " + distributionName + "...");
                download(url, archive, integerProperty(properties, "networkTimeout", 30000));
                String actual = sha256(archive);
                if (!actual.equals(checksum)) {
                    Files.deleteIfExists(archive);
                    throw new IOException("Gradle distribution checksum mismatch. Expected " + checksum + " but got " + actual);
                }
                extractSafely(archive, staging);
                Files.deleteIfExists(archive);
                Path extracted = staging.resolve(distributionName);
                if (!Files.isRegularFile(extracted.resolve("bin").resolve(executableName))) {
                    throw new IOException("Gradle archive did not contain the expected executable");
                }
                deleteTree(install);
                move(extracted, install);
                deleteTree(staging);
            }

            executable = install.resolve(distributionName).resolve("bin").resolve(executableName);
            if (!Files.isRegularFile(executable)) {
                throw new IOException("Gradle executable not found at " + executable);
            }

            List<String> command = new ArrayList<String>();
            if (isWindows()) {
                command.add("cmd.exe");
                command.add("/d");
                command.add("/s");
                command.add("/c");
                StringBuilder line = new StringBuilder("\"");
                line.append(quoteWindows(executable.toString()));
                for (String arg : args) {
                    line.append(' ').append(quoteWindows(arg));
                }
                line.append('\"');
                command.add(line.toString());
            } else {
                command.add("/bin/sh");
                command.add(executable.toString());
                command.addAll(Arrays.asList(args));
            }

            Process process = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start();
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Gradle bootstrap interrupted.");
            return 130;
        } catch (Exception e) {
            System.err.println("Gradle bootstrap failed: " + e.getMessage());
            System.err.println("Check Java, network access, and gradle/wrapper/gradle-wrapper.properties.");
            return 1;
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing property: " + key);
        return value.trim();
    }

    private static int integerProperty(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static void download(URL url, Path destination, int timeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(Math.max(timeout, 10000));
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "TOTEM-MOBILE-GradleBootstrap/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status + " downloading Gradle");
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    private static void extractSafely(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination.normalize())) {
                    throw new IOException("Unsafe path in Gradle distribution archive");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            Path[] all = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : all) Files.deleteIfExists(path);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String quoteWindows(String arg) {
        return "\"" + arg.replace("\"", "\\\"") + "\"";
    }
}
