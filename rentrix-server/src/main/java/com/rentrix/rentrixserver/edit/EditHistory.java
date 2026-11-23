package com.rentrix.rentrixserver.edit;

import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes the owners' edit history (D-156): one append-only row per changed field, before and after, grouped by one
 * {@code edit_ref} per save. Joins the caller's transaction, so a change and its history commit or roll back together.
 * Values are masked before they are stored: a 12-digit Aadhaar-like number keeps its last 4 digits and a PAN-like
 * code is hidden, so no full ID number ever reaches this table (D-106).
 */
@Component
@RequiredArgsConstructor
public class EditHistory {
	
	static final int MAX_VALUE = 2000;
	
	private static final Pattern AADHAAR = Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?(\\d{4})(?!\\d)");
	private static final Pattern PAN = Pattern.compile("(?i)(?<![a-z0-9])[a-z]{5}\\d{4}[a-z](?![a-z0-9])");
	
	private final EditRecordRepository records;
	
	/** The fields one save changed, in order; a field whose value is the same before and after is left out. */
	public static final class Changes {
		
		private final List<String[]> rows = new ArrayList<>();
		
		public Changes field(String name, Object before, Object after) {
			if (!Objects.equals(before, after)) {
				rows.add(new String[] {name, text(before), text(after)});
			}
			return this;
		}
		
		public boolean isEmpty() {
			return rows.isEmpty();
		}
		
		private static String text(Object value) {
			return value == null ? null : value.toString();
		}
		
	}
	
	/** Records one save of {@code changes}; does nothing when nothing changed. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void edited(User editor, EditTarget type, Long targetId, UUID targetRef, boolean wasVerified,
		Changes changes) {
		String editRef = UUID.randomUUID().toString();
		for (String[] row : changes.rows) {
			save(editor, type, targetId, targetRef, EditChange.EDITED, row[0], row[1], row[2], wasVerified, editRef);
		}
	}
	
	/** Records that the owner deleted the item; {@code summary} says what it was. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleted(User editor, EditTarget type, Long targetId, UUID targetRef, boolean wasVerified,
		String field, String summary) {
		save(editor, type, targetId, targetRef, EditChange.DELETED, field, summary, null, wasVerified,
			UUID.randomUUID().toString());
	}
	
	private void save(User editor, EditTarget type, Long targetId, UUID targetRef, EditChange change, String field,
		String before, String after, boolean wasVerified, String editRef) {
		EditRecord row = new EditRecord();
		row.setEditRef(editRef);
		row.setEditor(editor);
		row.setTargetType(type);
		row.setTargetId(targetId);
		row.setTargetRef(targetRef.toString());
		row.setChange(change);
		row.setField(field.length() > 64 ? field.substring(0, 64) : field);
		row.setOldValue(mask(before));
		row.setNewValue(mask(after));
		row.setWasVerified(wasVerified);
		records.save(row);
	}
	
	/** No full Aadhaar or PAN in the history: {@code 1234 5678 9012} → {@code XXXX XXXX 9012}, a PAN → {@code XXXXXXXXXX}. */
	static String mask(String value) {
		if (value == null) {
			return null;
		}
		String masked = AADHAAR.matcher(value).replaceAll("XXXX XXXX $1");
		masked = PAN.matcher(masked).replaceAll("XXXXXXXXXX");
		return masked.length() > MAX_VALUE ? masked.substring(0, MAX_VALUE - 1) + "…" : masked;
	}
	
}
