package io.github.luisr96.whathappenedhere.importer;

import java.io.IOException;
import java.io.InputStream;

/** Opens the raw, still gzipped dump: a URL or a file path. Called again on every reconnect. */
@FunctionalInterface
public interface DumpOpener {

    InputStream open(String source) throws IOException;
}
