package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Tenancy emails (D-65): invite, confirm request, confirmed / declined / ended, rent record to confirm. Plain
 * text with the property's public name and a link to the web app ({@code APP_BASE_URL}); never documents, ID
 * numbers or the other party's email.
 */
@Component
class TenancyNotifier {
	
	private final EmailSender email;
	private final String webBaseUrl;
	private final String brand;
	
	TenancyNotifier(EmailSender email, @Value("${app.web.base-url:}") String webBaseUrl, BrandProperties brand) {
		this.email = email;
		this.brand = brand.name();
		this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
	}
	
	/** To someone without an account: the single-use link binds the tenancy to the account they sign in with. */
	void invite(Tenancy tenancy, String rawToken) {
		email.send(new EmailMessage(tenancy.getInviteEmail(),
			PublicNames.shortName(tenancy.getLandlord().getDisplayName()) + " added you as a tenant on " + brand,
			"""
				%s says you rent %s, from %s%s.
				
				Sign in to %s with Google using this email address, then confirm or decline:
				%s/tenancy-invite?token=%s
				
				The link works once and expires in 14 days. If you never rented this home, decline it or ignore this
				email.""".formatted(PublicNames.shortName(tenancy.getLandlord().getDisplayName()), home(tenancy),
				tenancy.getStartDate(), rent(tenancy), brand, webBaseUrl,
				URLEncoder.encode(rawToken, StandardCharsets.UTF_8))));
	}
	
	/** To a counterparty who already has an account. */
	void confirmRequest(Tenancy tenancy, User counterparty) {
		User initiator = tenancy.getInitiatedBy() == TenancyParty.LANDLORD ? tenancy.getLandlord() : tenancy.getTenant();
		send(counterparty, "Confirm your tenancy at " + home(tenancy), """
			%s asked you to confirm a tenancy at %s, from %s%s.
			
			Confirm or decline within 14 days: %s""".formatted(PublicNames.shortName(initiator.getDisplayName()),
			home(tenancy), tenancy.getStartDate(), rent(tenancy), link(tenancy)));
	}
	
	/** " at Rs 32000 a month", or ", rent not shared" when the tenant left it out. */
	private static String rent(Tenancy tenancy) {
		return tenancy.getInitialRentInr() == null ? ", rent not shared"
			: " at Rs %d a month".formatted(tenancy.getInitialRentInr());
	}
	
	void confirmed(Tenancy tenancy, User to) {
		send(to, "Tenancy confirmed: " + home(tenancy),
			"Both sides confirmed the tenancy at %s. %s".formatted(home(tenancy), link(tenancy)));
	}
	
	void declined(Tenancy tenancy, User to) {
		String reason = tenancy.getDeclineReason() == null ? "" : "\nReason: " + tenancy.getDeclineReason();
		send(to, "Tenancy declined: " + home(tenancy),
			"The tenancy request at %s was declined.%s\n%s".formatted(home(tenancy), reason, link(tenancy)));
	}
	
	void ended(Tenancy tenancy, User to) {
		send(to, "Tenancy ended: " + home(tenancy), """
			The tenancy at %s was ended on %s by the other party.
			If this is wrong, report it from the tenancy page within 14 days: %s""".formatted(home(tenancy),
			tenancy.getEndDate(), link(tenancy)));
	}
	
	/** To a real landlord: a tenant recorded their own tenancy on the landlord's home (D-142). */
	void recordedByTenant(Tenancy tenancy, User to) {
		send(to, "A tenant recorded a tenancy on your home", """
			%s recorded a tenancy at %s, from %s%s.
			If this is wrong, report it from the tenancy page: %s""".formatted(
			PublicNames.shortName(tenancy.getTenant().getDisplayName()), home(tenancy), tenancy.getStartDate(),
			rent(tenancy), link(tenancy)));
	}
	
	void rentRecordToConfirm(RentRecord record, User to) {
		Tenancy tenancy = record.getTenancy();
		send(to, "Confirm a rent change at " + home(tenancy), """
			A rent of Rs %d a month from %s was added for %s.
			Confirm or dispute it: %s""".formatted(record.getMonthlyRentInr(), record.getEffectiveFrom(),
			home(tenancy), link(tenancy)));
	}
	
	private void send(User to, String subject, String body) {
		if (to != null && to.getEmail() != null && !UnclaimedLandlord.is(to)) {
			email.send(new EmailMessage(to.getEmail(), subject, body));
		}
	}
	
	private String link(Tenancy tenancy) {
		return webBaseUrl + "/tenancies/" + tenancy.getPublicId();
	}
	
	private static String home(Tenancy tenancy) {
		Property property = tenancy.getProperty();
		String name = property.getBuildingName() == null ? property.getPropertyType().name() : property.getBuildingName();
		return name + ", " + property.getLocality();
	}
	
}
