# MediaGrab

Android app that saves photos and videos from X/Twitter, Instagram, Facebook, YouTube and TikTok.
Paste a link, or use **Share → MediaGrab** from any of those apps.
Files are saved to `Downloads/MediaGrab` and listed in the **Media** tab (tap to open,
long-press to share, delete or retry).

## Download

Get the latest APK from the [Releases page](https://github.com/KimochiDesu/MediaGrab/releases/latest).

1. Download the `MediaGrab-vX.Y.Z.apk` file on your phone (Android 10 or newer).
2. Open it and allow "Install unknown apps" for your browser or file manager when asked.
3. Open **MediaGrab**, paste a link, or share a post to it from another app.

The app isn't on Google Play because Play doesn't allow YouTube downloaders.

After that the app updates itself: when it opens it checks this repo's latest release and offers
the new version (Settings → Updates to turn this off or check by hand). The first time, Android
asks you to allow MediaGrab to install apps.

## How it works

| Site | Order tried | Login needed? |
|---|---|---|
| X/Twitter | 1. `cdn.syndication.twimg.com/tweet-result` (the embed widget's endpoint)<br>2. `TweetResultByRestId` GraphQL — with your x.com session if logged in, else a guest token | Only for protected accounts you follow |
| Instagram post/reel | 1. `api/v1/media/{id}/info/` with your session<br>2. public `graphql/query` (`xdt_shortcode_media`)<br>3. `/p/{code}/embed/captioned/` page | Often; Instagram rate-limits anonymous access heavily |
| Instagram story | `web_profile_info` → `feed/reels_media` | Yes |
| Facebook video/reel | 1. `plugins/video.php` embed (`hd_src`/`sd_src`)<br>2. the video page anonymously (reels)<br>3. the video page with your session | Only for non-public videos |
| Facebook photo/post | the photo or post page (anonymous, then with your session): every `viewer_image`, else `og:image` | Only for non-public posts |
| YouTube video/Short | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | No (age-restricted videos fail) |
| TikTok video | the embed player `/embed/v2/{id}` (its MP4 links work without cookies) | No; private videos can't be downloaded |
| TikTok photo slideshow | the mobile post page's `api-data` script (`imagePost.images`) | No |

YouTube only serves ready-made video+audio files up to 360p. Higher qualities download the
H.264 video and AAC audio tracks separately and merge them on the phone with `MediaMuxer`.

### Logins

Logins open the site's own page in an in-app browser; the app never sees your password and
reuses only the resulting cookies, stored privately on the device. Instagram hides its
"Log in with Facebook" button inside apps, so the Instagram login screen has a
**Continue with Facebook instead** button: log in to Facebook there and Instagram then offers
"Continue as <you>". That Facebook login is also used for Facebook downloads.

## App features

- **Themes** (Settings → Theme): Night Shift (default), Scrapbook, Loud, Index. Each sets colours,
  bundled fonts (`res/font`, SIL OFL), shapes, wording and the Media tab layout — see `ui/theme/Theme.kt`.
- **Built-in viewer**: tap any saved item; swipe between items, videos/audio play with Media3 ExoPlayer,
  photos pinch- and double-tap-zoom. "Open with…" still hands a file to another app.
- **Copied-link suggestion** (can be turned off in Settings) and a "This one's private" card that offers
  the right login and retries automatically.

## Things that will need updating over time

The sites change their web clients. When something breaks, check first:

- `TwitterExtractor.TWEET_QUERY_ID` and `GRAPHQL_FEATURES`
- `InstagramExtractor.POST_DOC_IDS`
- `TikTokParser` (the embed page's `__FRONTITY_CONNECT_STATE__` and the mobile page's `api-data`)
- the NewPipeExtractor version in `app/build.gradle.kts` (YouTube breaks most often; update it)

## Adding another site

Implement `extract.Extractor` (`canHandle` + `extract` returning a `MediaPost`) and add it to the
list in `App.extractors`. Downloads, history and UI are shared.

## Releasing an update

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease` on the machine that built the previous release. Android only accepts
   an update signed with the same key, and it is currently that machine's debug key.
3. Create a GitHub release tagged `vX.Y.Z` and attach the APK. Installed apps pick it up the next
   time they open.

## Build and test

```sh
./gradlew testDebugUnitTest            # offline parser/link tests
LIVE_TESTS=1 ./gradlew testDebugUnitTest   # also hits real X, Facebook and YouTube
./gradlew assembleRelease              # app/build/outputs/apk/release/app-release.apk
```

Building needs JDK 17+ (Android Studio's bundled JBR works) and the Android SDK; set `sdk.dir`
in `local.properties`.

## License

[GPL-3.0](LICENSE), as required by NewPipeExtractor (GPL-3.0).

- Only download content you have the right to save, and respect each site's terms.
- Release APKs are currently signed with a debug key. If you later install a build signed with
  a different key, uninstall the old one first.
