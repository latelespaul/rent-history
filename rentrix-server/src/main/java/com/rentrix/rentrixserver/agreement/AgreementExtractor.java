package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.storage.DocumentType;

import java.util.Set;

/**
 * Reads an agreement's key fields (D-127). v1 has one implementation, {@link ManualAgreementExtractor}: the uploader
 * types them. A vision extractor (OCR plus an LLM) is not built until its cost is approved;
 * {@code AGREEMENT_EXTRACTOR=manual} is the only accepted value. Whatever extracts, the uploader sees and confirms the
 * fields before the agreement is {@code SUBMITTED}, and every field is masked for Aadhaar numbers.
 */
public interface AgreementExtractor {
	
	/** The fields to store, already masked, the extractor's name and the flags raised while reading them. */
	record ExtractedAgreement(AgreementFields fields, String extractor, Set<AgreementFlag> flags) {
	}
	
	ExtractedAgreement extract(byte[] file, DocumentType type, AgreementFields typed);
	
}
