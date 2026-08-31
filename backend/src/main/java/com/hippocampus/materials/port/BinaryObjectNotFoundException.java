package com.hippocampus.materials.port;

public final class BinaryObjectNotFoundException extends BinaryObjectStoreException {

    public BinaryObjectNotFoundException() {
        super("Binary object was not found");
    }
}
