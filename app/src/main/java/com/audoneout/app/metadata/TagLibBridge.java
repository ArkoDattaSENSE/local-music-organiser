package com.audoneout.app.metadata;

import com.kyant.taglib.Metadata;
import com.kyant.taglib.TagLib;
import java.util.HashMap;

public final class TagLibBridge {
    private TagLibBridge() {}

    public static HashMap<String, String[]> readProperties(int fileDescriptor) {
        Metadata metadata = TagLib.getMetadata(fileDescriptor, false);
        return metadata == null ? null : new HashMap<>(metadata.getPropertyMap());
    }

    public static boolean saveProperties(
            int fileDescriptor,
            HashMap<String, String[]> properties
    ) {
        return TagLib.savePropertyMap(fileDescriptor, properties);
    }
}
