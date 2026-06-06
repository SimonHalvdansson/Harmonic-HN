(function() {
    if (window.HarmonicReaderMode && window.HarmonicReaderMode.version === 5) {
        return;
    }

    var STATE_KEY = "__harmonicReaderModeState";
    var THEME_KEY = "__harmonicReaderModeTheme";
    var TRANSITION_KEY = "__harmonicReaderModeTransition";
    var MIN_ARTICLE_TEXT_LENGTH = 250;
    var TRANSITION_DURATION_MS = 180;
    var TRANSITION_TRANSLATE_Y = "12px";
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
    var REMOVE_SELECTOR = [
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
        "form",
        "button",
        "input",
        "select",
        "textarea",
        "dialog",
        "[aria-hidden='true']",
        "[hidden]"
    ].join(",");

    function getText(element) {
        if (!element) {
            return "";
        }
        return (element.textContent || "").replace(/\s+/g, " ").trim();
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

    function findArticle() {
        if (!document.body || getText(document.body).length < MIN_ARTICLE_TEXT_LENGTH) {
            return null;
        }

        var preferred = document.querySelector("article, main, [role='main']");
        if (preferred && getText(preferred).length >= MIN_ARTICLE_TEXT_LENGTH && linkDensity(preferred) < 0.45) {
            return preferred;
        }

        var selectors = "article, main, section, div, td, [role='main']";
        var candidates = [document.body];
        var nodes = document.body.querySelectorAll(selectors);
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
                byline: parsed.byline || "",
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

        var article = findArticle();
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
            "a": ["href", "title"],
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
        var keep = allowed[tagName] || [];
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
                    node.setAttribute("href", absolutizeUrl(href));
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
            if (!keepEmpty[tagName] && getText(node).length === 0 && node.getElementsByTagName("img").length === 0) {
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
            var text = getText(node);
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
        return Math.max(14, Math.min(24, parsed));
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
            fontSizePx: sanitizeFontSize(configured.fontSizePx, 18)
        };
    }

    function setTheme(theme) {
        window[THEME_KEY] = theme || {};
        return "theme_set";
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
            "body[data-harmonic-reader='true']{margin:0!important;background:" + theme.backgroundColor + "!important;color:" + theme.textColor + "!important;font-family:" + theme.fontFamily + "!important;line-height:1.68!important;font-size:" + bodyFontSize + "px!important;-webkit-text-size-adjust:100%!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-mode{box-sizing:border-box;max-width:760px;margin:0 auto;padding:32px 20px 96px!important;background:" + theme.backgroundColor + "!important;color:" + theme.textColor + "!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-mode *{box-sizing:border-box;max-width:100%;}",
            "#harmonic-reader-kicker{font:600 13px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif!important;letter-spacing:.04em!important;text-transform:uppercase!important;color:" + theme.linkColor + "!important;margin-bottom:14px!important;}",
            "#harmonic-reader-mode h1{font:700 " + titleFontSize + "px/1.15 " + theme.headingFontFamily + "!important;margin:0 0 12px!important;color:" + theme.headingColor + "!important;}",
            "#harmonic-reader-byline{font:500 " + bylineFontSize + "px/1.5 " + theme.fontFamily + "!important;color:" + theme.secondaryTextColor + "!important;margin:0 0 28px!important;}",
            "#harmonic-reader-article,#harmonic-reader-article section,#harmonic-reader-article div,#harmonic-reader-article figure{background:transparent!important;color:" + theme.textColor + "!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-article *{background:transparent!important;visibility:visible!important;opacity:1!important;}",
            "#harmonic-reader-article p,#harmonic-reader-article li{font-size:" + bodyFontSize + "px!important;line-height:1.68!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article span,#harmonic-reader-article strong,#harmonic-reader-article em,#harmonic-reader-article small{color:inherit!important;}",
            "#harmonic-reader-article p{margin:0 0 1.05em!important;}",
            "#harmonic-reader-article h2,#harmonic-reader-article h3,#harmonic-reader-article h4,#harmonic-reader-article h5,#harmonic-reader-article h6{font-family:" + theme.headingFontFamily + "!important;line-height:1.25!important;margin:1.65em 0 .6em!important;background:transparent!important;color:" + theme.headingColor + "!important;}",
            "#harmonic-reader-article a{color:" + theme.linkColor + "!important;text-decoration:underline!important;text-decoration-thickness:1px!important;text-underline-offset:3px!important;}",
            "#harmonic-reader-article img,#harmonic-reader-article video{display:block;height:auto!important;margin:1.2em auto!important;border-radius:4px!important;}",
            "#harmonic-reader-article figure{margin:1.4em 0!important;}",
            "#harmonic-reader-article figcaption{font:" + captionFontSize + "px/1.45 " + theme.fontFamily + "!important;color:" + theme.secondaryTextColor + "!important;margin-top:.5em!important;}",
            "#harmonic-reader-article blockquote{border-left:3px solid " + theme.linkColor + "!important;margin:1.3em 0!important;padding:.1em 0 .1em 1em!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article pre{overflow-x:auto!important;background:" + theme.codeBackgroundColor + "!important;color:" + theme.textColor + "!important;border-radius:4px!important;padding:14px!important;font-size:" + codeFontSize + "px!important;line-height:1.5!important;}",
            "#harmonic-reader-article code,#harmonic-reader-article pre *{font-family:ui-monospace,SFMono-Regular,Consolas,'Liberation Mono',monospace!important;font-size:.9em!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article table{border-collapse:collapse!important;display:block!important;overflow-x:auto!important;margin:1.2em 0!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article thead,#harmonic-reader-article tbody,#harmonic-reader-article tfoot,#harmonic-reader-article tr{background:transparent!important;color:" + theme.textColor + "!important;}",
            "#harmonic-reader-article th,#harmonic-reader-article td{border:1px solid " + theme.dividerColor + "!important;padding:8px!important;background:transparent!important;color:" + theme.textColor + "!important;}",
            "</style>"
        ].join("");
    }

    function nextTransitionId() {
        window[TRANSITION_KEY] = (window[TRANSITION_KEY] || 0) + 1;
        return window[TRANSITION_KEY];
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
        setTransitionStyle(element, "1", "0");
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (!isCurrentTransition(transitionId)) {
                    restoreTransitionStyles(element, styles);
                    return;
                }
                setTransitionStyle(element, "0", TRANSITION_TRANSLATE_Y);
                setTimeout(function() {
                    if (!isCurrentTransition(transitionId)) {
                        return;
                    }
                    after(transitionId);
                }, TRANSITION_DURATION_MS);
            });
        });
        return transitionId;
    }

    function animateIn(element, transitionId, after) {
        var styles = captureTransitionStyles(element);
        setTransitionStyle(element, "0", TRANSITION_TRANSLATE_Y);
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (!isCurrentTransition(transitionId)) {
                    restoreTransitionStyles(element, styles);
                    return;
                }
                setTransitionStyle(element, "1", "0");
                setTimeout(function() {
                    restoreTransitionStyles(element, styles);
                    if (after) {
                        after();
                    }
                }, TRANSITION_DURATION_MS);
            });
        });
    }

    function enable() {
        try {
            if (window[STATE_KEY] && window[STATE_KEY].enabled) {
                return "enabled";
            }
            if (!document.body) {
                return "unavailable";
            }

            var extractedArticle = extractArticle();
            if (!extractedArticle) {
                return "no_article";
            }

            var title = extractedArticle.title || findTitle(extractedArticle.root);
            var site = (extractedArticle.siteName || location.hostname || "").replace(/^www\./, "");
            var meta = extractedArticle.byline ? extractedArticle.byline : site;
            var originalTitle = document.title;

            window[STATE_KEY] = {
                enabled: true,
                bodyHtml: document.body.innerHTML,
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
                extractedArticle.root.innerHTML,
                "</article>",
                "</main>"
            ].join("");

            animateOut(document.body, function(transitionId) {
                var state = window[STATE_KEY];
                if (!state || !state.enabled || !isCurrentTransition(transitionId)) {
                    return;
                }
                document.body.innerHTML = html;
                restoreAttributes(document.body, {"data-harmonic-reader": "true"});
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
                document.body.innerHTML = currentState.bodyHtml;
                restoreAttributes(document.body, currentState.bodyAttrs || {});
                restoreAttributes(document.documentElement, currentState.htmlAttrs || {});
                document.title = currentState.title || document.title;
                var scrollX = currentState.scrollX || 0;
                var scrollY = currentState.scrollY || 0;
                window[STATE_KEY] = null;
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
            return document.body && extractArticle() ? "available" : "unavailable";
        } catch (e) {
            return "unavailable";
        }
    }

    window.HarmonicReaderMode = {
        version: 5,
        setTheme: setTheme,
        isAvailable: isAvailable,
        enable: enable,
        disable: disable
    };
})();
