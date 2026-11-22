package com.rentrix.rentrixserver.notification.email;

/** One plain-text email (D-65). Never holds document data or ID numbers. */
public record EmailMessage(String to, String subject, String body) {
}
