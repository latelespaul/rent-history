package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D-43 content rules, checked at submit and on every edit so personal data stays out of public text: length,
 * no phone numbers, emails, web addresses or flat / house numbers, and a basic profanity list. A broken rule is
 * {@code 422 validation_failed} with one field error per rule on {@code body}.
 */
public final class ReviewContentRules {

	public static final int REVIEW_MIN = 30;
	public static final int REVIEW_MAX = 2000;
	public static final int RESPONSE_MAX = 1000;

	/** A run of 10 or more digits, allowing spaces, dots, dashes and brackets between them (phone numbers). */
	private static final Pattern DIGIT_RUN = Pattern.compile("\\+?\\d[\\d\\s().-]{8,}\\d");
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
	private static final Pattern URL = Pattern.compile(
		"(?i)(https?://|www\\.)\\S+|\\b[a-z0-9-]+\\.(com|in|net|org|io|co|me|info|app|xyz|biz)\\b");
	/** A number followed by a duration or distance ("3 years", "10 min") is not a unit number. */
	private static final String NOT_MEASURE = "(?!\\s*(years?|yrs?|months?|weeks?|days?|kms?|mins?|minutes?)\\b)";
	private static final String UNIT_WORD = "flat|unit|apt|apartment|house|door|room";
	/**
	 * Only explicit unit markers: "flat no 302", "house no. 12", "unit #5", "room number 4B"; block-unit codes tied
	 * to a unit, tower, wing or block word ("flat B 402", "tower C-1204", "wing #1203"); and bare codes of one capital
	 * letter and 3-4 digits ("A-1203", "B 402"), never multi-letter road codes like "NH-48" nor "A" or "I" then a
	 * space ("A 1200 sq ft").
	 */
	private static final Pattern UNIT = Pattern.compile(
		"(?i)\\b(" + UNIT_WORD + ")\\s*(no\\.?|number|#)\\s*[a-z]?-?\\d{1,4}[a-z]?\\b" + NOT_MEASURE
			+ "|\\b(" + UNIT_WORD + "|tower|wing|block)\\s+(#\\s*|[a-z](\\s?-\\s?|\\s))\\d{2,4}\\b" + NOT_MEASURE
			+ "|(?-i:\\b([A-Z]-?|[B-HJ-Z] )\\d{3,4}\\b)" + NOT_MEASURE);
	/** A basic list, English and common Hindi transliterations; matched as whole words. */
	private static final Pattern PROFANITY = Pattern.compile("(?i)\\b(fuck\\w*|shit\\w*|bitch\\w*|bastard\\w*|"
		+ "asshole\\w*|cunt\\w*|dickhead\\w*|motherfuck\\w*|chutiya\\w*|madarchod\\w*|behenchod\\w*|bhenchod\\w*|"
		+ "gandu\\w*|randi\\w*|harami\\w*)\\b");

	private ReviewContentRules() {
	}

	/** Checks a review body and returns it trimmed. {@code unitLabel} is the home's own flat number, if any. */
	public static String review(String body, String unitLabel) {
		return check(body, REVIEW_MIN, REVIEW_MAX, unitLabel);
	}

	/** Checks a response body (1–1000 characters, same personal-data rules) and returns it trimmed. */
	public static String response(String body, String unitLabel) {
		return check(body, 1, RESPONSE_MAX, unitLabel);
	}

	private static String check(String raw, int min, int max, String unitLabel) {
		String body = raw == null ? "" : raw.strip();
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		int length = body.codePointCount(0, body.length());
		if (length < min) {
			errors.add(error("too_short", "Write at least " + min + " characters."));
		} else if (length > max) {
			errors.add(error("too_long", "Keep it to " + max + " characters."));
		}
		if (containsPhone(body)) {
			errors.add(error("contains_phone", "Remove the phone number; reviews are public."));
		}
		if (EMAIL.matcher(body).find()) {
			errors.add(error("contains_email", "Remove the email address; reviews are public."));
		}
		if (URL.matcher(body).find()) {
			errors.add(error("contains_url", "Remove the web address."));
		}
		if (UNIT.matcher(body).find() || containsUnitLabel(body, unitLabel)) {
			errors.add(error("contains_unit_number", "Remove the flat or house number; it identifies the tenant."));
		}
		if (PROFANITY.matcher(body).find()) {
			errors.add(error("profanity", "Remove the offensive words."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return body;
	}

	private static boolean containsPhone(String body) {
		Matcher m = DIGIT_RUN.matcher(body);
		while (m.find()) {
			if (m.group().chars().filter(Character::isDigit).count() >= 10) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsUnitLabel(String body, String unitLabel) {
		if (unitLabel == null || unitLabel.isBlank()) {
			return false;
		}
		String label = unitLabel.strip().toLowerCase(Locale.ROOT);
		return Pattern.compile("(?<![\\p{Alnum}])" + Pattern.quote(label) + "(?![\\p{Alnum}])")
			.matcher(body.toLowerCase(Locale.ROOT)).find();
	}

	private static ApiProblem.FieldError error(String code, String message) {
		return new ApiProblem.FieldError("body", code, message);
	}

}
