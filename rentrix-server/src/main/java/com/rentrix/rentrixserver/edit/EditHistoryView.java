package com.rentrix.rentrixserver.edit;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of an item's edit history for admins (D-156): who changed which field, when, from what to what, and
 * whether the item had the green tick then. {@code editRef} groups the rows of one save.
 */
public record EditHistoryView(String editRef, Instant at, UUID editorId, String editorName, EditChange change,
	String field, String before, String after, boolean wasVerified) {
	
	static EditHistoryView of(EditRecord r) {
		return new EditHistoryView(r.getEditRef(), r.getCreatedAt(), r.getEditor().getPublicId(),
			r.getEditor().getDisplayName(), r.getChange(), r.getField(), r.getOldValue(), r.getNewValue(),
			r.isWasVerified());
	}
	
}
