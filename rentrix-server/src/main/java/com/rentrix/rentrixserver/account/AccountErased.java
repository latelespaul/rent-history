package com.rentrix.rentrixserver.account;

import java.util.UUID;

/**
 * Published inside the account-deletion transaction, after the core erasure (D-58, D-159). A feature that stores
 * more of a user's personal data, such as uploaded files, listens to it and erases its share in the same transaction
 * (or after commit, for files outside the database).
 *
 * @param userId       internal id of the deleted account
 * @param userPublicId its public id, which stays on the anonymised row
 */
public record AccountErased(Long userId, UUID userPublicId) {
}
