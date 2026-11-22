package com.rentrix.rentrixserver.notification.sms;

/**
 * The masked form of a phone number, the only form any response or log line carries (D-106): an Indian number
 * shows as {@code +91 XXXXXX3210}, any other as {@code XXXXXX3210}.
 */
public final class PhoneMasks {
	
	private PhoneMasks() {
	}
	
	/** Masks an E.164 number down to its last 4 digits; null stays null. */
	public static String mask(String phoneE164) {
		if (phoneE164 == null) {
			return null;
		}
		String last4 = phoneE164.length() < 4 ? "" : phoneE164.substring(phoneE164.length() - 4);
		return phoneE164.startsWith("+91") ? fromLast4(last4) : "XXXXXX" + last4;
	}
	
	/** The mask of an Indian mobile number from its last 4 digits. */
	public static String fromLast4(String last4) {
		return "+91 XXXXXX" + last4;
	}
	
}
