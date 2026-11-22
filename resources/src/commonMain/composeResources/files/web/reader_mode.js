(function() {
    if (window.HarmonicReaderMode && window.HarmonicReaderMode.version === 18) {
        return;
    }

    var STATE_KEY = "__harmonicReaderModeState";
    var THEME_KEY = "__harmonicReaderModeTheme";
    var TRANSITION_KEY = "__harmonicReaderModeTransition";
    var MIN_ARTICLE_TEXT_LENGTH = 250;
    // Availability is often checked immediately before enabling. Retain only a small extraction
    // briefly, never a clone of the whole source document, and invalidate on any source mutation.
    var MAX_CACHED_ARTICLE_CHARS = 256 * 1024;
    var ARTICLE_CACHE_TTL_MS = 30000;
    var articleCache = null;
    var articleCacheObserver = null;
    var articleCacheTimer = null;
    var TRANSITION_DURATION_MS = 180;
    var TRANSITION_FALLBACK_MS = TRANSITION_DURATION_MS + 320;
    var TRANSITION_TRANSLATE_Y = "12px";
    var transitionScrollbarStyle = null;
    var DEFAULT_LIGHT_READER_THEME = {
        isLight: true,
        backgroundColor: "#fafafa",
        textColor: "#202124",
        headingColor: "#202124",
        secondaryTextColor: "#5f6368",
        linkColor: "#1a73e8",
        dividerColor: "#dadce0",
        codeBackgroundColor: "#f0f2f4"
    };
    var DEFAULT_DARK_READER_THEME = {
        isLight: false,
        backgroundColor: "#151617",
        textColor: "#e8eaed",
        headingColor: "#e8eaed",
        secondaryTextColor: "#aeb4ba",
        linkColor: "#8ab4f8",
        dividerColor: "#3d4248",
        codeBackgroundColor: "#222528"
    };
    var POSITIVE_CLASS_RE = /article|body|content|entry|hentry|main|page|post|read|story|text/i;
    var NEGATIVE_CLASS_RE = /ad-|ads|advert|banner|breadcrumb|comment|combx|contact|cookie|footer|masthead|menu|meta|modal|nav|outbrain|promo|related|scroll|share|shoutbox|sidebar|sponsor|subscribe|tag|tool|widget/i;
    var NON_ARTICLE_SELECTOR = "dialog,form,[role~='dialog' i],[role~='alertdialog' i],[aria-modal='true' i],[itemprop~='comment']";
    var REMOVE_SELECTOR = [
        "harmonic-listen",
        "script",
        "style",
        "noscript",
        "template",
        "svg",
        "canvas",
        "iframe",
        "nav",
        "header",
        "footer",
        "aside",
        "button",
        "input",
        "select",
        "textarea",
        NON_ARTICLE_SELECTOR,
        "[aria-hidden='true']",
        "[hidden]"
    ].join(",");

    function getText(element) {
        if (!element) {
            return "";
        }
        return (element.textContent || "").replace(/\s+/g, " ").trim();
    }

    // textContent joins adjacent blocks and <br> without a separator. Preserve those boundaries
    // on extraction clones, without splitting words styled with inline elements (e.g. Mc<b>Donald</b>).
    function separateBlockText(root) {
        var blocks = root.querySelectorAll("br,p,div,section,article,header,footer,address,li,dt,dd,h1,h2,h3,h4,h5,h6,tr,td,th");
        for (var i = 0; i < blocks.length; i++) {
            var block = blocks[i];
            block.parentNode.insertBefore(document.createTextNode(" "), block);
            block.parentNode.insertBefore(document.createTextNode(" "), block.nextSibling);
        }
    }

    function getAttributeSignature(element) {
        if (!element) {
            return "";
        }
        return ((element.getAttribute("class") || "") + " " + (element.getAttribute("id") || "")).trim();
    }

    function linkDensity(element) {
        var textLength = getText(element).length;
        if (textLength === 0) {
            return 0;
        }

        var linkTextLength = 0;
        var links = element.getElementsByTagName("a");
        for (var i = 0; i < links.length; i++) {
            linkTextLength += getText(links[i]).length;
        }
        return linkTextLength / textLength;
    }

    function scoreElement(element) {
        var text = getText(element);
        if (text.length < MIN_ARTICLE_TEXT_LENGTH) {
            return 0;
        }

        var score = Math.min(90, text.length / 75);
        var paragraphs = element.getElementsByTagName("p").length;
        var punctuation = text.match(/[.!?]/g);
        var tagName = element.tagName ? element.tagName.toLowerCase() : "";
        var signature = getAttributeSignature(element);

        score += Math.min(70, paragraphs * 5);
        score += Math.min(60, punctuation ? punctuation.length * 1.5 : 0);
        score -= linkDensity(element) * 90;

        if (tagName === "article") {
            score += 35;
        } else if (tagName === "main") {
            score += 25;
        }

        if (POSITIVE_CLASS_RE.test(signature)) {
            score += 25;
        }
        if (NEGATIVE_CLASS_RE.test(signature)) {
            score -= 35;
        }

        return score;
    }

    function removeNonArticleContent(root) {
        var nodes = root.querySelectorAll(NON_ARTICLE_SELECTOR);
        for (var i = nodes.length - 1; i >= 0; i--) {
            nodes[i].remove();
        }
    }

    function findArticle(root) {
        if (!root || getText(root).length < MIN_ARTICLE_TEXT_LENGTH) {
            return null;
        }

        var preferred = root.querySelector("article, main, [role='main']");
        if (preferred && getText(preferred).length >= MIN_ARTICLE_TEXT_LENGTH && linkDensity(preferred) < 0.45) {
            return preferred;
        }

        var selectors = "article, main, section, div, td, [role='main']";
        var candidates = [root];
        var nodes = root.querySelectorAll(selectors);
        for (var i = 0; i < nodes.length; i++) {
            candidates.push(nodes[i]);
        }

        var best = null;
        var bestScore = 0;
        for (var j = 0; j < candidates.length; j++) {
            var candidate = candidates[j];
            var score = scoreElement(candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        return bestScore >= 20 ? best : null;
    }

    function parseReadabilityDocument() {
        if (typeof Readability !== "function" || !document.body) {
            return null;
        }

        try {
            var documentClone = document.cloneNode(true);
            // Dialogs, forms and secondary comments can outweigh a short article. Exclude
            // them before scoring, on the clone only, preserving the source's live state.
            removeNonArticleContent(documentClone);
            var bylines = documentClone.querySelectorAll(
                "[rel~='author'],[itemprop~='author'],[class*='byline' i],[id*='byline' i],[class*='author' i],[id*='author' i]"
            );
            for (var bylineIndex = 0; bylineIndex < bylines.length; bylineIndex++) {
                separateBlockText(bylines[bylineIndex]);
            }
            // Readability absolutizes URLs using <base>; keep article-local anchors local first.
            var fragmentLinks = documentClone.querySelectorAll("a[href^='#']");
            for (var i = 0; i < fragmentLinks.length; i++) {
                fragmentLinks[i].setAttribute("href", new URL(fragmentLinks[i].getAttribute("href"), location.href).href);
            }
            var parsed = new Readability(documentClone, {
                charThreshold: MIN_ARTICLE_TEXT_LENGTH,
                keepClasses: false
            }).parse();
            if (!parsed || !parsed.content || !parsed.textContent || parsed.textContent.length < MIN_ARTICLE_TEXT_LENGTH) {
                return null;
            }
            return parsed;
        } catch (e) {
            return null;
        }
    }

    function parseWithReadability() {
        var parsed = parseReadabilityDocument();
        if (!parsed) {
            return null;
        }

        try {
            var container = document.createElement("div");
            container.innerHTML = parsed.content;
            cleanArticle(container);
            if (getText(container).length < MIN_ARTICLE_TEXT_LENGTH) {
                return null;
            }

            return {
                root: container,
                title: parsed.title || "",
                byline: (parsed.byline || "").replace(/\s+/g, " ").trim(),
                siteName: parsed.siteName || "",
                publishedTime: parsed.publishedTime || ""
            };
        } catch (e) {
            return null;
        }
    }

    function extractArticle() {
        var parsedArticle = parseWithReadability();
        if (parsedArticle) {
            removeDuplicateTitle(parsedArticle.root, parsedArticle.title);
            return parsedArticle;
        }

        var source = document.body.cloneNode(true);
        removeNonArticleContent(source);
        var article = findArticle(source);
        if (!article) {
            return null;
        }

        var clone = article.cloneNode(true);
        cleanArticle(clone);

        var title = findTitle(article);
        removeDuplicateTitle(clone, title);

        if (getText(clone).length < MIN_ARTICLE_TEXT_LENGTH) {
            return null;
        }

        return {
            root: clone,
            title: title,
            byline: findByline(article),
            siteName: "",
            publishedTime: ""
        };
    }

    function clearArticleCache() {
        articleCache = null;
        if (articleCacheObserver) articleCacheObserver.disconnect();
        articleCacheObserver = null;
        if (articleCacheTimer !== null) clearTimeout(articleCacheTimer);
        articleCacheTimer = null;
    }

    function preparedArticle(consume) {
        // takeRecords covers mutations made earlier in this same JavaScript task, before the
        // observer callback has had a chance to run. URL changes can happen without DOM changes.
        if (articleCache && (Date.now() >= articleCache.expiresAt ||
                articleCache.url !== location.href || articleCache.baseURI !== document.baseURI ||
                articleCache.hasReadability !== (typeof Readability === "function") ||
                articleCacheObserver.takeRecords().length > 0)) {
            clearArticleCache();
        }
        if (articleCache) {
            var retained = articleCache.article;
            if (consume) clearArticleCache();
            return retained;
        }
        var extracted = extractArticle();
        if (!consume && typeof MutationObserver === "function" &&
                (!extracted || extracted.root.outerHTML.length <= MAX_CACHED_ARTICLE_CHARS)) {
            articleCache = {
                article: extracted,
                url: location.href,
                baseURI: document.baseURI,
                hasReadability: typeof Readability === "function",
                expiresAt: Date.now() + ARTICLE_CACHE_TTL_MS
            };
            articleCacheObserver = new MutationObserver(clearArticleCache);
            articleCacheObserver.observe(document, {
                subtree: true, childList: true, characterData: true, attributes: true
            });
            articleCacheTimer = setTimeout(clearArticleCache, ARTICLE_CACHE_TTL_MS);
        }
        return extracted;
    }

    function absolutizeUrl(value) {
        if (!value) {
            return value;
        }
        try {
            return new URL(value, document.baseURI).href;
        } catch (e) {
            return value;
        }
    }

    function absolutizeSrcset(value) {
        if (!value) {
            return value;
        }
        return String(value).split(",").map(function(part) {
            var trimmed = part.trim();
            var match = /^(\S+)(.*)$/.exec(trimmed);
            if (!match) {
                return trimmed;
            }
            return absolutizeUrl(match[1]) + match[2];
        }).join(", ");
    }

    function cleanAttributes(element) {
        var allowed = {
            "a": ["href", "title", "name"],
            "img": ["src", "srcset", "sizes", "alt", "title", "width", "height"],
            "picture": [],
            "video": ["src", "poster", "controls"],
            "source": ["src", "srcset", "sizes", "media", "type"],
            "td": ["colspan", "rowspan"],
            "th": ["colspan", "rowspan"],
            "ol": ["start"],
            "code": ["class"],
            "pre": ["class"]
        };
        var tagName = element.tagName ? element.tagName.toLowerCase() : "";
        var keep = (allowed[tagName] || []).concat(["id", "lang", "dir"]);
        for (var i = element.attributes.length - 1; i >= 0; i--) {
            var name = element.attributes[i].name;
            if (keep.indexOf(name) === -1) {
                element.removeAttribute(name);
            }
        }
    }

    function pruneByClassName(root) {
        var nodes = root.querySelectorAll("*");
        for (var i = nodes.length - 1; i >= 0; i--) {
            var node = nodes[i];
            var signature = getAttributeSignature(node);
            if (NEGATIVE_CLASS_RE.test(signature) && getText(node).length < 600) {
                node.parentNode.removeChild(node);
            }
        }
    }

    function cleanArticle(root) {
        var removals = root.querySelectorAll(REMOVE_SELECTOR);
        for (var i = removals.length - 1; i >= 0; i--) {
            removals[i].parentNode.removeChild(removals[i]);
        }

        pruneByClassName(root);

        var nodes = root.querySelectorAll("*");
        for (var j = 0; j < nodes.length; j++) {
            var node = nodes[j];
            var tagName = node.tagName ? node.tagName.toLowerCase() : "";

            if (tagName === "img") {
                var src = node.getAttribute("src") || node.getAttribute("data-src") || node.getAttribute("data-lazy-src");
                var srcset = node.getAttribute("srcset") || node.getAttribute("data-srcset");
                if (src) {
                    node.setAttribute("src", absolutizeUrl(src));
                }
                if (srcset) {
                    node.setAttribute("srcset", absolutizeSrcset(srcset));
                }
            } else if (tagName === "a") {
                var href = node.getAttribute("href");
                if (href) {
                    // Fragment links refer to this article even when the source uses a <base> URL.
                    node.setAttribute("href", href.charAt(0) === "#" ? new URL(href, location.href).href : absolutizeUrl(href));
                }
            } else if (tagName === "video") {
                var videoSrc = node.getAttribute("src");
                var poster = node.getAttribute("poster");
                if (videoSrc) {
                    node.setAttribute("src", absolutizeUrl(videoSrc));
                }
                if (poster) {
                    node.setAttribute("poster", absolutizeUrl(poster));
                }
                node.setAttribute("controls", "controls");
            } else if (tagName === "source") {
                var sourceSrc = node.getAttribute("src");
                var sourceSrcset = node.getAttribute("srcset") || node.getAttribute("data-srcset");
                if (sourceSrc) {
                    node.setAttribute("src", absolutizeUrl(sourceSrc));
                }
                if (sourceSrcset) {
                    node.setAttribute("srcset", absolutizeSrcset(sourceSrcset));
                }
            }

            cleanAttributes(node);
        }

        removeEmptyElements(root);
    }

    function removeEmptyElements(root) {
        var keepEmpty = {
            "br": true,
            "hr": true,
            "img": true,
            "video": true,
            "source": true,
            "iframe": true
        };
        var nodes = root.querySelectorAll("*");
        for (var i = nodes.length - 1; i >= 0; i--) {
            var node = nodes[i];
            var tagName = node.tagName ? node.tagName.toLowerCase() : "";
            if (!keepEmpty[tagName] && !node.id && !node.getAttribute("name") &&
                    getText(node).length === 0 && !node.querySelector("img, video, source, iframe")) {
                node.parentNode.removeChild(node);
            }
        }
    }

    function escapeHtml(value) {
        return String(value || "")
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
    }

    function normalizeTitle(value) {
        return String(value || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
    }

    function getMetaContent(selector) {
        var meta = document.querySelector(selector);
        return meta ? (meta.getAttribute("content") || "").trim() : "";
    }

    function findTitle(article) {
        var localTitle = article.querySelector("h1");
        var title = localTitle ? getText(localTitle) : "";
        if (!title) {
            title = getMetaContent("meta[property='og:title']");
        }
        if (!title) {
            title = getMetaContent("meta[name='twitter:title']");
        }
        if (!title) {
            title = document.title || "";
        }
        return title.replace(/\s+[|-]\s+[^|-]+$/, "").trim();
    }

    function removeDuplicateTitle(root, title) {
        var titleText = normalizeTitle(title);
        if (!titleText) {
            return;
        }
        var headings = root.querySelectorAll("h1, h2");
        for (var i = 0; i < headings.length; i++) {
            if (normalizeTitle(getText(headings[i])) === titleText) {
                if (headings[i].id) {
                    var target = document.createElement("span");
                    target.id = headings[i].id;
                    headings[i].parentNode.insertBefore(target, headings[i]);
                }
                headings[i].parentNode.removeChild(headings[i]);
                return;
            }
        }
    }

    function findByline(article) {
        var selectors = [
            "[rel='author']",
            "[itemprop='author']",
            ".byline",
            ".author",
            "[class*='byline']",
            "[class*='author']"
        ];
        for (var i = 0; i < selectors.length; i++) {
            var node = article.querySelector(selectors[i]);
            var clone = node && node.cloneNode(true);
            if (clone) separateBlockText(clone);
            var text = getText(clone);
            if (text && text.length <= 160) {
                return text;
            }
        }
        return getMetaContent("meta[name='author']");
    }

    function getAttributes(element) {
        var attrs = {};
        if (!element) {
            return attrs;
        }
        for (var i = 0; i < element.attributes.length; i++) {
            attrs[element.attributes[i].name] = element.attributes[i].value;
        }
        return attrs;
    }

    function restoreAttributes(element, attrs) {
        if (!element) {
            return;
        }
        for (var i = element.attributes.length - 1; i >= 0; i--) {
            element.removeAttribute(element.attributes[i].name);
        }
        for (var name in attrs) {
            if (Object.prototype.hasOwnProperty.call(attrs, name)) {
                element.setAttribute(name, attrs[name]);
            }
        }
    }

    function setReaderViewport() {
        var nodes = document.querySelectorAll("meta[name='viewport' i]");
        var originals = [];
        for (var i = 0; i < nodes.length; i++) {
            originals.push({node: nodes[i], content: nodes[i].getAttribute("content")});
            nodes[i].setAttribute("content", "width=device-width, initial-scale=1");
        }
        var added = null;
        if (!nodes.length) {
            added = document.createElement("meta");
            added.name = "viewport";
            added.content = "width=device-width, initial-scale=1";
            document.head.appendChild(added);
        }
        return {originals: originals, added: added};
    }

    function restoreViewport(viewport) {
        if (!viewport) return;
        // Chromium retains the last viewport when a tag/content attribute is removed.
        // An empty content value first restores the browser's default viewport policy.
        if (viewport.added) {
            viewport.added.content = "";
            viewport.added.remove();
        }
        for (var i = 0; i < viewport.originals.length; i++) {
            var original = viewport.originals[i];
            original.node.setAttribute("content", original.content || "");
            if (original.content === null) original.node.removeAttribute("content");
        }
    }

    function sanitizeColor(value, fallback) {
        return /^#[0-9a-f]{6}$/i.test(String(value || "")) ? value : fallback;
    }

    function sanitizeCssText(value, fallback) {
        value = String(value || "");
        return /[<>]/.test(value) ? fallback : value;
    }

    function sanitizeFontSize(value, fallback) {
        var parsed = parseInt(value, 10);
        if (!isFinite(parsed)) {
            return fallback;
        }
        return Math.max(13, Math.min(24, parsed));
    }

    function getReaderTheme() {
        var configured = window[THEME_KEY] || {};
        var fallback = configured.isLight === false ? DEFAULT_DARK_READER_THEME : DEFAULT_LIGHT_READER_THEME;
        return {
            isLight: configured.isLight !== false,
            backgroundColor: sanitizeColor(configured.backgroundColor, fallback.backgroundColor),
            textColor: sanitizeColor(configured.textColor, fallback.textColor),
            headingColor: sanitizeColor(configured.headingColor, fallback.headingColor),
            secondaryTextColor: sanitizeColor(configured.secondaryTextColor, fallback.secondaryTextColor),
            linkColor: sanitizeColor(configured.linkColor, fallback.linkColor),
            dividerColor: sanitizeColor(configured.dividerColor, fallback.dividerColor),
            codeBackgroundColor: sanitizeColor(configured.codeBackgroundColor, fallback.codeBackgroundColor),
            fontFaceCss: sanitizeCssText(configured.fontFaceCss, ""),
            fontFamily: sanitizeCssText(configured.fontFamily, "Georgia,'Times New Roman',serif"),
            headingFontFamily: sanitizeCssText(configured.headingFontFamily, "Georgia,'Times New Roman',serif"),
            fontSizePx: sanitizeFontSize(configured.fontSizePx, 17),
            lineHeight: [1.4, 1.68, 2].indexOf(configured.lineHeight) >= 0 ? configured.lineHeight : 1.68
        };
    }

    var installedFontToken = null;

    function setTheme(theme, fontToken) {
        window[THEME_KEY] = theme || {};
        installedFontToken = fontToken || null;
        if (window[STATE_KEY] && window[STATE_KEY].enabled) {
            var oldStyle = document.getElementById("harmonic-reader-style");
            if (oldStyle) {
                var styles = document.createElement("div");
                styles.innerHTML = readerStyles();
                oldStyle.replaceWith(styles.firstElementChild);
                correctReaderContrast(document.getElementById("harmonic-reader-mode"));
            }
        }
        return "theme_set";
    }

    // A compact native update omits only the already-installed font bytes. Normal setTheme
    // remains a complete replacement, including when switching back to the device font.
    function updateTheme(theme, fontToken) {
        if (!fontToken || fontToken !== installedFontToken || !window[THEME_KEY]) return false;
        theme.fontFaceCss = window[THEME_KEY].fontFaceCss;
        setTheme(theme, fontToken);
        return true;
    }

    function readerStyles() {
        var theme = getReaderTheme();
        var bodyFontSize = theme.fontSizePx;
        var titleFontSize = Math.round(bodyFontSize * 1.78);
        var bylineFontSize = Math.max(13, bodyFontSize - 3);
        var captionFontSize = Math.max(12, bodyFontSize - 4);
        var codeFontSize = Math.max(12, bodyFontSize - 4);
        return [
            "<style id=\"harmonic-reader-style\">",
            theme.fontFaceCss,
            "html[data-harmonic-reader='true']{width:100%!important;min-width:0!important;overflow-x:hidden!important;}",
            "body[data-harmonic-reader='true']{display:block!important;width:100%!important;min-width:0!important;max-width:100%!important;overflow-x:hidden!important;margin:0!important;background:" + theme.backgroundColor + "!important;color:" + theme.textColor + "!important;font-family:" + theme.fontFamily + "!important;line-height:" + theme.lineHeight + "!important;font-size:" + bodyFontSize + "px!important;-webkit-text-size-adjust:100%!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-mode{display:block!important;box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:760px!important;margin:0 auto!important;padding:32px 20px 96px!important;background:" + theme.backgroundColor + "!important;color:" + theme.textColor + "!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-mode *{box-sizing:border-box!important;min-width:0!important;max-width:100%!important;}",
            "#harmonic-reader-kicker{font:600 13px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif!important;letter-spacing:.04em!important;text-transform:uppercase!important;color:" + theme.linkColor + "!important;margin-bottom:14px!important;}",
            "#harmonic-reader-mode h1{overflow-wrap:anywhere!important;font:700 " + titleFontSize + "px/1.15 " + theme.headingFontFamily + "!important;margin:0 0 12px!important;color:" + theme.headingColor + "!important;}",
            "#harmonic-reader-byline{font:500 " + bylineFontSize + "px/1.5 " + theme.fontFamily + "!important;color:" + theme.secondaryTextColor + "!important;margin:0 0 28px!important;}",
            "#harmonic-reader-byline[data-harmonic-listen-header]{margin-bottom:0!important;}",
            "#harmonic-reader-article,#harmonic-reader-article section,#harmonic-reader-article div,#harmonic-reader-article figure{overflow-wrap:anywhere!important;background:transparent!important;color:" + theme.textColor + "!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-article *{background:transparent!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-article p,#harmonic-reader-article li{font-size:" + bodyFontSize + "px!important;line-height:" + theme.lineHeight + "!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article span,#harmonic-reader-article strong,#harmonic-reader-article em,#harmonic-reader-article small{color:inherit!important;}",
            "#harmonic-reader-article p{margin:0 0 1.05em!important;}",
            "#harmonic-reader-article ul,#harmonic-reader-article ol{display:block!important;margin:0 0 1.05em!important;padding-left:1.4em!important;list-style-position:outside!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article ul{list-style-type:disc!important;}",
            "#harmonic-reader-article ol{list-style-type:decimal!important;}",
            "#harmonic-reader-article li{display:list-item!important;margin:.35em 0!important;padding-left:.15em!important;}",
            "#harmonic-reader-article li::marker{color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article li p{margin:.35em 0!important;}",
            "#harmonic-reader-article li>ul,#harmonic-reader-article li>ol{margin:.45em 0 .45em!important;padding-left:1.25em!important;}",
            "#harmonic-reader-article li>ul{list-style-type:circle!important;}",
            "#harmonic-reader-article li>ol{list-style-type:lower-alpha!important;}",
            "#harmonic-reader-article h2,#harmonic-reader-article h3,#harmonic-reader-article h4,#harmonic-reader-article h5,#harmonic-reader-article h6{font-family:" + theme.headingFontFamily + "!important;line-height:1.25!important;margin:1.65em 0 .6em!important;background:transparent!important;color:" + theme.headingColor + "!important;}",
            "#harmonic-reader-article a{color:" + theme.linkColor + "!important;text-decoration:underline!important;text-decoration-thickness:1px!important;text-underline-offset:3px!important;}",
            "#harmonic-reader-article img,#harmonic-reader-article video{display:block;height:auto!important;margin:1.2em auto!important;border-radius:4px!important;}",
            "#harmonic-reader-article figure{margin:1.4em 0!important;}",
            "#harmonic-reader-article figcaption{font:" + captionFontSize + "px/1.45 " + theme.fontFamily + "!important;color:" + theme.secondaryTextColor + "!important;margin-top:.5em!important;}",
            "#harmonic-reader-article blockquote{border-left:3px solid " + theme.linkColor + "!important;margin:1.3em 0!important;padding:.1em 0 .1em 1em!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article pre{white-space:pre!important;overflow-wrap:normal!important;overflow-x:auto!important;background:" + theme.codeBackgroundColor + "!important;color:" + theme.textColor + "!important;border-radius:4px!important;padding:14px!important;font-size:" + codeFontSize + "px!important;line-height:1.5!important;}",
            "#harmonic-reader-article code,#harmonic-reader-article pre *{font-family:ui-monospace,SFMono-Regular,Consolas,'Liberation Mono',monospace!important;font-size:.9em!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article table{overflow-wrap:normal!important;border-collapse:collapse!important;display:block!important;overflow-x:auto!important;margin:1.2em 0!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article thead,#harmonic-reader-article tbody,#harmonic-reader-article tfoot,#harmonic-reader-article tr{background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article th,#harmonic-reader-article td{border:1px solid " + theme.dividerColor + "!important;padding:8px!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "</style>"
        ].join("");
    }

    function colorLuminance(color) {
        var linear = color.slice(0, 3).map(function(channel) {
            channel /= 255;
            return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        });
        return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
    }

    function colorContrast(first, second) {
        var a = colorLuminance(first);
        var b = colorLuminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    function compositeColor(foreground, background) {
        return foreground.slice(0, 3).map(function(channel, index) {
            return channel * foreground[3] + background[index] * (1 - foreground[3]);
        });
    }

    function readableColor(color, background, normalText) {
        var minimumContrast = 4.5;
        if (colorContrast(color, background) >= minimumContrast) return null;
        var target = normalText;
        // A custom theme's normal text can itself be too faint on a code block's background.
        if (colorContrast(target, background) < minimumContrast) {
            target = colorContrast([0, 0, 0], background) > colorContrast([255, 255, 255], background)
                ? [0, 0, 0] : [255, 255, 255];
        }
        var low = 0;
        var high = 1;
        var result = target;
        for (var i = 0; i < 16; i++) {
            var amount = (low + high) / 2;
            var candidate = color.map(function(channel, index) {
                return Math.round(channel + (target[index] - channel) * amount);
            });
            if (colorContrast(candidate, background) >= minimumContrast) {
                high = amount;
                result = candidate;
            } else {
                low = amount;
            }
        }
        return "rgb(" + result.join(",") + ")";
    }

    var contrastCorrections = [];

    function correctReaderContrast(root) {
        if (!root) return;
        // Re-evaluate source colors after a theme change, rather than adjusting our previous
        // correction. Restore only reader clones; the retained original document is untouched.
        for (var previous of contrastCorrections) {
            if (previous.color) previous.element.style.setProperty("color", previous.color, previous.priority);
            else previous.element.style.removeProperty("color");
        }
        contrastCorrections = [];
        var theme = getReaderTheme();
        // Let the browser resolve CSS colors, including alpha and wide-gamut syntax, into sRGB.
        var context = document.createElement("canvas").getContext("2d", {willReadFrequently: true});
        if (!context) return;
        var colors = Object.create(null);
        function parseColor(cssColor) {
            if (!colors[cssColor]) {
                context.clearRect(0, 0, 1, 1);
                context.fillStyle = cssColor;
                context.fillRect(0, 0, 1, 1);
                var pixel = context.getImageData(0, 0, 1, 1).data;
                colors[cssColor] = [pixel[0], pixel[1], pixel[2], pixel[3] / 255];
            }
            return colors[cssColor];
        }
        var normalText = parseColor(theme.textColor).slice(0, 3);
        var changes = [];
        function visit(element, parentBackground) {
            var style = window.getComputedStyle(element);
            var background = compositeColor(parseColor(style.backgroundColor), parentBackground);
            for (var child = element.firstChild; child; child = child.nextSibling) {
                if (child.nodeType === 3 && /\S/.test(child.nodeValue)) {
                    var foreground = compositeColor(parseColor(style.color), background);
                    var corrected = readableColor(foreground, background, normalText);
                    if (corrected) changes.push({element: element, color: corrected});
                    break;
                }
            }
            for (var i = 0; i < element.children.length; i++) visit(element.children[i], background);
        }
        visit(root, parseColor(theme.backgroundColor));
        // Read all computed styles before writing, preserving inherited source colors and avoiding
        // a style recalculation for every text node. Original page nodes are never modified.
        for (var i = 0; i < changes.length; i++) {
            contrastCorrections.push({
                element: changes[i].element,
                color: changes[i].element.style.getPropertyValue("color"),
                priority: changes[i].element.style.getPropertyPriority("color")
            });
            changes[i].element.style.setProperty("color", changes[i].color, "important");
        }
    }

    function nextTransitionId() {
        window[TRANSITION_KEY] = (window[TRANSITION_KEY] || 0) + 1;
        hideTransitionScrollbar(window[TRANSITION_KEY]);
        return window[TRANSITION_KEY];
    }

    function restoreTransitionScrollbar(transitionId) {
        if (isCurrentTransition(transitionId) && transitionScrollbarStyle) {
            transitionScrollbarStyle.remove();
            transitionScrollbarStyle = null;
        }
    }

    function hideTransitionScrollbar(transitionId) {
        if (transitionScrollbarStyle) transitionScrollbarStyle.remove();
        var style = document.createElement("style");
        style.id = "harmonic-reader-transition-scrollbar";
        // A page's custom scrollbar can briefly become viewport-sized while its body is
        // replaced. Suppress its paint, not scrolling or overflow. Retain a classic gutter's
        // width so hiding it cannot rewrap the article or move the saved scroll position.
        var gutter = Math.max(0, window.innerWidth - document.documentElement.clientWidth);
        if (gutter > 0) {
            style.textContent = "html,body{scrollbar-color:auto!important}" +
                "html::-webkit-scrollbar,body::-webkit-scrollbar{width:" + gutter + "px!important}";
        } else {
            style.textContent = "html,body{scrollbar-color:transparent transparent!important}" +
                "html::-webkit-scrollbar,body::-webkit-scrollbar{width:0!important;height:0!important}";
        }
        var parts = ["", "-thumb", "-track", "-track-piece", "-button", "-corner"];
        var selectors = [];
        for (var i = 0; i < parts.length; i++) {
            selectors.push("html::-webkit-scrollbar" + parts[i], "body::-webkit-scrollbar" + parts[i]);
        }
        style.textContent += selectors.join(",") +
            "{background:transparent!important;border-color:transparent!important;box-shadow:none!important}";
        (document.head || document.documentElement).appendChild(style);
        transitionScrollbarStyle = style;
        // Also release on an interrupted/failed transition or a throttled background page.
        setTimeout(function() { restoreTransitionScrollbar(transitionId); }, 2 * TRANSITION_FALLBACK_MS + 100);
    }

    function isCurrentTransition(transitionId) {
        return window[TRANSITION_KEY] === transitionId;
    }

    function captureTransitionStyles(element) {
        return {
            opacity: element.style.getPropertyValue("opacity"),
            opacityPriority: element.style.getPropertyPriority("opacity"),
            transform: element.style.getPropertyValue("transform"),
            transformPriority: element.style.getPropertyPriority("transform"),
            transition: element.style.getPropertyValue("transition"),
            transitionPriority: element.style.getPropertyPriority("transition"),
            willChange: element.style.getPropertyValue("will-change"),
            willChangePriority: element.style.getPropertyPriority("will-change")
        };
    }

    function restoreTransitionStyles(element, styles) {
        element.style.setProperty("opacity", styles.opacity, styles.opacityPriority);
        element.style.setProperty("transform", styles.transform, styles.transformPriority);
        element.style.setProperty("transition", styles.transition, styles.transitionPriority);
        element.style.setProperty("will-change", styles.willChange, styles.willChangePriority);
    }

    function setTransitionStyle(element, opacity, translateY) {
        element.style.setProperty("opacity", opacity, "important");
        element.style.setProperty("transform", "translateY(" + translateY + ")", "important");
        element.style.setProperty("transition", "opacity " + TRANSITION_DURATION_MS + "ms ease, transform " + TRANSITION_DURATION_MS + "ms ease", "important");
        element.style.setProperty("will-change", "opacity, transform", "important");
    }

    function animateOut(element, after) {
        var transitionId = nextTransitionId();
        var styles = captureTransitionStyles(element);
        var completed = false;
        function complete() {
            if (completed) {
                return;
            }
            completed = true;
            if (!isCurrentTransition(transitionId)) {
                restoreTransitionStyles(element, styles);
                return;
            }
            after(transitionId);
        }
        setTransitionStyle(element, "1", "0");
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (!isCurrentTransition(transitionId)) {
                    restoreTransitionStyles(element, styles);
                    return;
                }
                setTransitionStyle(element, "0", TRANSITION_TRANSLATE_Y);
                setTimeout(complete, TRANSITION_DURATION_MS);
            });
        });
        setTimeout(complete, TRANSITION_FALLBACK_MS);
        return transitionId;
    }

    function animateIn(element, transitionId, after) {
        var styles = captureTransitionStyles(element);
        var completed = false;
        function complete() {
            if (completed) {
                return;
            }
            completed = true;
            if (!isCurrentTransition(transitionId)) {
                restoreTransitionStyles(element, styles);
                return;
            }
            restoreTransitionStyles(element, styles);
            restoreTransitionScrollbar(transitionId);
            if (after) {
                after();
            }
        }
        setTransitionStyle(element, "0", TRANSITION_TRANSLATE_Y);
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (!isCurrentTransition(transitionId)) {
                    restoreTransitionStyles(element, styles);
                    return;
                }
                setTransitionStyle(element, "1", "0");
                setTimeout(complete, TRANSITION_DURATION_MS);
            });
        });
        setTimeout(complete, TRANSITION_FALLBACK_MS);
    }

    function enable() {
        try {
            if (window[STATE_KEY] && window[STATE_KEY].enabled) {
                return "enabled";
            }
            if (!document.body) {
                return "unavailable";
            }

            var extractedArticle = preparedArticle(true);
            if (!extractedArticle) {
                return "no_article";
            }

            var title = extractedArticle.title || findTitle(extractedArticle.root);
            var site = (extractedArticle.siteName || location.hostname || "").replace(/^www\./, "");
            var meta = extractedArticle.byline ? extractedArticle.byline : site;
            var originalTitle = document.title;

            window[STATE_KEY] = {
                enabled: true,
                bodyNodes: null,
                viewport: null,
                bodyAttrs: getAttributes(document.body),
                htmlAttrs: getAttributes(document.documentElement),
                title: originalTitle,
                scrollX: window.pageXOffset || 0,
                scrollY: window.pageYOffset || 0
            };

            var html = [
                readerStyles(),
                "<main id=\"harmonic-reader-mode\">",
                site ? "<div id=\"harmonic-reader-kicker\">" + escapeHtml(site) + "</div>" : "",
                title ? "<h1>" + escapeHtml(title) + "</h1>" : "",
                meta ? "<div id=\"harmonic-reader-byline\">" + escapeHtml(meta) + "</div>" : "",
                "<article id=\"harmonic-reader-article\">",
                extractedArticle.root.id ? "<span id=\"" + escapeHtml(extractedArticle.root.id) + "\"></span>" : "",
                extractedArticle.root.innerHTML,
                "</article>",
                "</main>"
            ].join("");

            animateOut(document.body, function(transitionId) {
                var state = window[STATE_KEY];
                if (!state || !state.enabled || !isCurrentTransition(transitionId)) {
                    return;
                }
                // Keep the live nodes (including listeners and form state) for restoration.
                // Detach only when the transition commits so a cancelled open leaves them intact.
                state.bodyNodes = document.createDocumentFragment();
                while (document.body.firstChild) {
                    state.bodyNodes.appendChild(document.body.firstChild);
                }
                document.body.innerHTML = html;
                document.documentElement.setAttribute("data-harmonic-reader", "true");
                restoreAttributes(document.body, {"data-harmonic-reader": "true"});
                // Desktop-width or absent viewport metadata otherwise scales down the entire reader.
                state.viewport = setReaderViewport();
                correctReaderContrast(document.getElementById("harmonic-reader-mode"));
                document.title = title || originalTitle;
                window.scrollTo(0, 0);
                animateIn(document.getElementById("harmonic-reader-mode") || document.body, transitionId);
            });
            return "enabled";
        } catch (e) {
            return "error";
        }
    }

    function disable() {
        try {
            var state = window[STATE_KEY];
            if (!state || !state.enabled || !document.body) {
                return "disabled";
            }
            var readerElement = document.getElementById("harmonic-reader-mode") || document.body;
            animateOut(readerElement, function(transitionId) {
                var currentState = window[STATE_KEY];
                if (!currentState || !currentState.enabled || !isCurrentTransition(transitionId)) {
                    return;
                }
                if (currentState.bodyNodes) {
                    while (document.body.firstChild) {
                        document.body.removeChild(document.body.firstChild);
                    }
                    document.body.appendChild(currentState.bodyNodes);
                }
                restoreAttributes(document.body, currentState.bodyAttrs || {});
                restoreAttributes(document.documentElement, currentState.htmlAttrs || {});
                restoreViewport(currentState.viewport);
                document.title = currentState.title || document.title;
                var scrollX = currentState.scrollX || 0;
                var scrollY = currentState.scrollY || 0;
                window[STATE_KEY] = null;
                contrastCorrections = [];
                setTimeout(function() {
                    window.scrollTo(scrollX, scrollY);
                }, 0);
                animateIn(document.body, transitionId);
            });
            return "disabled";
        } catch (e) {
            return "error";
        }
    }

    function isAvailable() {
        try {
            return document.body && preparedArticle(false) ? "available" : "unavailable";
        } catch (e) {
            return "unavailable";
        }
    }

    // Host-driven controls: speech survives document changes and app backgrounding.
    var speechCommand = null;
    var speechTheme = null;
    // Material Symbols Rounded, fill 0, weight 400, optical size 24 (official Android vectors).
    var speechIcons = {"listen":"<svg viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M760,479Q760,396 716,327.5Q672,259 598,225Q583,218 576,203.5Q569,189 574,174Q580,158 595.5,151Q611,144 627,151Q724,194 782,282.5Q840,371 840,479Q840,587 782,675.5Q724,764 627,807Q611,814 595.5,807Q580,800 574,784Q569,769 576,754.5Q583,740 598,733Q672,699 716,630.5Q760,562 760,479ZM280,600L160,600Q143,600 131.5,588.5Q120,577 120,560L120,400Q120,383 131.5,371.5Q143,360 160,360L280,360L412,228Q431,209 455.5,219.5Q480,230 480,257L480,703Q480,730 455.5,740.5Q431,751 412,732L280,600ZM660,480Q660,522 641,559.5Q622,597 591,621Q581,627 570.5,621.5Q560,616 560,604L560,354Q560,342 570.5,336.5Q581,331 591,337Q622,362 641,400Q660,438 660,480ZM400,354L314,440L200,440L200,520L314,520L400,606L400,354ZM300,480L300,480L300,480L300,480L300,480L300,480L300,480Z\"/></svg>","play":"<svg viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M320,687L320,273Q320,256 332,244.5Q344,233 360,233Q365,233 370.5,234.5Q376,236 381,239L707,446Q716,452 720.5,461Q725,470 725,480Q725,490 720.5,499Q716,508 707,514L381,721Q376,724 370.5,725.5Q365,727 360,727Q344,727 332,715.5Q320,704 320,687ZM400,480L400,480L400,480L400,480ZM400,614L610,480L400,346L400,614Z\"/></svg>","pause":"<svg viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M600,760Q567,760 543.5,736.5Q520,713 520,680L520,280Q520,247 543.5,223.5Q567,200 600,200L680,200Q713,200 736.5,223.5Q760,247 760,280L760,680Q760,713 736.5,736.5Q713,760 680,760L600,760ZM280,760Q247,760 223.5,736.5Q200,713 200,680L200,280Q200,247 223.5,223.5Q247,200 280,200L360,200Q393,200 416.5,223.5Q440,247 440,280L440,680Q440,713 416.5,736.5Q393,760 360,760L280,760ZM600,680L680,680L680,280L600,280L600,680ZM280,680L360,680L360,280L280,280L280,680ZM280,280L280,280L280,680L280,680L280,280ZM600,280L600,280L600,680L600,680L600,280Z\"/></svg>","previous":"<svg viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M220,680L220,280Q220,263 231.5,251.5Q243,240 260,240Q277,240 288.5,251.5Q300,263 300,280L300,680Q300,697 288.5,708.5Q277,720 260,720Q243,720 231.5,708.5Q220,697 220,680ZM678,679L430,513Q421,507 416.5,498.5Q412,490 412,480Q412,470 416.5,461.5Q421,453 430,447L678,281Q683,277 689,276Q695,275 700,275Q716,275 728,286Q740,297 740,315L740,645Q740,663 728,674Q716,685 700,685Q695,685 689,684Q683,683 678,679ZM660,480L660,480L660,480L660,480ZM660,570L660,390L524,480L660,570Z\"/></svg>","stop":"<svg viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M240,640L240,320Q240,287 263.5,263.5Q287,240 320,240L640,240Q673,240 696.5,263.5Q720,287 720,320L720,640Q720,673 696.5,696.5Q673,720 640,720L320,720Q287,720 263.5,696.5Q240,673 240,640ZM320,640L640,640Q640,640 640,640Q640,640 640,640L640,320Q640,320 640,320Q640,320 640,320L320,320Q320,320 320,320Q320,320 320,320L320,640Q320,640 320,640Q320,640 320,640ZM480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Z\"/></svg>","next":"<svg style=\"transform:scaleX(-1)\" viewBox=\"0 0 960 960\" aria-hidden=\"true\"><path d=\"M220,680L220,280Q220,263 231.5,251.5Q243,240 260,240Q277,240 288.5,251.5Q300,263 300,280L300,680Q300,697 288.5,708.5Q277,720 260,720Q243,720 231.5,708.5Q220,697 220,680ZM678,679L430,513Q421,507 416.5,498.5Q412,490 412,480Q412,470 416.5,461.5Q421,453 430,447L678,281Q683,277 689,276Q695,275 700,275Q716,275 728,286Q740,297 740,315L740,645Q740,663 728,674Q716,685 700,685Q695,685 689,684Q683,683 678,679ZM660,480L660,480L660,480L660,480ZM660,570L660,390L524,480L660,570Z\"/></svg>"};
    function readAloudTick(state) {
        var host = document.querySelector("harmonic-listen");
        var reader = document.getElementById("harmonic-reader-mode");
        if (!reader) {
            if (host) host.remove();
            var pendingCommand = speechCommand;
            speechCommand = null;
            return JSON.stringify(pendingCommand);
        }
        if (!host) {
            var heading = reader.querySelector("h1");
            if (heading && document.getElementById("harmonic-reader-article")) {
                host = document.createElement("harmonic-listen");
                host.style.cssText = "display:block!important;position:sticky!important;top:0!important;z-index:20!important;margin:12px 0 24px!important;";
                var shadow = host.attachShadow({mode:"open"});
                shadow.innerHTML = '<style>' +
                    ':host{font-size:14px}*{box-sizing:border-box}[hidden]{display:none!important}' +
                    '.bar{font:500 14px/1.4 var(--font);color:var(--text);display:grid;width:100%;max-width:100%}' +
                    '.start,.playback{grid-area:1/1;align-self:start}.start{justify-self:start}' +
                    '.playback{display:grid;grid-template-rows:0fr;opacity:0;visibility:hidden;transform:translateY(4px);pointer-events:none}' +
                    '.playback-clip{min-height:0;overflow:hidden;border-radius:24px}' +
                    '.controls{display:flex;gap:4px;align-items:center;flex-wrap:wrap;width:100%;padding:8px;border-radius:24px;background:var(--surface)}' +
                    '.active .playback{grid-template-rows:1fr;opacity:1;visibility:visible;transform:none;pointer-events:auto}' +
                    '.active .start{opacity:0;visibility:hidden;transform:translateY(-3px);pointer-events:none}' +
                    'button,select{font:inherit;color:inherit;border:0;background:transparent;border-radius:50%;height:44px;min-width:44px;padding:10px;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;-webkit-tap-highlight-color:transparent}' +
                    'svg{width:24px;height:24px;fill:currentColor;pointer-events:none}' +
                    'button:hover,select:hover{background:var(--page)}button:focus-visible,select:focus-visible{outline:2px solid var(--accent);outline-offset:2px}' +
                    'button:disabled{opacity:.35;cursor:default}.toggle{width:48px;height:48px;border-radius:18px;background:var(--accent);color:var(--page)}.toggle:hover{background:var(--accent);filter:brightness(.94)}' +
                    '.start{gap:8px;padding:0 16px 0 12px;border-radius:22px;background:var(--surface);color:var(--accent)}' +
                    '.toggle{display:grid}.toggle svg{grid-area:1/1}.toggle[data-action="pause"] .play-icon,.toggle[data-action="play"] .pause-icon{opacity:0;transform:scale(.85)}' +
                    'select{margin-left:auto;appearance:none;text-align:center;border-radius:16px;min-width:58px;background:var(--page);padding:8px}' +
                    '.status,.notice{flex:1 1 100%;padding:3px 12px 2px;color:var(--muted);font-size:12px;overflow-wrap:anywhere}' +
                    '@media(prefers-reduced-motion:no-preference){' +
                    ':host{animation:listen-appear .18s ease-out both}' +
                    '@keyframes listen-appear{from{opacity:0;transform:translateY(3px)}to{opacity:1;transform:none}}' +
                    'button{transition:background .15s,filter .15s}' +
                    '.start{transition:opacity .14s ease,transform .18s ease,visibility 0s .18s}' +
                    '.bar:not(.active) .start{transition-delay:.06s,.06s,0s}' +
                    '.playback{transition:grid-template-rows .24s cubic-bezier(.2,0,0,1),opacity .16s ease,transform .22s ease,visibility 0s .24s}' +
                    '.active .playback{transition-delay:0s,.04s,0s,0s}' +
                    '.toggle svg{transition:opacity .14s ease,transform .18s ease}' +
                    '}' +
                    '</style><div class="bar" role="group" aria-label="Read aloud">' +
                    '<button class="start" data-action="start">' + speechIcons.listen + '<span>Listen</span></button>' +
                    '<div class="playback" inert aria-hidden="true"><div class="playback-clip"><div class="controls">' +
                    '<button data-action="previous" aria-label="Previous sentence" title="Previous sentence">' + speechIcons.previous + '</button>' +
                    '<button class="toggle" data-action="pause" aria-label="Pause">' + speechIcons.pause.replace('<svg ', '<svg class="pause-icon" ') + speechIcons.play.replace('<svg ', '<svg class="play-icon" ') + '</button>' +
                    '<button data-action="next" aria-label="Next sentence" title="Next sentence">' + speechIcons.next + '</button>' +
                    '<select aria-label="Reading speed" title="Reading speed"><option value="0.75">0.75×</option><option value="1">1×</option><option value="1.25">1.25×</option><option value="1.5">1.5×</option><option value="2">2×</option></select>' +
                    '<button data-action="stop" aria-label="Stop" title="Stop reading">' + speechIcons.stop + '</button>' +
                    '<div class="status" role="status" hidden></div></div></div></div>' +
                    '<div class="notice" role="status" hidden></div></div>';
                shadow.addEventListener("click", function(event) {
                    var button = event.target.closest("button");
                    var action = button && button.getAttribute("data-action");
                    if (!event.isTrusted || !action || speechCommand) return;
                    if (action === "start") {
                        var root = document.getElementById("harmonic-reader-article");
                        var article = root ? {root:root,title:document.title} : preparedArticle(true);
                        if (!article) return;
                        var clone = article.root.cloneNode(true);
                        separateBlockText(clone);
                        var text = getText(clone);
                        if (text.length > 250000) {
                            shadow.querySelector(".notice").hidden = false;
                            shadow.querySelector(".notice").textContent = "This article is too long to read aloud.";
                            return;
                        }
                        speechCommand = {action:action,text:text,title:article.title || document.title,url:location.href};
                    } else speechCommand = {action:action};
                });
                shadow.querySelector("select").addEventListener("change", function(event) {
                    if (event.isTrusted) speechCommand = {action:"rate",rate:Number(event.target.value)};
                });
                var byline = document.getElementById("harmonic-reader-byline");
                if (byline) byline.setAttribute("data-harmonic-listen-header", "true");
                (byline || heading).insertAdjacentElement("afterend", host);
            }
        }
        if (host && host.shadowRoot) {
            var theme = document.getElementById("harmonic-reader-mode") ? getReaderTheme() : (speechTheme || getReaderTheme());
            host.style.colorScheme = theme.isLight ? "light" : "dark";
            host.style.setProperty("--surface", theme.codeBackgroundColor);
            host.style.setProperty("--page", theme.backgroundColor);
            host.style.setProperty("--text", theme.textColor);
            host.style.setProperty("--muted", theme.secondaryTextColor);
            host.style.setProperty("--accent", theme.linkColor);
            host.style.setProperty("--font", theme.fontFamily || "system-ui,sans-serif");
            var ui = host.shadowRoot;
            var active = state.count > 0;
            var start = ui.querySelector(".start");
            var playback = ui.querySelector(".playback");
            // Keep both views mounted for reversible transitions, but only the current controls
            // participate in focus, accessibility and input while the old view fades away.
            var previousFocus = ui.activeElement;
            start.inert = active;
            start.setAttribute("aria-hidden", String(active));
            playback.inert = !active;
            playback.setAttribute("aria-hidden", String(!active));
            ui.querySelector(".bar").classList.toggle("active", active);
            var toggle = ui.querySelector('[data-action="pause"],[data-action="play"]');
            toggle.setAttribute("data-action", state.playing ? "pause" : "play");
            var toggleLabel = state.playing ? "Pause" : "Resume";
            if (toggle.getAttribute("aria-label") !== toggleLabel) {
                toggle.setAttribute("aria-label", toggleLabel);
            }
            if (active && previousFocus === start) toggle.focus({preventScroll:true});
            if (!active && previousFocus && playback.contains(previousFocus)) start.focus({preventScroll:true});
            ui.querySelector('[data-action="previous"]').disabled = state.index === 0;
            ui.querySelector('[data-action="next"]').disabled = state.index >= state.count - 1;
            ui.querySelector("select").value = String(state.rate || 1);
            ui.querySelector(".notice").hidden = active || !state.error;
            var status = ui.querySelector(active ? ".status" : ".notice");
            status.hidden = !active && !state.error;
            var statusText = state.error || (active ? (state.playing ? "Reading" : "Paused") + " · Sentence " + (state.index + 1) + " of " + state.count + (state.url === location.href ? "" : " · " + state.title) : "");
            if (status.textContent !== statusText) {
                status.textContent = statusText;
                if (status.animate && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
                    if (status.readingAnimation) status.readingAnimation.cancel();
                    status.readingAnimation = status.animate([{opacity:.55}, {opacity:1}], {duration:160,easing:"ease-out"});
                }
            }
        }
        var command = speechCommand;
        speechCommand = null;
        return JSON.stringify(command);
    }

    window.HarmonicReaderMode = {
        version: 18,
        clearReadAloud: function() {
            speechCommand = null;
            var host = document.querySelector("harmonic-listen");
            if (host) host.remove();
        },
        readAloudTick: readAloudTick,
        setReadAloudTheme: function(theme) { speechTheme = theme; },
        setTheme: setTheme,
        updateTheme: updateTheme,
        isAvailable: isAvailable,
        enable: enable,
        disable: disable
    };
})();
