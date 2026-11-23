package com.rentrix.rentrixserver.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * The one storage abstraction for images and private documents (§15, D-66). Code depends only on this interface.
 * The rental-agreement PR (PLAN B18) lands before B9, so today there is only {@link LocalImageStorage} with
 * {@link Visibility#PRIVATE} objects; B9 adds {@code PUBLIC} objects, public URLs and the {@code s3} backend.
 * Keys are chosen by the caller and are always random ({@code agreements/<uuid>}), never a file name.
 */
public interface ImageStorage {
	
	/** Stores {@code data} under {@code key}, replacing nothing: a key already in use is an error. */
	void put(Visibility visibility, String key, byte[] data) throws IOException;
	
	/** The stored bytes; {@link java.nio.file.NoSuchFileException} when the object is gone. */
	InputStream open(Visibility visibility, String key) throws IOException;
	
	/** Deletes the object; a missing object is not an error. */
	void delete(Visibility visibility, String key) throws IOException;
	
	/** True when the object is stored. */
	boolean exists(Visibility visibility, String key);
	
}
