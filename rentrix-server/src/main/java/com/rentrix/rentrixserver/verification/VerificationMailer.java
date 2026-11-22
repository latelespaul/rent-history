package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;

/**
 * The decision emails (D-65, D-90): approved, rejected (with the reason) and approval revoked (with the reason).
 * Sent after the decision commits, so a rolled-back decision never emails; a failed send is logged and never
 * undoes the decision. The text has no ID numbers or document data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class VerificationMailer {
	
	private final EmailSender email;
	private final BrandProperties brand;
	
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void decided(VerificationDecided decided) {
		if (decided.email() == null) {
			return;
		}
		try {
			email.send(message(decided, brand.name()));
		} catch (RuntimeException e) {
			log.warn("Verification decision email failed: {}", e.toString());
		}
	}
	
	static EmailMessage message(VerificationDecided decided, String brand) {
		String role = decided.role().name().toLowerCase(Locale.ROOT);
		String hello = "Hello " + decided.displayName() + ",\n\n";
		String reason = "Reason: " + label(decided.code()) + (decided.note() == null ? "" : "\n" + decided.note());
		return switch (decided.outcome()) {
			case APPROVED -> new EmailMessage(decided.email(), "Your " + brand + " " + role + " account is approved",
				hello + "Your " + role + " verification is approved. You can now confirm tenancies and write reviews."
					+ "\n\n" + brand);
			case REJECTED -> new EmailMessage(decided.email(), "Your " + brand + " " + role + " verification needs changes",
				hello + "Your " + role + " verification was not approved.\n\n" + reason
					+ "\n\nFix the steps in " + brand + " and submit again.\n\n" + brand);
			case REVOKED -> new EmailMessage(decided.email(), "Your " + brand + " " + role + " approval was withdrawn",
				hello + "An admin withdrew the approval of your " + role + " account.\n\n" + reason
					+ "\n\nFix the steps in " + brand + " and submit again.\n\n" + brand);
			case SUBMITTED, IDENTITY_CHECKED -> throw new IllegalArgumentException("Not a decision: " + decided.outcome());
		};
	}
	
	private static String label(VerificationDecisionCode code) {
		if (code == null) {
			return "Other";
		}
		return switch (code) {
			case NO_VERIFIED_ID -> "No verified ID";
			case ID_UNREADABLE -> "The ID document could not be read";
			case ID_NAME_MISMATCH -> "The name on the ID does not match your profile";
			case AADHAAR_NOT_MASKED -> "The Aadhaar copy is not masked";
			case DOCUMENT_MISSING -> "A document is missing";
			case DUPLICATE_ACCOUNT -> "Another account already uses these details";
			case OTHER -> "Other";
		};
	}
	
}
