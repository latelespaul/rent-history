package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Reviews published" email (D-65) to the person a review is about, or to the home's landlord for a property review.
 * It names the home and links to the web app; never the text, the rating or the author.
 */
@Component
class ReviewNotifier {
	
	private final EmailSender email;
	private final String webBaseUrl;
	private final String brand;
	
	ReviewNotifier(EmailSender email, @Value("${app.web.base-url:}") String webBaseUrl, BrandProperties brand) {
		this.email = email;
		this.brand = brand.name();
		this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
	}
	
	void published(Review review) {
		User to = review.getDirection() == ReviewDirection.TENANT_TO_PROPERTY
			? review.getProperty().getLandlord() : review.getSubjectUser();
		if (to == null || to.getEmail() == null || to.getDeletedAt() != null || UnclaimedLandlord.is(to)) {
			return;
		}
		String about = review.getDirection() == ReviewDirection.TENANT_TO_PROPERTY ? "your home " + home(review)
			: "you, for the tenancy at " + home(review);
		email.send(new EmailMessage(to.getEmail(), "New review on " + brand,
			"""
				A verified review about %s is now published.
				
				Read it, and post one public response if you wish: %s/me/reviews""".formatted(about, webBaseUrl)));
	}
	
	private static String home(Review review) {
		Property property = review.getProperty();
		String name = property.getBuildingName() == null ? property.getPropertyType().name() : property.getBuildingName();
		return name + ", " + property.getLocality();
	}
	
}
