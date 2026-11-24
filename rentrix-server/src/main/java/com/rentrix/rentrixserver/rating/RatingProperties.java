package com.rentrix.rentrixserver.rating;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which published reviews the stored headline ratings count ({@code app.ratings.headline}, D-135). {@code VERIFIED}
 * (the default since D-163, captain 2026-11-24: anyone signed in may post, so only green-tick reviews make the headline)
 * counts only green-tick reviews; {@code ALL} (the interim default before) counts every published, counted review.
 * Switching it needs the aggregates rebuilt, which the nightly repair ({@link RatingRecomputer#recomputeAll()}, D-33)
 * does, or an admin at once with {@code POST /admin/ratings/recompute} (D-163).
 */
@ConfigurationProperties(prefix = "app.ratings")
public record RatingProperties(Headline headline) {
	
	public enum Headline { ALL, VERIFIED }
	
	public RatingProperties {
		headline = headline == null ? Headline.VERIFIED : headline;
	}
	
	boolean verifiedOnly() {
		return headline == Headline.VERIFIED;
	}
	
}
