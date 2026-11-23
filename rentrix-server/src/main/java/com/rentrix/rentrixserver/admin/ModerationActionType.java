package com.rentrix.rentrixserver.admin;

/**
 * Admin actions recorded in the audit log: the D-51 moderation set, plus the design-v2 decisions (applications,
 * listings, documents, cities, admin role, D-87) and the design-v3 identity and location actions. {@code VERIFY_ITEM} /
 * {@code REJECT_ITEM} give or refuse the green tick on a tenancy or review (D-134).
 */
public enum ModerationActionType {
	DISMISS_REPORT, HIDE_REVIEW, RESTORE_REVIEW, HIDE_RESPONSE, RESTORE_RESPONSE, VOID_TENANCY, SUSPEND_USER,
	REINSTATE_USER, ACCEPT_DOCUMENT, REJECT_DOCUMENT, HIDE_PROPERTY,
	APPROVE_APPLICATION, REJECT_APPLICATION, REVOKE_APPROVAL, APPROVE_PROPERTY, REJECT_PROPERTY, VIEW_DOCUMENT,
	CREATE_CITY, UPDATE_CITY, ACTIVATE_CITY, DEACTIVATE_CITY, GRANT_ADMIN, REVOKE_ADMIN,
	VERIFY_IDENTITY, REJECT_IDENTITY, REVOKE_IDENTITY, CREATE_AREA, UPDATE_AREA, APPROVE_LOCATION, UPDATE_LOCATION,
	VERIFY_ITEM, REJECT_ITEM,
	/** The user deleted their own account (D-58): the one self-service row; its "admin" is the anonymised user. */
	ACCOUNT_DELETED
}
