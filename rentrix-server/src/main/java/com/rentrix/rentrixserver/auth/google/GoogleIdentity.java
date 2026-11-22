package com.rentrix.rentrixserver.auth.google;

/** The claims Rentrix uses from a verified Google ID token (D-75). */
public record GoogleIdentity(String sub, String email, boolean emailVerified, String name, String pictureUrl) {
}
