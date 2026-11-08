/**
 * The "Remove ads" patch. {@code AdBlock} answers Wordfeud's ad calls itself, so the app's ad plugin never receives
 * an ad unit and never shows or preloads an interstitial. The ad consent form is still shown (see
 * {@link app.wordfeudwordfinder.extension.ads.AdBlock#BLOCKED}).
 *
 * <p>Wordfeud dependency: {@code FlutterJNI.handlePlatformMessage} (hooked by {@code RemoveAdsPatch}), the public
 * {@code FlutterJNI} methods {@code invokePlatformMessageResponseCallback} and {@code cleanupMessageData} (called by
 * reflection) and the pigeon channel names {@code dev.flutter.pigeon.wordfeud.AdApi.*} in {@link
 * app.wordfeudwordfinder.extension.ads.AdBlock#BLOCKED}. All are checked by tools/check-apk/check_apk.py. The Java ad
 * plugin itself is obfuscated and is deliberately not touched.
 */
package app.wordfeudwordfinder.extension.ads;
