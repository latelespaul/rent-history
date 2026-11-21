package com.rentrix.rentrixserver.common.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ScheduledJobLockRepository extends JpaRepository<ScheduledJobLock, String> {

	/** Takes the lock when its {@code lock_until} has passed. Returns the number of rows changed (0 or 1). */
	@Modifying
	@Query("""
		update ScheduledJobLock l set l.lockUntil = :until, l.lockedAt = :now, l.lockedBy = :by
		where l.name = :name and l.lockUntil <= :now""")
	int takeIfFree(@Param("name") String name, @Param("now") Instant now, @Param("until") Instant until,
		@Param("by") String lockedBy);

	/** Ends this instance's hold (identified by {@code lockedAt}) at {@code until}. */
	@Modifying
	@Query("""
		update ScheduledJobLock l set l.lockUntil = :until
		where l.name = :name and l.lockedBy = :by and l.lockedAt = :lockedAt""")
	int release(@Param("name") String name, @Param("by") String lockedBy, @Param("lockedAt") Instant lockedAt,
		@Param("until") Instant until);

}
