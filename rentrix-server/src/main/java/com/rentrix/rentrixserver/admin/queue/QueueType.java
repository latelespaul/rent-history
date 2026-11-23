package com.rentrix.rentrixserver.admin.queue;

/**
 * What one approvals-queue card is about (D-136): a user's verification application, one PAN / Aadhaar / LinkedIn
 * check, a home, a tenancy, a review, a city someone typed that is not live yet, or an open report.
 */
public enum QueueType {
	USER, IDENTITY, PROPERTY, TENANCY, REVIEW, CITY, REPORT
}
