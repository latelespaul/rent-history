package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.PhoneOtpChallenge;
import com.rentrix.rentrixserver.identity.PhoneOtpChallengeRepository;
import com.rentrix.rentrixserver.notification.sms.AdminRelaySmsSender;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.notification.sms.SmsSender;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /admin/sms-relay} (D-137): the phone OTP codes the preview-only {@code admin-relay} sender holds, newest
 * first, each with the user who asked for it and the masked phone, so an admin can pass the code to a trusted
 * first user. {@code /admin/**} is {@code ADMIN} only. {@code 404} unless {@code SMS_BACKEND=admin-relay}. The
 * PLAN B13 approvals queue shows the same code on the new user's card; this endpoint serves the preview until then.
 */
@Slf4j
@RestController
public class AdminSmsRelayController {
	
	/** One relayed code; the phone is masked (D-106). */
	public record RelayedCodeView(UUID userId, String displayName, String email, String phone, String code,
		Instant sentAt, Instant expiresAt) {
	}
	
	private final AdminRelaySmsSender relay;
	private final PhoneOtpChallengeRepository challenges;
	
	public AdminSmsRelayController(ObjectProvider<SmsSender> sender, PhoneOtpChallengeRepository challenges) {
		this.relay = sender.getIfAvailable() instanceof AdminRelaySmsSender r ? r : null;
		this.challenges = challenges;
	}
	
	@GetMapping("/admin/sms-relay")
	@Transactional(readOnly = true)
	public List<RelayedCodeView> pending(@AuthenticationPrincipal AuthenticatedUser caller) {
		if (relay == null) {
			throw ApiException.notFound("SMS relay");
		}
		List<RelayedCodeView> codes = relay.pending().stream().map(code -> {
			PhoneOtpChallenge challenge = challenges.findFirstByPhoneE164OrderByCreatedAtDesc(code.phoneE164())
				.orElse(null);
			return new RelayedCodeView(challenge == null ? null : challenge.getUser().getPublicId(),
				challenge == null ? null : challenge.getUser().getDisplayName(),
				challenge == null ? null : challenge.getUser().getEmail(), PhoneMasks.mask(code.phoneE164()),
				code.code(), code.sentAt(), code.expiresAt());
		}).toList();
		log.info("Admin {} read {} relayed SMS codes", caller.publicId(), codes.size());
		return codes;
	}
	
}
