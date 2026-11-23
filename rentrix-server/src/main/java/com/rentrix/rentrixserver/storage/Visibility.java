package com.rentrix.rentrixserver.storage;

/** Where an object lives (§15, D-66): {@code PUBLIC} images, or {@code PRIVATE} files served only through signed URLs. */
public enum Visibility {
	PUBLIC, PRIVATE
}
