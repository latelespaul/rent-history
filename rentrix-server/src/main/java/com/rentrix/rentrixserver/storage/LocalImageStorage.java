package com.rentrix.rentrixserver.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * {@code STORAGE_BACKEND=local} (D-66): files on the API's own disk under {@code STORAGE_LOCAL_DIR}. Private objects
 * live in {@code <dir>/private}, which the API never serves as static files; they are read only by the code that
 * checks a signed URL ({@link DocumentStorage}). Files and folders are owner-only where the file system allows it.
 * {@code PUBLIC} objects come with B9.
 */
@Component
public class LocalImageStorage implements ImageStorage {
	
	private final Path privateRoot;
	
	public LocalImageStorage(@Value("${app.storage.local-dir}") String localDir) {
		this.privateRoot = Path.of(localDir).toAbsolutePath().normalize().resolve("private");
	}
	
	@Override
	public void put(Visibility visibility, String key, byte[] data) throws IOException {
		Path target = path(visibility, key);
		createOwnerOnlyDirectories(target.getParent());
		Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
		try {
			Files.write(temp, data);
			ownerOnly(temp, "rw-------");
			if (Files.exists(target)) {
				throw new FileAlreadyExistsException(key);
			}
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException notAtomic) {
				Files.move(temp, target);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}
	
	@Override
	public InputStream open(Visibility visibility, String key) throws IOException {
		return Files.newInputStream(path(visibility, key));
	}
	
	@Override
	public void delete(Visibility visibility, String key) throws IOException {
		Files.deleteIfExists(path(visibility, key));
	}
	
	@Override
	public boolean exists(Visibility visibility, String key) {
		return Files.isRegularFile(path(visibility, key));
	}
	
	/** The file of {@code key}; a key that is not a {@link StorageKeys} key is refused before any path is built. */
	Path path(Visibility visibility, String key) {
		if (visibility != Visibility.PRIVATE) {
			throw new UnsupportedOperationException("PUBLIC objects come with PLAN B9");
		}
		Path path = privateRoot.resolve(StorageKeys.require(key)).normalize();
		if (!path.startsWith(privateRoot)) {
			throw new IllegalArgumentException("Bad storage key");
		}
		return path;
	}
	
	private static void createOwnerOnlyDirectories(Path dir) throws IOException {
		Files.createDirectories(dir);
		ownerOnly(dir, "rwx------");
		ownerOnly(dir.getParent(), "rwx------");
	}
	
	private static void ownerOnly(Path path, String permissions) {
		try {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
		} catch (UnsupportedOperationException | IOException notPosix) {
			// Windows and some mounted volumes: the folder's own permissions apply.
		}
	}
	
}
