package com.rentrix.rentrixserver.review;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Key of a stored aggregate: users.id for LANDLORD/TENANT, properties.id for PROPERTY. */
@Embeddable
public record RatingAggregateId(
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "subject_type", nullable = false, length = 16)
	RatingSubjectType subjectType,
	
	@Column(name = "subject_id", nullable = false)
	Long subjectId
) {
}
