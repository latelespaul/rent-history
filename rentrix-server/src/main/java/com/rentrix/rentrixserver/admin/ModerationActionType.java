package com.rentrix.rentrixserver.admin;

/** Admin actions recorded in the audit log (D-51). */
public enum ModerationActionType {
	DISMISS_REPORT, HIDE_REVIEW, RESTORE_REVIEW, HIDE_RESPONSE, RESTORE_RESPONSE, VOID_TENANCY, SUSPEND_USER, REINSTATE_USER, ACCEPT_DOCUMENT, REJECT_DOCUMENT, HIDE_PROPERTY
}
