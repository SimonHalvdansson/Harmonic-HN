package com.simon.harmonichackernews.utils;

public class Changelog {
    static public String getHTML() {
        return  "<b>Version 2.2.3:</b><br>" +
                "- Material 3 Expressive design updates: New buttons, some improved colors<br>" +
                "- Some improved edge-to-edge behavior<br>" +
                "- Improved error message when no internet<br>" +
                "- Fixed crash on Android API 23 devices when using HN API, sorry about this was in a rush for the fix<br>" +
                "- Significantly improved performance of adblock, now barely makes a difference (~1ms per request)<br>" +
                "- Added setting to use official HN API always if you want to<br>" +
                "- Fixed some edge-to-edge issues<br>" +
                "- Fix rare login crash<br>" +
                "- Fixed crash when parsing weird post title related to PDF<br>" +
                "- Added new auto day/night themes (thanks flofriday!)<br>" +
                "- Added button to open HN comments in browser (thanks arkon!)<br>" +
                "- Added toggle for comment navigation animation (thanks Hao Lu!)<br>" +
                "<br>" +
                "<b>Version 2.2.2:</b><br>" +
                "- As you probably noticed, there has been an Algolia API outage recently. Thanks to Amith KK for coding a quick official HN API fallback which is automatically used now in the event of outages<br>" +
                "<br>" +
                "<b>Version 2.2.1:</b><br>" +
                "- Fixed issue when posting multiple comments / doing multiple upvotes in one session<br>" +
                "<br>" +
                "<b>Version 2.2:</b><br>" +
                "- Added (experimental) ability to cache posts for when you're offline<br>" +
                "- Fixed submit stories not working<br>" +
                "- Added ability to set a Tag on a user to remember them<br>" +
                "- Updated History to sort by click time (thanks Fernando F. H.!)<br>" +
                "- Fixed animation when opening story/comment from submissions view<br>" +
                "- Fixed some weird font rendering (thanks Carey Metcalfe!)<br>" +
                "- Fixed design issue with \"Tap to update\" button<br>" +
                "- Added button to upvote post from main view long press menu (Thanks Piotr Płaczek!)<br>" +
                "- Tweaked design of the replying to comments screen<br>" +
                "- Fixed crash when clearing data<br>" +
                "<br>" +
                "<b>Version 2.1.2:</b><br>" +
                "- Material 3 expressive loading indicators<br>" +
                "- Fixed color of comment navigation button in dark mode<br>" +
                "- Improved the speed of comment parsing by ~20%<br>" +
                "- Add option to filter users (thanks Carey Metcalfe!)<br>" +
                "<br>" +
                "<b>Version 2.1:</b><br>" +
                "- Add history view (thanks Fernando F. H.!)<br>" +
                "- Enabled foldable support for Galaxy Fold 6 (thanks Michael Swiger)<br>" +
                "- Changed Algolia search parameters (thanks Jonas Wunderlich)<br>" +
                "- Updated libraries and targeting Android 15<br>" +
                "- Fixed post title/domain filtering for Algolia API<br>" +
                "- Made links unshortened when selecting text<br>" +
                "- Added ability to copy comment text if posting fails<br>" +
                "<br>" +
                "<b>Version 2.0.3:</b><br>" +
                "- Removed Nitter integration (RIP)<br>" +
                "- Fixed long comment links opened on long press (thanks flofriday!)<br>" +
                "- Added vote direction in toast (thanks flofriday!)<br>" +
                "- Added button to view changelog from about (thanks flofriday!)<br>" +
                "- Fixed crash when clicking link in reply screen (thanks flofriday!)<br>" +
                "- Changed some button colors<br>" +
                "- Fixed 2 rare crashes<br>" +
                "- Added button to add/remove bookmark on post long press<br>" +
                "- Added option to filter domains (thanks naitgacem!)<br>" +
                "<br>" +
                "<b>Version 2.0.2:</b><br>" +
                "- New app icon!<br>" +
                "- Fixed crash when opening PDF in browser<br>" +
                "- More material 3 styling<br>" +
                "- Added option to hide comment count (thanks Loïc Carr)<br>" +
                "- Added loading indicator to main screen for initial load<br>" +
                "- Added option to swap comment tap/long press behavior<br>" +
                "- Improved code comment formatting (thanks @naitgacem)<br>" +
                "- Added option to disable startup changelog (this thing)<br>" +
                "- Fixed collapsed comment cutoff issue<br>" +
                "- Fixed Material dialog discard button placement (thanks Carsten Hagemann)<br>" +
                "<br>" +
                "<b>Version 2.0.1:</b><br>" +
                "- Fixed crash when WebView runs out of memory<br>" +
                "- Fixed crash when encountering some network errors<br>" +
                "<br>" +
                "<b>Version 2.0:</b><br>" +
                "- Material (auto) is new default theme, I recommend you try this out<br>" +
                "- Added ability to search comments<br>" +
                "- Updated splash screen to use correct dark mode color<br>" +
                "- Added GitHub repo link preview<br>" +
                "- Added Wikipedia link preview<br>" +
                "- Updated arXiv link preview<br>" +
                "- Added Algolia API error information<br>" +
                "- Experimental fix for EncryptedSharedPreferences crash<br>" +
                "- Added optional Twitter/X to Nitter redirect<br>" +
                "- Design tweaks to story page<br>" +
                "- Changed default favicon provider to Google<br>" +
                "- Removed WebView back, only device back from now on<br>" +
                "- Updated preferences to be more Material 3<br>" +
                "- Fixed issue with transparent status bar<br>" +
                "- Added experimental ability to post stories<br>" +
                "- Fixed several Android 14 predictive back issue<br>" +
                "- Added Material You comment depth indicators<br>" +
                "- Restored correct search settings (thanks Leslie Cheng)<br>" +
                "- Improved comment show/hide animation<br>" +
                "- Expanded font selection to affect more components<br>" +
                "- Prompt to update stale stories<br>" +
                "- Changed default favicon provider to Google<br>" +
                "- Fixed a possible WebView crash<br>" +
                "- Fixed 2 comments crashes<br>" +
                "<br>" +
                "<b>Version 1.11.1:</b><br>" +
                "- Fixed submissions failed to load (thanks Leslie Cheng)<br>" +
                "- New dialog-less animation when posting comments<br>" +
                "- Added arXiv abstract resolver<br>" +
                "- Fixed rare Android 14 predictive back flashing<br>" +
                "- Added touch rejection on navigation bar<br>" +
                "- Added long comment scrim in submissions<br>" +
                "<br>" +
                "<b>Version 1.11:</b><br>" +
                "- Added alternative comment sortings (thanks John Rapp Farnes)<br>" +
                "- Fixed user bio bottom padding<br>" +
                "- Added option to choose favicon provider<br>" +
                "- Improved old Android navigation scrim (thanks AppearamidGuy)<br>" +
                "- Optimized AdBlock adlist loading (thanks AppearamidGuy)<br>" +
                "- New comment opening animation when not using SwipeBack<br>" +
                "- Improved logging for login errors<br>" +
                "- Fixed scroll restoration bug<br>" +
                "- Improved collapsed comment scroll performance (thanks Yigit Boyar)<br>" +
                "- Made story text selectable (thanks AppearamidGuy)<br>" +
                "- Fixed tablet comment scroll bug<br>" +
                "- Fixed rare long click crash<br>" +
                "<br>" +
                "<b>Version 1.10:</b><br>" +
                "- Added option to select start page (thanks Thomas Dalgetty)<br>" +
                "- Comment scroll progress is now saved <br>" +
                "- Posts can be marked as read/unread by long pressing and interacting with a small menu<br>" +
                "- Added option to hide clicked posts<br>" +
                "- Updated predictive back to work on all screens<br>" +
                "- New scroll behavior when full collapsing comments<br>" +
                "- Added option to collapse all top-level comments by default<br>" +
                "- Fixed two comments related crashes<br>" +
                "<br>" +
                "<b>Version 1.9.6:</b><br>" +
                "- Increased stability of WebView (thanks AppearamidGuy and flofriday)<br>" +
                "- Added option to use device back button for WebView<br>" +
                "- Fixed a tablet bottom sheet layout bug<br>" +
                "- Minor search bar behavior changes<br>" +
                "<br>" +
                "<b>Version 1.9.5:</b><br>" +
                "- Fixed comment navigation buttons always visible<br>" +
                "<br>" +
                "<b>Version 1.9.4:</b><br>" +
                "- A white background now fades in behind the WebView after 2 seconds to better handle transparent websites<br>" +
                "- Fixed scroll issue with comment navigation buttons<br>" +
                "- Improved internal link handling<br>" +
                "- Better parsing of post titles<br>" +
                "- Animated keyboard when composing comments<br>" +
                "<br>" +
                "<b>Version 1.9.3:</b><br>" +
                "- Sharing now only shares URL<br>" +
                "- Experimental fix to WebView memory leak<br>" +
                "- Fixed crash when loading submissions<br>" +
                "- Fixed PDF viewer crash<br>" +
                "- Added partial caching of post titles<br>" +
                "- New animation for \"Tap to update\" button<br>" +
                "- Increased maximum number of loaded submissions<br>" +
                "- Fixed error while opening submissions<br>" +
                "- Reworked padding system throughout the app (thanks AppearamidGuy)<br>" +
                "- Added support for combined text/link posts (thanks Jonas Wunderlich)<br>" +
                "- Better WebView intent handling<br>" +
                "<br>" +
                "<b>Version 1.9.2:</b><br>" +
                "- Fixed white theme" +
                "<br><br>" +
                "<b>Version 1.9.1:</b><br>" +
                "- Fixed compact header padding<br>" +
                "- Fixed dark WebView, the API was significantly changed<br>" +
                "- Added a setting to disable comment animations<br>" +
                "- Added initial support for Android 14's predictive back gesture<br>" +
                "- Updated bottom sheet animation slightly<br>" +
                "- Updated dependencies<br>" +
                "<br>" +
                "<b>Version 1.9:</b><br>" +
                "In case you missed it, Harmonic is now open source! There have already been a bunch of nice pull requests (see below), feel free to check out the repo! <br><br>" +
                "- Added experimental support for foldables (thanks Travis Gayle!)<br>" +
                "- Added option for transparent status bar (thanks fireph!)<br>" +
                "- Added Android 13 dynamic theme icon (thanks Ramit Suri)<br>" +
                "- New automatic Material You theme (thanks kyleatmakrs)<br>" +
                "- Fixed a submissions crash (thanks Timothy J. Frisch!)<br>" +
                "- Layout fixes (thanks AppearamidGuy)<br>" +
                "- Reworked bottom sheet behavior, this should fix squished icons for Samsung phones among others hopefully and also fix Android 12L tablet navigation bar issues (more fixes for this will come later)<br><br>" +
                "Plus some small minor things :)";
    }
}
