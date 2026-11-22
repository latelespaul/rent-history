package com.rentrix.rentrixserver.rating;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which published reviews the stored headline ratings count ({@code app.ratings.headline}, D-135). {@code ALL} (the
 * default until admins can give reviews the green tick, PLAN W9) counts every published, counted review;
 * {@code VERIFIED} counts only green-tick reviews. Switching it needs the aggregates rebuilt, which the nightly
 * repair ({@link RatingRecomputer#recomputeAll()}, D-33) does.
 */
@ConfigurationProperties(prefix = "app.ratings")
public record RatingProperties(Headline headline) {
	
	public enum Headline { ALL, VERIFIED }
	
	public RatingProperties {
		headline = headline == null ? Headline.ALL : headline;
	}
	
	boolean verifiedOnly() {
		return headline == Headline.VERIFIED;
	}
	
}
