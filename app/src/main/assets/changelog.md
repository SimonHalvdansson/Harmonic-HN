# Version 3.1
- Added ability to show extra HN frontpages in Settings → Stories → Behavior
- Added setting to reset all settings
- Updated icons to rounded Material symbols
- Updated dropdown header font size for smaller screens
- Fixed behavior of 'Hide clicked posts'
- Added ability to clear history from history screen

# Version 3.0
This version is the to-date most comprehensive redesign and quality of life release, consisting of more than 200 commits. If you don't like the design updated, you are able to revert all big changes in the settings but I encourage you to give them a try!

There are many small changes which are not mentioned in these notes, but this is a highlight:
- Added support for HN Favorites. You can convert all your bookmarks to favorites in Settings -> Data -> Bookmarks and also disable bookmarks from there.
- Added an Upvoted story list.
- Added support for the HN Active page.
- Added story preview images. (De)activate in Settings → Stories
- Redesigned comment details dialog, opened by long pressing a comment
- Added ability to bookmark comments
- Added new story display style: Card
- Added card display style for comments.
- Updated many parts of settings with previews
- Added support for HN CAPTCHAs
- Always enable foldable support
- Added setting to tint cards using preview images
- Added button to only show comment threads where the OP has commented
- Added new setting for graying out clicked posts
- Added collected reference link boxes for comments and text posts.
- Added [video] badges for video posts.
- Added new sharing option for link + HN comments (thanks daniel-egan!)
- Added new archive options
- Added reader mode for integrated WebView
- Added text size setting for titles
- Changed the default app font to Google Sans Flex Rounded.
- Added separate text size control for comments.
- Added a setting to hide top-level domains in story rows.
- Added a compact points setting for story rows.
- Added a "None" option for comment depth indicators.
- Added comment sorting from the comments screen.
- Improved comment search with highlighted results.
- Redesigned the welcome flow.
- Added a setting for number of stories to cache
- Added setting to automatically redirect certain domains to archive version
- Fixed some crashes and reduced memory usage when opening large threads
- Added additional guarding against memory leaks
- Fixed foldable navigation issues in 3.0.1


# Version 2.4
- Redesigned search, including adding search options
- Added support for notifications for comment replies activated from the profile page
- Improved performance of opening and interacting with comments
- Added button to go to parent comment (thanks Tsung-Han Yu!)
- Improved accessibility
- Updated UI for managing user tags
- New UI for selecting thread depth indicator colors
- Added Stack Exchange and GitLab link previews
- Added support for LaTeX in ArXiV link preview
- Improved navigation animation between settings screens
- Added ability to share Hacker News links to Harmonic
- Added support for polls via Algolia API
- Added new 'HN' theme
- Update loading indicator style for pull to refresh to Material 3 Expressive
- Made comment scroll speed adaptive
- Many other small tweaks and fixes to make the app nicer


# Version 2.3.1
- Fixed crash when viewing setting sub screen (my bad!)
- Fixed fullscreen button in integrated WebView videos (for example YouTube)
- Added fallback for parsing empty comment in submissions view
- Added loading indicator for the initial submissions screen load


# Version 2.3
- Redesign settings screen into multiple levels (thanks SquirrelWave!)
- Added a new homescreen widget for stories (thanks SquirrelWave!)
- Fixed comment search scrolling when a matched comment is inside collapsed parents
- Fixed the "Go back to comments" setting on devices before Android 14


# Version 2.2.6
- Updated behavior when met with HN CAPTCHA
- Switched to new library for loading thumbnails


# Version 2.2.5
- Added back Nitter integration. Enable it in the settings to redirect x.com to Nitter and get link previews inside Harmonic
- Added option to use pagination instead of infinite scroll (thanks Elias Floreteng!)
- Minor changes to blocking UX
- Added error message when post title is more than 80 characters (thanks Jonathan Hult!)
- Added linkification for misformatted HN post text
- Couple of assorted design tweaks
- Fixed rare crash with empty post titles


# Version 2.2.4
- Added option to navigate comments using volume buttons
- Added option for back navigation to move from WebView -> Comments instead of WebView -> Main screen (thanks Łukasz Wasylkowski!)
- Minor design fixes
- Added button in setting to help the user intercept news.ycombinator.com links
- Improved offline behavior


# Version 2.2.3
- Material 3 Expressive design updates: New buttons, some improved colors
- Some improved edge-to-edge behavior
- Improved error message when no internet
- Fixed crash on Android API 23 devices when using HN API, sorry about this was in a rush for the fix
- Significantly improved performance of adblock, now barely makes a difference (~1ms per request)
- Added setting to use official HN API always if you want to
- Fixed some edge-to-edge issues
- Fix rare login crash
- Fixed crash when parsing weird post title related to PDF
- Added new auto day/night themes (thanks flofriday!)
- Added button to open HN comments in browser (thanks arkon!)
- Added toggle for comment navigation animation (thanks Hao Lu!)


# Version 2.2.2
- As you probably noticed, there has been an Algolia API outage recently. Thanks to Amith KK for coding a quick official HN API fallback which is automatically used now in the event of outages


# Version 2.2.1
- Fixed issue when posting multiple comments / doing multiple upvotes in one session


# Version 2.2
- Added (experimental) ability to cache posts for when you're offline
- Fixed submit stories not working
- Added ability to set a Tag on a user to remember them
- Updated History to sort by click time (thanks Fernando F. H.!)
- Fixed animation when opening story/comment from submissions view
- Fixed some weird font rendering (thanks Carey Metcalfe!)
- Fixed design issue with "Tap to update" button
- Added button to upvote post from main view long press menu (Thanks Piotr Płaczek!)
- Tweaked design of the replying to comments screen
- Fixed crash when clearing data


# Version 2.1.2
- Material 3 expressive loading indicators
- Fixed color of comment navigation button in dark mode
- Improved the speed of comment parsing by ~20%
- Add option to filter users (thanks Carey Metcalfe!)


# Version 2.1
- Add history view (thanks Fernando F. H.!)
- Enabled foldable support for Galaxy Fold 6 (thanks Michael Swiger)
- Changed Algolia search parameters (thanks Jonas Wunderlich)
- Updated libraries and targeting Android 15
- Fixed post title/domain filtering for Algolia API
- Made links unshortened when selecting text
- Added ability to copy comment text if posting fails


# Version 2.0.3
- Removed Nitter integration (RIP)
- Fixed long comment links opened on long press (thanks flofriday!)
- Added vote direction in toast (thanks flofriday!)
- Added button to view changelog from about (thanks flofriday!)
- Fixed crash when clicking link in reply screen (thanks flofriday!)
- Changed some button colors
- Fixed 2 rare crashes
- Added button to add/remove bookmark on post long press
- Added option to filter domains (thanks naitgacem!)


# Version 2.0.2
- New app icon!
- Fixed crash when opening PDF in browser
- More material 3 styling
- Added option to hide comment count (thanks Loïc Carr)
- Added loading indicator to main screen for initial load
- Added option to swap comment tap/long press behavior
- Improved code comment formatting (thanks @naitgacem)
- Added option to disable startup changelog (this thing)
- Fixed collapsed comment cutoff issue
- Fixed Material dialog discard button placement (thanks Carsten Hagemann)


# Version 2.0.1
- Fixed crash when WebView runs out of memory
- Fixed crash when encountering some network errors


# Version 2.0
- Material (auto) is new default theme, I recommend you try this out
- Added ability to search comments
- Updated splash screen to use correct dark mode color
- Added GitHub repo link preview
- Added Wikipedia link preview
- Updated arXiv link preview
- Added Algolia API error information
- Experimental fix for EncryptedSharedPreferences crash
- Added optional Twitter/X to Nitter redirect
- Design tweaks to story page
- Changed default favicon provider to Google
- Removed WebView back, only device back from now on
- Updated preferences to be more Material 3
- Fixed issue with transparent status bar
- Added experimental ability to post stories
- Fixed several Android 14 predictive back issue
- Added Material You comment depth indicators
- Restored correct search settings (thanks Leslie Cheng)
- Improved comment show/hide animation
- Expanded font selection to affect more components
- Prompt to update stale stories
- Changed default favicon provider to Google
- Fixed a possible WebView crash
- Fixed 2 comments crashes


# Version 1.11.1
- Fixed submissions failed to load (thanks Leslie Cheng)
- New dialog-less animation when posting comments
- Added arXiv abstract resolver
- Fixed rare Android 14 predictive back flashing
- Added touch rejection on navigation bar
- Added long comment scrim in submissions


# Version 1.11
- Added alternative comment sortings (thanks John Rapp Farnes)
- Fixed user bio bottom padding
- Added option to choose favicon provider
- Improved old Android navigation scrim (thanks AppearamidGuy)
- Optimized AdBlock adlist loading (thanks AppearamidGuy)
- New comment opening animation when not using SwipeBack
- Improved logging for login errors
- Fixed scroll restoration bug
- Improved collapsed comment scroll performance (thanks Yigit Boyar)
- Made story text selectable (thanks AppearamidGuy)
- Fixed tablet comment scroll bug
- Fixed rare long click crash


# Version 1.10
- Added option to select start page (thanks Thomas Dalgetty)
- Comment scroll progress is now saved 
- Posts can be marked as read/unread by long pressing and interacting with a small menu
- Added option to hide clicked posts
- Updated predictive back to work on all screens
- New scroll behavior when full collapsing comments
- Added option to collapse all top-level comments by default
- Fixed two comments related crashes


# Version 1.9.6
- Increased stability of WebView (thanks AppearamidGuy and flofriday)
- Added option to use device back button for WebView
- Fixed a tablet bottom sheet layout bug
- Minor search bar behavior changes


# Version 1.9.5
- Fixed comment navigation buttons always visible


# Version 1.9.4
- A white background now fades in behind the WebView after 2 seconds to better handle transparent websites
- Fixed scroll issue with comment navigation buttons
- Improved internal link handling
- Better parsing of post titles
- Animated keyboard when composing comments


# Version 1.9.3
- Sharing now only shares URL
- Experimental fix to WebView memory leak
- Fixed crash when loading submissions
- Fixed PDF viewer crash
- Added partial caching of post titles
- New animation for "Tap to update" button
- Increased maximum number of loaded submissions
- Fixed error while opening submissions
- Reworked padding system throughout the app (thanks AppearamidGuy)
- Added support for combined text/link posts (thanks Jonas Wunderlich)
- Better WebView intent handling


# Version 1.9.2
- Fixed white theme


# Version 1.9.1
- Fixed compact header padding
- Fixed dark WebView, the API was significantly changed
- Added a setting to disable comment animations
- Added initial support for Android 14's predictive back gesture
- Updated bottom sheet animation slightly
- Updated dependencies


# Version 1.9
In case you missed it, Harmonic is now open source! There have already been a bunch of nice pull requests (see below), feel free to check out the repo! 

- Added experimental support for foldables (thanks Travis Gayle!)
- Added option for transparent status bar (thanks fireph!)
- Added Android 13 dynamic theme icon (thanks Ramit Suri)
- New automatic Material You theme (thanks kyleatmakrs)
- Fixed a submissions crash (thanks Timothy J. Frisch!)
- Layout fixes (thanks AppearamidGuy)
- Reworked bottom sheet behavior, this should fix squished icons for Samsung phones among others hopefully and also fix Android 12L tablet navigation bar issues (more fixes for this will come later)

Plus some small minor things :)
