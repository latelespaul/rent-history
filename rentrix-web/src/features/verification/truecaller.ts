/**
 * Truecaller's mobile-web verification runs only in Android browsers, with the Truecaller app installed: the
 * deep link opens the app's consent sheet, and Truecaller then calls our API, not the page.
 */
export function canUseTruecaller(userAgent: string = navigator.userAgent): boolean {
  return /Android/i.test(userAgent)
}

/** Opens the `truecallersdk://` deep link. Without the app nothing happens and the page keeps focus. */
export function openDeepLink(deepLink: string): void {
  window.location.assign(deepLink)
}
