package com.hippocampus.materials.infrastructure.storage.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.hippocampus.materials.port.BinaryObjectStore;

class FileSystemStorageConfigurationTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void enablesFilesystemOnlyForExplicitLocalConfiguration() {
        context("local", temporaryDirectory.resolve("local"), true)
                .run(result -> assertThat(result).hasSingleBean(BinaryObjectStore.class));
    }

    @Test
    void disablesFilesystemForPilotEvenWhenSelected() {
        context("pilot", temporaryDirectory.resolve("pilot"), true)
                .run(result -> assertThat(result).doesNotHaveBean(BinaryObjectStore.class));
    }

    @Test
    void pilotOverridesLocalRegardlessOfProfileOrdering() {
        context("local,pilot", temporaryDirectory.resolve("combined-one"), true)
                .run(result -> assertThat(result).doesNotHaveBean(BinaryObjectStore.class));
        context("pilot,local", temporaryDirectory.resolve("combined-two"), true)
                .run(result -> assertThat(result).doesNotHaveBean(BinaryObjectStore.class));
    }

    @Test
    void remainsDisabledWhenBackendSelectionIsMissing() {
        context("local", temporaryDirectory.resolve("missing-backend"), false)
                .run(result -> assertThat(result).doesNotHaveBean(BinaryObjectStore.class));
    }

    @Test
    void failsSafelyForInvalidRoot() throws IOException {
        Path file = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(file, "data");

        context("local", file, true).run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseMessage("Filesystem storage root is invalid");
        });
    }

    @Test
    void failsSafelyForSymbolicRootWhenSupported() throws IOException {
        Path actual = temporaryDirectory.resolve("actual");
        Files.createDirectory(actual);
        Path link = temporaryDirectory.resolve("link");
        try {
            Files.createSymbolicLink(link, actual);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false, "Symbolic links are unavailable: " + exception.getClass().getSimpleName());
        }

        context("local", link, true).run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseMessage("Filesystem storage root is invalid");
        });
    }

    @Test
    void unrelatedDatasourceFreeContextRemainsUnaffected() {
        new ApplicationContextRunner()
                .run(result -> assertThat(result).hasNotFailed().doesNotHaveBean(BinaryObjectStore.class));
    }

    private static ApplicationContextRunner context(String profiles, Path root, boolean selectBackend) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(FileSystemStorageConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "hippocampus.storage.filesystem.root=" + root);
        return selectBackend
                ? runner.withPropertyValues("hippocampus.storage.backend=filesystem")
                : runner;
    }
}
