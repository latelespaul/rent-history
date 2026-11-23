package com.rentrix.rentrixserver.admin.queue;

/** Why an admin refuses the green tick on a home, tenancy or review, or hides a review (D-87, D-134). */
public enum ItemRejectCode {
	/** Nothing the team could check backs it up. */
	CANNOT_CONFIRM,
	/** The facts look wrong: address, dates, rent. */
	DETAILS_WRONG,
	/** The same item is already on the site. */
	DUPLICATE,
	/** Hide only: abusive, hateful or off-topic. */
	ABUSE,
	/** Hide only: names, phone numbers or other personal data. */
	PERSONAL_DATA,
	OTHER
}
