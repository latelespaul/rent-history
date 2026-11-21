package com.rentrix.rentrixserver.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * {@code created_at} / {@code updated_at} on every table (D-14, UTC).
 * A {@code createdAt} set before the first save is kept, so the dev seeder can write
 * historical timestamps (D-68). Values are kept at millisecond precision so they
 * round-trip identically on SQLite, MySQL and PostgreSQL.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TimestampedEntity {
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	
	public static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}
	
	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = now();
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}
	
	@PreUpdate
	protected void onUpdate() {
		updatedAt = now();
	}
	
}
