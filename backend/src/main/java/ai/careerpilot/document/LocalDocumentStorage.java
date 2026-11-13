package ai.careerpilot.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

@Component
public class LocalDocumentStorage implements DocumentStorage {
    private final Path root;
    public LocalDocumentStorage(@Value("${app.documents.storage-root:${user.home}/.careerpilot/private-documents}") String configuredRoot) throws IOException {
        Path configured=Path.of(configuredRoot);
        if (!configured.isAbsolute()) throw new IllegalArgumentException("Document storage root must be absolute.");
        Files.createDirectories(configured);
        try { Files.setPosixFilePermissions(configured, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { /* Windows ACLs must protect the configured data root. */ }
        this.root=configured.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Document storage root is not a directory.");
        createPrivateDirectory(root.resolve("quarantine"));
        createPrivateDirectory(root.resolve("available"));
    }
    private void createPrivateDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Storage directories must not be symbolic links.");
        Files.createDirectories(path);
        if (Files.isSymbolicLink(path) || !path.toRealPath().getParent().equals(root)) throw new IOException("Storage directory escaped the configured root.");
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { /* Windows ACLs are inherited from the private data root. */ }
    }
    private Path path(String key, String area) {
        if (key==null || !key.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid opaque storage key.");
        Path parent=root.resolve(area);
        Path target=parent.resolve(key).normalize();
        if (!target.getParent().equals(parent)) throw new IllegalArgumentException("Invalid storage key.");
        return target;
    }
    @Override public String storeQuarantined(InputStream input,String key) throws IOException {
        Path target=path(key,"quarantine");
        try (OutputStream output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            input.transferTo(output);
        } catch (IOException ex) { Files.deleteIfExists(target); throw ex; }
        try { Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) {}
        return key;
    }
    @Override public void promote(String key) throws IOException {
        Path source=path(key,"quarantine"), target=path(key,"available");
        if (Files.isSymbolicLink(source) || Files.exists(target,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe storage promotion.");
        Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);
    }
    @Override public InputStream open(String key) throws IOException {
        Path target=path(key,"available");
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)) throw new FileNotFoundException("Document not found.");
        return Files.newInputStream(target,StandardOpenOption.READ);
    }
    @Override public void remove(String key) throws IOException {
        Files.deleteIfExists(path(key,"quarantine"));
        Files.deleteIfExists(path(key,"available"));
    }
    @Override public boolean exists(String key) throws IOException {
        Path quarantine = path(key, "quarantine");
        Path available = path(key, "available");
        if (Files.isSymbolicLink(quarantine) || Files.isSymbolicLink(available)) {
            throw new IOException("Unexpected symbolic link in private document storage.");
        }
        return Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS)
            || Files.exists(available, LinkOption.NOFOLLOW_LINKS);
    }
    @Override public Path resolveForInternalUse(String key) { return path(key,"available"); }
}
