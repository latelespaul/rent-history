package com.rentrix.rentrixserver.common.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledJobLockRepository extends JpaRepository<ScheduledJobLock, String> {

}
