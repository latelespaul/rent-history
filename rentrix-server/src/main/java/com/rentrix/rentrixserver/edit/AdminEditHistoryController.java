package com.rentrix.rentrixserver.edit;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /admin/edit-history?type=TENANCY|PROPERTY|REVIEW&id=}: what the owner changed on one item, newest first
 * (D-156). {@code /admin/**} answers only on the admin host and only to an admin console session (§13.3); the main
 * site never shows this history.
 */
@RestController
@RequiredArgsConstructor
public class AdminEditHistoryController {
	
	private final EditRecordRepository records;
	
	@GetMapping("/admin/edit-history")
	@Transactional(readOnly = true)
	public List<EditHistoryView> history(@RequestParam EditTarget type, @RequestParam UUID id) {
		return records.findByTargetTypeAndTargetRefOrderByCreatedAtDescIdDesc(type, id.toString()).stream()
			.map(EditHistoryView::of)
			.toList();
	}
	
}
