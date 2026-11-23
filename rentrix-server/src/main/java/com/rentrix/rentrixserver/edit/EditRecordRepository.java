package com.rentrix.rentrixserver.edit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditRecordRepository extends JpaRepository<EditRecord, Long> {
	
	/** The edit history of one item, newest first (index {@code ix_edit_history_target}). */
	List<EditRecord> findByTargetTypeAndTargetRefOrderByCreatedAtDescIdDesc(EditTarget targetType, String targetRef);
	
	/** Whether the owner ever changed this item (the queue card's {@code EDITED} flag). */
	boolean existsByTargetTypeAndTargetRef(EditTarget targetType, String targetRef);
	
}
