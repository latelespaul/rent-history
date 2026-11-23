package com.rentrix.rentrixserver.report;

/** Why content was reported (§5.11); {@code CHANGE_REQUEST} is a creator asking the team to change their own item (D-156). */
public enum ReportReason {
	FAKE_TENANCY, PERSONAL_DATA, HARASSMENT_OR_HATE, OFF_TOPIC, CONFLICT_OF_INTEREST, SPAM, OTHER, CHANGE_REQUEST
}
