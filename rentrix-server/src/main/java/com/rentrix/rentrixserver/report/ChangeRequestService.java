package com.rentrix.rentrixserver.report;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyService;
import com.rentrix.rentrixserver.report.dto.ChangeRequest;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * "Ask our team to change this" (D-156): when a rule protecting other people's records blocks a creator's edit or
 * delete, they file a request instead. It is an open {@link Report} with reason {@code CHANGE_REQUEST}, so it shows in
 * the admin report queue. Only the item's creator may file one; no approval is needed. A caller has one report per
 * item (D-49): a repeat request adds its note to an open one, or reopens a handled one with the new note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeRequestService {
	
	private final UserRepository users;
	private final TenancyRepository tenancies;
	private final PropertyRepository properties;
	private final ReviewRepository reviews;
	private final ReportRepository reports;
	
	/** {@code reports.details} holds at most this many characters. */
	private static final int MAX_DETAILS = 1000;
	
	@Transactional
	public UUID create(ChangeRequest request, AuthenticatedUser caller) {
		User me = users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
		UUID id = request.targetId();
		Long targetId;
		User creator;
		switch (request.targetType()) {
			case TENANCY -> {
				var tenancy = tenancies.findByPublicId(id).orElseThrow(() -> ApiException.notFound("Tenancy"));
				targetId = tenancy.getId();
				creator = TenancyService.initiator(tenancy);
			}
			case PROPERTY -> {
				var property = properties.findByPublicIdAndDeletedAtIsNull(id)
					.orElseThrow(() -> ApiException.notFound("Property"));
				targetId = property.getId();
				creator = PropertyService.createdBy(property, me.getPublicId()) ? me : null;
			}
			case REVIEW -> {
				var review = reviews.findByPublicId(id).orElseThrow(() -> ApiException.notFound("Review"));
				targetId = review.getId();
				creator = review.getAuthor();
			}
			default -> throw new IllegalStateException();
		}
		if (creator == null || !creator.getId().equals(me.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_creator",
				"Only the person who added this can ask our team to change it.");
		}
		ReportTargetType type = ReportTargetType.valueOf(request.targetType().name());
		String note = request.note().trim();
		Report report = reports.findByReporterIdAndTargetTypeAndTargetId(me.getId(), type, targetId).orElse(null);
		if (report == null) {
			report = new Report();
			report.setReporter(me);
			report.setTargetType(type);
			report.setTargetId(targetId);
			report.setReason(ReportReason.CHANGE_REQUEST);
			report.setDetails(note);
		} else if (report.getStatus() == ReportStatus.OPEN) {
			String details = report.getDetails() == null ? note : report.getDetails() + "\n\n" + note;
			report.setDetails(details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
		} else {
			report.setStatus(ReportStatus.OPEN);
			report.setReason(ReportReason.CHANGE_REQUEST);
			report.setDetails(note);
			report.setHandledBy(null);
			report.setHandledAt(null);
			report.setResolutionNote(null);
		}
		Report saved = reports.saveAndFlush(report);
		log.info("Change request {} on {} {} by {}", saved.getPublicId(), request.targetType(), id, me.getPublicId());
		return saved.getPublicId();
	}
	
}
