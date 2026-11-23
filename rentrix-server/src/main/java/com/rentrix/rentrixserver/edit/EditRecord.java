package com.rentrix.rentrixserver.edit;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One field an owner changed on their own entry, before and after (D-156). Append-only: rows are never updated or
 * deleted; write them through {@link EditHistory}. Readable by admins only.
 */
@Entity
@Table(name = "edit_history")
@Getter
@Setter
public class EditRecord extends BaseEntity {
	
	/** Groups the rows of one save. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "edit_ref", nullable = false, length = 36)
	private String editRef;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "editor_id", nullable = false)
	private User editor;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "target_type", nullable = false, length = 16)
	private EditTarget targetType;
	
	@Column(name = "target_id", nullable = false)
	private Long targetId;
	
	/** The target's {@code public_id}. */
	@Column(name = "target_ref", nullable = false, length = 36)
	private String targetRef;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "change_type", nullable = false, length = 16)
	private EditChange change;
	
	@Column(name = "field", nullable = false, length = 64)
	private String field;
	
	@Column(name = "old_value", length = 2000)
	private String oldValue;
	
	@Column(name = "new_value", length = 2000)
	private String newValue;
	
	/** Whether the item had the green tick when it was changed (the change took it away). */
	@Column(name = "was_verified", nullable = false)
	private boolean wasVerified;
	
}
