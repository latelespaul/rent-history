package com.rentrix.rentrixserver.admin;

/** What an audit row is about (§5.11): the report targets plus the design-v2 and v3 admin targets (D-87). */
public enum ModerationTargetType {
	REVIEW, REVIEW_RESPONSE, USER, PROPERTY, TENANCY,
	APPLICATION, DOCUMENT, CITY, IDENTITY, AREA, SOCIETY, TOWER, WING
}
