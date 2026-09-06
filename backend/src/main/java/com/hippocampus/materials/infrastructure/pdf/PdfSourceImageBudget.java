package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

final class PdfSourceImageBudget {
    private final int maxDimension;
    private final long maxPixelsPerImage;
    private final long maxPixelsPerPage;
    private final Set<Object> inspectedImages = Collections.newSetFromMap(new IdentityHashMap<>());
    private long totalPixels;

    PdfSourceImageBudget(int maxDimension, long maxPixelsPerImage, long maxPixelsPerPage) {
        if (maxDimension <= 0 || maxPixelsPerImage <= 0 || maxPixelsPerPage <= 0) {
            throw new IllegalArgumentException("Source image limits must be positive");
        }
        this.maxDimension = maxDimension;
        this.maxPixelsPerImage = maxPixelsPerImage;
        this.maxPixelsPerPage = maxPixelsPerPage;
    }

    void inspect(PDImage image) throws IOException {
        Object identity = image.getCOSObject();
        if (!inspectedImages.add(identity)) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension) {
            throw new SourceImageLimitExceededException();
        }

        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
            totalPixels = Math.addExact(totalPixels, pixels);
        } catch (ArithmeticException exception) {
            throw new SourceImageLimitExceededException(exception);
        }
        if (pixels > maxPixelsPerImage || totalPixels > maxPixelsPerPage) {
            throw new SourceImageLimitExceededException();
        }

        if (image instanceof PDImageXObject imageXObject) {
            PDImageXObject mask = imageXObject.getMask();
            if (mask != null) {
                inspect(mask);
            }
            PDImageXObject softMask = imageXObject.getSoftMask();
            if (softMask != null) {
                inspect(softMask);
            }
        }
    }

    static final class SourceImageLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SourceImageLimitExceededException() {}

        SourceImageLimitExceededException(Throwable cause) {
            super(cause);
        }
    }
}
