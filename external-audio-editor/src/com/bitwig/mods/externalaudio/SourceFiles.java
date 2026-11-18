package com.bitwig.mods.externalaudio;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Collapses slices of the same source, while refusing an ambiguous source selection. */
final class SourceFiles {
    private final Set<File> files = new LinkedHashSet<>();

    void add(File file) throws IOException {
        if (file == null) throw new IOException("The selected audio has an unresolved sample file.");
        files.add(file.getCanonicalFile());
    }

    File single() throws IOException {
        if (files.isEmpty()) throw new IOException("The selected audio has no sample file.");
        if (files.size() != 1) {
            throw new IOException("This clip uses multiple sample files. Select one audio event instead.");
        }
        return files.iterator().next();
    }
}
