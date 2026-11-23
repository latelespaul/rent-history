package com.rentrix.rentrixserver.admin.queue;

/**
 * An admin's answer to a queue card (D-134, D-136): {@code APPROVE} gives the green tick (a city goes live, a report is
 * dismissed); {@code REJECT} leaves the item visible with no tick; {@code HIDE} is for abuse only (reviews, and a
 * reported review).
 */
public enum QueueAction {
	APPROVE, REJECT, HIDE
}
