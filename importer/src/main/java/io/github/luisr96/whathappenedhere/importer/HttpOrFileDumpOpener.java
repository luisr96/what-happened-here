package io.github.luisr96.whathappenedhere.importer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Streams the dump straight from Wikimedia (or reads a local file), so the 150 GB dump never has
 * to be stored. Each call is a fresh request from the start: a gzip stream can't be resumed in the
 * middle, so {@link DumpEntityReader} skips what it already read instead.
 *
 * <p>HttpURLConnection rather than HttpClient for its socket read timeout: over hours a
 * connection can stall without being reset, and only a read timeout turns that into the
 * IOException that makes the reader reconnect instead of waiting forever.
 */
@Component
public class HttpOrFileDumpOpener implements DumpOpener {

    private static final String USER_AGENT =
            "what-happened-here-importer/0.1 (https://github.com/luisr96/what-happened-here)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

    @Override
    public InputStream open(String source) throws IOException {
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            return Files.newInputStream(Path.of(source));
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(source).toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
        connection.setInstanceFollowRedirects(true);
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + status + " for " + source);
        }
        return connection.getInputStream();
    }
}
