package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.storage.DocumentType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code AGREEMENT_EXTRACTOR=manual} (D-127): the fields are what the uploader typed next to the file; the file is not
 * read. Every text field is trimmed and masked for Aadhaar-like numbers ({@link AadhaarMask}, D-131); the stamp or
 * registration number is kept as typed (masked too, so a 12-digit Aadhaar typed there never lands).
 */
@Component
public class ManualAgreementExtractor implements AgreementExtractor {
	
	public static final String NAME = "MANUAL";
	
	@Override
	public ExtractedAgreement extract(byte[] file, DocumentType type, AgreementFields typed) {
		Set<AgreementFlag> flags = EnumSet.noneOf(AgreementFlag.class);
		if (Stream.of(typed.landlordName(), typed.tenantName(), typed.addressText(), typed.anchorNumber())
			.anyMatch(AadhaarMask::contains)) {
			flags.add(AgreementFlag.AADHAAR_MASKED_ON_INTAKE);
		}
		AgreementFields masked = new AgreementFields(
			clean(typed.landlordName()),
			clean(typed.tenantName()),
			clean(typed.addressText()),
			blankToNull(typed.pincode()),
			typed.monthlyRentInr(),
			typed.depositInr(),
			typed.startDate(),
			typed.endDate(),
			anchor(typed.anchorNumber()),
			typed.anchorDate());
		return new ExtractedAgreement(masked, NAME, flags);
	}
	
	private static String clean(String text) {
		String value = blankToNull(text);
		return value == null ? null : AadhaarMask.mask(value.replaceAll("\\s+", " "));
	}
	
	private static String anchor(String number) {
		String value = clean(number);
		return value == null ? null : value.toUpperCase(Locale.ROOT);
	}
	
	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}
	
}
