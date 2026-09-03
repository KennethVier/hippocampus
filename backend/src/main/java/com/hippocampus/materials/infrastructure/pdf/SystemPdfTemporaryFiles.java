package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class SystemPdfTemporaryFiles implements PdfTemporaryFiles {
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    @Override
    public Path create() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempFile(
                    "hippocampus-pdf-", ".tmp", PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
        }
        return Files.createTempFile("hippocampus-pdf-", ".tmp");
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
