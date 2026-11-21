package com.rentrix.rentrixserver.common.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Row lock for in-process scheduled jobs (D-08), ShedLock table layout: one row per job,
 * held by the instance named in {@code lockedBy} until {@code lockUntil}.
 */
@Entity
@Table(name = "scheduled_job_lock")
@Getter
@Setter
public class ScheduledJobLock {
	
	@Id
	@Column(name = "name", length = 64)
	private String name;
	
	@Column(name = "lock_until", nullable = false)
	private Instant lockUntil;
	
	@Column(name = "locked_at", nullable = false)
	private Instant lockedAt;
	
	@Column(name = "locked_by", nullable = false)
	private String lockedBy;
	
}
