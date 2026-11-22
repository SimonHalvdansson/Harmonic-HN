// Run with node --test resources/src/test/javascript/reader_mode.test.cjs and Playwright installed.
// PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH optionally selects an existing Chromium installation.
const { before, after, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const web = path.resolve(__dirname, '../../commonMain/composeResources/files/web');
const reader = fs.readFileSync(path.join(web, 'reader_mode.js'), 'utf8');
const readability = fs.readFileSync(path.join(web, 'vendor/mozilla/readability/0.6.0/Readability.min.js'), 'utf8');
let browser;
before(async () => {
    browser = await chromium.launch({
        headless: true,
        ignoreDefaultArgs: ['--hide-scrollbars'],
        executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined,
    });
});
after(async () => { await browser?.close(); });

async function article(useReadability = false, options = {}) {
    const page = await browser.newPage(options);
    await page.route('https://reader.test/article', route => route.fulfill({
        contentType: 'text/html',
        body: `<!doctype html><title>Article title</title><base href="https://cdn.test/assets/">
            <article id="article-top"><h1 id="heading">Article title</h1>
            <p>${'A substantive article sentence, with detail and punctuation. '.repeat(30)}</p>
            <p><a href="#note-1">Footnote</a> and <a href="#legacy-note">Legacy footnote</a>.</p>
            <p id="note-1">This is the footnote target.</p><a name="legacy-note"></a>
            <button id="action">Click</button><input id="draft" value="original"></article>
            <script>
                window.scriptRuns = (window.scriptRuns || 0) + 1;
                window.originalButton = document.getElementById('action');
                window.originalInput = document.getElementById('draft');
                window.clicks = 0;
                originalButton.addEventListener('click', () => window.clicks++);
                originalInput.value = 'unsaved typing';
            </script>`,
    }));
    await page.goto('https://reader.test/article');
    if (useReadability) await page.addScriptTag({ content: readability });
    await page.addScriptTag({ content: reader });
    return page;
}

async function enable(page) {
    assert.equal(await page.evaluate(() => HarmonicReaderMode.enable()), 'enabled');
    await page.waitForSelector('#harmonic-reader-article');
    await page.waitForTimeout(600); // Finish both animation callbacks, including fallback timers.
}

async function disable(page) {
    assert.equal(await page.evaluate(() => HarmonicReaderMode.disable()), 'disabled');
    await page.waitForSelector('#action');
    await page.waitForTimeout(600);
}

async function countDocumentClones(page) {
    await page.evaluate(() => {
        window.documentClones = 0;
        const original = Document.prototype.cloneNode;
        Document.prototype.cloneNode = function(deep) {
            documentClones++;
            return original.call(this, deep);
        };
    });
}

for (const viewport of [[], ['width=980'], ['width=device-width, initial-scale=1'],
    ['width=980', 'width=1200, initial-scale=0.5'], [null]]) {
    test(`mobile reader normalizes and restores viewport ${JSON.stringify(viewport)}`, async () => {
        const page = await article(true, {viewport: {width: 393, height: 852}, isMobile: true});
        try {
            await page.addStyleTag({content: 'body { min-height: 5000px }'});
            const original = await page.evaluate(contents => {
                window.sourceViewports = contents.map(content => {
                    const meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (content !== null) meta.content = content;
                    document.head.appendChild(meta);
                    return meta;
                });
                window.scrollTo(0, 450);
                return {width: innerWidth, scroll: scrollY};
            }, viewport);
            for (let cycle = 0; cycle < 2; cycle++) {
                await enable(page);
                const dimensions = await page.evaluate(() => ({
                    width: innerWidth,
                    scale: visualViewport.scale,
                    font: getComputedStyle(document.querySelector('#harmonic-reader-article p')).fontSize,
                    article: document.getElementById('harmonic-reader-article').getBoundingClientRect().toJSON(),
                }));
                assert.equal(dimensions.width, 393);
                assert.equal(dimensions.scale, 1);
                assert.equal(dimensions.font, '17px');
                assert.equal(dimensions.article.x, 20);
                assert.equal(dimensions.article.width, 353);
                await disable(page);
                assert.deepEqual(await page.evaluate(() => ({width: innerWidth, scroll: scrollY})), original);
                assert.deepEqual(await page.locator('meta[name="viewport"]').evaluateAll(nodes =>
                    nodes.map(node => node.getAttribute('content'))), viewport);
                assert.equal(await page.evaluate(() => sourceViewports.every(node => node.isConnected)), true);
            }
            await page.evaluate(() => { HarmonicReaderMode.enable(); HarmonicReaderMode.disable(); });
            await page.waitForTimeout(800);
            assert.deepEqual(await page.locator('meta[name="viewport"]').evaluateAll(nodes =>
                nodes.map(node => node.getAttribute('content'))), viewport);
            assert.deepEqual(await page.evaluate(() => ({width: innerWidth, scroll: scrollY})), original);
        } finally { await page.close(); }
    });
}

for (const useReadability of [false, true]) {
    for (const dialogMarkup of ['<dialog open>', '<div role="dialog">',
        '<div role="alertdialog">', '<div aria-modal="true">']) {
        test(`exclude ${dialogMarkup} before article selection (${useReadability ? 'Readability' : 'fallback'})`, async () => {
            const page = await article(useReadability);
            try {
                await page.evaluate(markup => {
                    const closingTag = markup.startsWith('<dialog') ? '</dialog>' : '</div>';
                    const notice = '<p>' + 'Consent notice: choose how partners store personal data. '.repeat(70) + '</p>';
                    document.body.insertAdjacentHTML('afterbegin', markup + '<main>' + notice.repeat(8) + '</main>' + closingTag);
                    window.originalDialog = document.body.firstElementChild;
                    document.querySelector('article p').textContent =
                        'This article discusses cookie consent, privacy policy, and browser storage in detail. '.repeat(30);
                }, dialogMarkup);
                assert.equal(await page.evaluate(() => HarmonicReaderMode.isAvailable()), 'available');
                assert.equal(await page.evaluate(() => originalDialog.isConnected), true,
                    'Availability must not dismiss or change source dialogs');
                await enable(page);
                const text = await page.locator('#harmonic-reader-article').textContent();
                assert.match(text, /This article discusses cookie consent/);
                assert.doesNotMatch(text, /Consent notice:/);
                await disable(page);
                assert.equal(await page.evaluate(() => originalDialog === document.body.firstElementChild), true);
            } finally { await page.close(); }
        });
    }

    test(`dialog-only pages are unavailable (${useReadability ? 'Readability' : 'fallback'})`, async () => {
        const page = await article(useReadability);
        try {
            await page.evaluate(() => {
                document.body.innerHTML = '<div role="dialog"><article><p>' +
                    'A lengthy consent notice is not an article to read. '.repeat(100) + '</p></article></div>';
            });
            assert.equal(await page.evaluate(() => HarmonicReaderMode.isAvailable()), 'unavailable');
            assert.equal(await page.evaluate(() => HarmonicReaderMode.enable()), 'no_article');
            assert.equal(await page.locator('[role="dialog"]').count(), 1);
        } finally { await page.close(); }
    });

    test(`short content outranks forms and secondary comments (${useReadability ? 'Readability' : 'fallback'})`, async () => {
        const page = await article(useReadability);
        try {
            await page.evaluate(() => {
                document.querySelector('article').outerHTML = `<div role="main">
                    <h1>Why measure underground vibrations?</h1>
                    <p>What can scientists learn from small explosions?</p>
                    <div><p>${'Seismic measurements reveal underground structure by measuring the propagation of shockwaves. '.repeat(5)}</p></div>
                    <div itemprop="comment"><p>${'Moderator notice about keeping the discussion on topic. '.repeat(30)}</p></div>
                    <form><p>${'Sign up or log in to submit your response to this discussion. '.repeat(50)}</p></form>
                </div>`;
            });
            await enable(page);
            const text = await page.locator('#harmonic-reader-article').textContent();
            assert.match(text, /Seismic measurements reveal underground structure/);
            assert.doesNotMatch(text, /Moderator notice|Sign up or log in/);
        } finally { await page.close(); }
    });
}

for (const customScrollbar of [false, true]) {
    test(`reader transition preserves scroll and scrollbar layout (${customScrollbar ? 'custom' : 'native'})`, async () => {
        const page = await article(true);
        try {
            await page.addStyleTag({ content: `body { min-height: 5000px }
                ${customScrollbar ? 'html::-webkit-scrollbar { width:24px; background:black } html::-webkit-scrollbar-thumb { background:red }' : ''}` });
            const original = await page.evaluate(() => {
                window.scrollTo(0, 600);
                return { width: document.documentElement.clientWidth, scroll: window.scrollY };
            });
            await page.evaluate(() => HarmonicReaderMode.enable());
            assert.equal(await page.evaluate(() => document.documentElement.clientWidth), original.width,
                'Suppressing scrollbar paint must not change the source layout');
            assert.equal(await page.evaluate(() => window.scrollY), original.scroll);
            assert.equal(await page.locator('#harmonic-reader-transition-scrollbar').count(), 1);
            await page.waitForSelector('#harmonic-reader-article');
            await page.waitForFunction(() => !document.getElementById('harmonic-reader-transition-scrollbar'));
            await disable(page);
            assert.deepEqual(await page.evaluate(() => ({
                width: document.documentElement.clientWidth, scroll: window.scrollY,
            })), original);
            assert.equal(await page.locator('#harmonic-reader-transition-scrollbar').count(), 0);
            assert.equal(await page.inputValue('#draft'), 'unsaved typing');
            await page.click('#action');
            assert.equal(await page.evaluate(() => clicks), 1);
        } finally { await page.close(); }
    });
}

test('rapid reader cancellation restores scrollbar styles and the original document', async () => {
    const page = await article(true);
    try {
        await page.evaluate(() => { HarmonicReaderMode.enable(); HarmonicReaderMode.disable(); });
        await page.waitForFunction(() => !document.getElementById('harmonic-reader-transition-scrollbar'));
        assert.equal(await page.locator('#harmonic-reader-article').count(), 0);
        assert.equal(await page.evaluate(() => originalButton === document.getElementById('action')), true);
        await enable(page);
        await disable(page);
        assert.equal(await page.locator('#harmonic-reader-transition-scrollbar').count(), 0);
    } finally { await page.close(); }
});

test('reader uses the new default, supports 13px, and updates line height without replacing article content', async () => {
    const page = await article(true);
    try {
        await enable(page);
        const typography = () => page.evaluate(() => {
            const style = getComputedStyle(document.querySelector('#harmonic-reader-article p'));
            return [style.fontSize, style.lineHeight];
        });
        assert.deepEqual(await typography(), ['17px', '28.56px']);
        await page.evaluate(() => {
            window.readerParagraph = document.querySelector('#harmonic-reader-article p');
            HarmonicReaderMode.setTheme({fontSizePx: 13, lineHeight: 1.4});
        });
        assert.deepEqual(await typography(), ['13px', '18.2px']);
        await page.evaluate(() => HarmonicReaderMode.setTheme({fontSizePx: 17, lineHeight: 2}));
        assert.deepEqual(await typography(), ['17px', '34px']);
        assert.equal(await page.evaluate(() => readerParagraph === document.querySelector('#harmonic-reader-article p')), true);
        await page.evaluate(() => HarmonicReaderMode.setTheme({fontSizePx: -100, lineHeight: 'invalid'}));
        assert.deepEqual(await typography(), ['13px', '21.84px']);
        await disable(page);
        assert.equal(await page.inputValue('#draft'), 'unsaved typing');
    } finally { await page.close(); }
});

test('availability reuses its extraction on enable without changing article output', async () => {
    const page = await article(true);
    try {
        await countDocumentClones(page);
        assert.equal(await page.evaluate(() => HarmonicReaderMode.isAvailable()), 'available');
        assert.equal(await page.evaluate(() => HarmonicReaderMode.isAvailable()), 'available');
        await enable(page);
        assert.equal(await page.evaluate(() => documentClones), 1);
        assert.match(await page.locator('#harmonic-reader-article').textContent(), /substantive article sentence/);
        const cachedHtml = await page.locator('#harmonic-reader-article').innerHTML();
        await disable(page);
        await enable(page);
        assert.equal(await page.evaluate(() => documentClones), 2, 'restored document gets a fresh extraction');
        assert.equal(await page.locator('#harmonic-reader-article').innerHTML(), cachedHtml);
    } finally { await page.close(); }
});

for (const sameTask of [false, true]) {
    test(`source changes invalidate extracted content (${sameTask ? 'before' : 'after'} observer delivery)`, async () => {
        const page = await article(true);
        try {
            await countDocumentClones(page);
            await page.evaluate(() => HarmonicReaderMode.isAvailable());
            await page.evaluate(sameTask => {
                document.querySelector('article p').textContent += ' Updated source text.';
                if (sameTask) HarmonicReaderMode.enable();
            }, sameTask);
            if (!sameTask) await enable(page);
            else await page.waitForSelector('#harmonic-reader-article');
            assert.match(await page.locator('#harmonic-reader-article').textContent(), /Updated source text/);
            assert.equal(await page.evaluate(() => documentClones), 2);
        } finally { await page.close(); }
    });
}

for (const change of ['url', 'base', 'expired', 'attributes']) {
    test(`reader extraction cache invalidates on ${change}`, async () => {
        const page = await article(true);
        try {
            await countDocumentClones(page);
            await page.evaluate(() => HarmonicReaderMode.isAvailable());
            assert.equal(await page.evaluate(change => {
                if (change === 'url') history.replaceState(null, '', 'https://reader.test/different-article');
                if (change === 'base') document.querySelector('base').href = 'https://new-base.test/';
                if (change === 'attributes') document.querySelector('article').setAttribute('class', 'new-content');
                if (change === 'expired') {
                    const now = Date.now();
                    Date.now = () => now + 31000;
                }
                return HarmonicReaderMode.isAvailable();
            }, change), 'available');
            assert.equal(await page.evaluate(() => documentClones), 2);
        } finally { await page.close(); }
    });
}

test('unavailable results invalidate when the page receives article content', async () => {
    const page = await article(true);
    try {
        const result = await page.evaluate(() => {
            const original = document.body.innerHTML;
            document.body.innerHTML = '<p>Loading</p>';
            const empty = HarmonicReaderMode.isAvailable();
            document.body.innerHTML = original;
            return [empty, HarmonicReaderMode.isAvailable()];
        });
        assert.deepEqual(result, ['unavailable', 'available']);
    } finally { await page.close(); }
});

test('oversized articles are extracted correctly without retaining a cached DOM', async () => {
    const page = await article(true);
    try {
        await page.evaluate(() => {
            document.querySelector('article p').textContent = 'Large article sentence with details. '.repeat(10000);
        });
        await countDocumentClones(page);
        await page.evaluate(() => HarmonicReaderMode.isAvailable());
        await page.evaluate(() => HarmonicReaderMode.isAvailable());
        assert.equal(await page.evaluate(() => documentClones), 2);
    } finally { await page.close(); }
});

test('compact theme updates retain installed fonts and reject stale or missing installations', async () => {
    const page = await article(true);
    try {
        assert.deepEqual(await page.evaluate(() => {
            const api = HarmonicReaderMode;
            const missing = api.updateTheme({fontSizePx: 24}, 'one');
            api.setTheme({fontFaceCss: 'FONT_CSS', fontSizePx: 16}, 'one');
            const accepted = api.updateTheme({fontSizePx: 24}, 'one');
            const retained = window.__harmonicReaderModeTheme;
            const stale = api.updateTheme({fontSizePx: 40}, 'two');
            api.setTheme({fontFaceCss: '', fontSizePx: 18}, 'two');
            return {missing, accepted, retained, stale, deviceFont: window.__harmonicReaderModeTheme.fontFaceCss};
        }), {
            missing: false, accepted: true, retained: {fontSizePx: 24, fontFaceCss: 'FONT_CSS'},
            stale: false, deviceFont: '',
        });
    } finally { await page.close(); }
});

function contrast(foreground, background) {
    const luminance = rgb => rgb.map(value => {
        const channel = value / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    }).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
    const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
    return (values[0] + 0.05) / (values[1] + 0.05);
}

for (const useReadability of [false, true]) {
    for (const dark of [false, true]) {
        test(`reader corrects faint colors and preserves readable colors (${useReadability ? 'Readability' : 'fallback'}, ${dark ? 'dark' : 'light'})`, async () => {
            const page = await article(useReadability);
            try {
                await page.addStyleTag({ content: `
                    #faint { color: ${dark ? '#302028' : 'pink'} !important; }
                    #readable { color: ${dark ? '#90ee90' : '#800000'} !important; }
                    #alpha { color: rgba(120, 80, 180, .15); }
                    #wide-gamut { color: color(display-p3 .7 .6 .8 / .1); }
                ` });
                await page.evaluate(dark => {
                    HarmonicReaderMode.setTheme({ isLight: !dark });
                    document.querySelector('article').insertAdjacentHTML('beforeend', `
                        <p><q id="faint">A faint quotation with <b id="nested">nested emphasis</b>.</q>
                        <q id="readable">Readable source color.</q>
                        <q id="alpha">Translucent source text.</q>
                        <q id="wide-gamut">Wide gamut source text.</q></p>`);
                }, dark);
                const original = await page.locator('#faint').evaluate(node => getComputedStyle(node).color);
                await enable(page);
                const colors = await page.evaluate(() => Object.fromEntries(
                    ['faint', 'nested', 'readable', 'alpha', 'wide-gamut'].map(id =>
                        [id, getComputedStyle(document.getElementById(id)).color.match(/[\d.]+/g).map(Number)]
                    )
                ));
                const background = dark ? [21, 22, 23] : [250, 250, 250];
                for (const [id, color] of Object.entries(colors)) {
                    assert.ok(contrast(color, background) >= 4.5, `${id}: ${color}`);
                }
                assert.deepEqual(colors.readable, dark ? [144, 238, 144] : [128, 0, 0]);
                assert.notDeepEqual(colors.faint, dark ? [232, 234, 237] : [32, 33, 36], 'retain some source tint');
                await disable(page);
                assert.equal(await page.locator('#faint').evaluate(node => getComputedStyle(node).color), original);
            } finally { await page.close(); }
        });
    }

    for (const separator of ['<br>', '</div><div>']) {
        test(`byline preserves structural spacing without splitting inline words (${useReadability ? 'Readability' : 'fallback'}, ${separator})`, async () => {
            const page = await article(useReadability);
            try {
                await page.evaluate(separator => {
                    document.querySelector('article').insertAdjacentHTML('afterbegin',
                        `<div class="byline"><div>By Jo Mc<em>Donald</em>${separator}September 23, 2026</div></div>`);
                }, separator);
                await enable(page);
                assert.equal(await page.locator('#harmonic-reader-byline').textContent(), 'By Jo McDonald September 23, 2026');
            } finally { await page.close(); }
        });
    }
}

test('contrast correction uses the code background and handles an insufficient theme text color', async () => {
    const page = await article();
    try {
        await page.evaluate(() => {
            HarmonicReaderMode.setTheme({ textColor: '#777777', codeBackgroundColor: '#888888' });
            document.querySelector('article').insertAdjacentHTML('beforeend', '<pre id="code-contrast">code sample</pre>');
        });
        await enable(page);
        const color = await page.locator('#code-contrast').evaluate(node => getComputedStyle(node).color.match(/\d+/g).map(Number));
        assert.ok(contrast(color, [136, 136, 136]) >= 4.5, String(color));
    } finally { await page.close(); }
});

for (const useReadability of [false, true]) {
    test(`reader preserves fragment destinations (${useReadability ? 'Readability' : 'fallback'})`, async () => {
        const page = await article(useReadability);
        try {
            await enable(page);
            assert.equal(await page.locator('#note-1').count(), 1);
            assert.equal(await page.locator('[name="legacy-note"]').count(), 1);
            await page.getByRole('link', { name: 'Footnote', exact: true }).click();
            assert.equal(new URL(page.url()).hash, '#note-1');
            assert.equal(new URL(page.url()).host, 'reader.test');
        } finally { await page.close(); }
    });
}

test('reader restores original node identity, listeners and live form values over repeated toggles', async () => {
    const page = await article(true);
    try {
        for (let cycle = 1; cycle <= 2; cycle++) {
            await enable(page);
            await disable(page);
            await page.locator('#action').click();
            assert.deepEqual(await page.evaluate(() => ({
                sameButton: document.getElementById('action') === originalButton,
                sameInput: document.getElementById('draft') === originalInput,
                value: document.getElementById('draft').value,
                clicks,
                scriptRuns,
            })), { sameButton: true, sameInput: true, value: 'unsaved typing', clicks: cycle, scriptRuns: 1 });
        }
    } finally { await page.close(); }
});

test('cancelling reader entry before its transition commits retains the original page', async () => {
    const page = await article();
    try {
        await page.evaluate(() => { HarmonicReaderMode.enable(); HarmonicReaderMode.disable(); });
        await page.waitForTimeout(800);
        await page.locator('#action').click();
        assert.deepEqual(await page.evaluate(() => ({
            original: document.getElementById('action') === originalButton,
            reader: Boolean(document.querySelector('#harmonic-reader-mode')),
            clicks,
            opacity: getComputedStyle(document.body).opacity,
        })), { original: true, reader: false, clicks: 1, opacity: '1' });
    } finally { await page.close(); }
});

for (const useReadability of [false, true]) {
    test(`wide blocks scroll independently without widening the reader (${useReadability ? 'Readability' : 'fallback'})`, async () => {
        const page = await article(useReadability);
        try {
            await page.setViewportSize({ width: 393, height: 852 });
            // Author styles remain in the head when the reader replaces the body.
            await page.addStyleTag({ content: `
                body { display: grid; grid-template-columns: 1fr; min-width: 900px; }
                #wide-grid { display: grid; grid-template-columns: 1fr; }
                #wide-flex { display: flex; }
                pre { white-space: pre-wrap; }
            ` });
            await page.evaluate(() => {
                const article = document.querySelector('article');
                article.insertAdjacentHTML('beforeend', `
                    <section id="wide-grid"><div id="wide-flex"><div>
                        <pre id="wide-code"><code>${'column_name = value; '.repeat(40)}</code></pre>
                        <table id="wide-table"><tbody><tr>
                            ${'<td>unbroken_column_value</td>'.repeat(12)}
                        </tr></tbody></table>
                    </div></div></section>`);
            });
            await enable(page);
            for (const width of [393, 320, 852]) {
                await page.setViewportSize({ width, height: 852 });
                const dimensions = await page.evaluate(() => {
                    const root = document.documentElement;
                    const blocks = ['wide-code', 'wide-table'].map(id => {
                        const block = document.getElementById(id);
                        block.scrollLeft = 100;
                        return { id, width: block.clientWidth, content: block.scrollWidth, left: block.scrollLeft };
                    });
                    window.scrollTo(100, 0);
                    return {
                        width: root.clientWidth, content: root.scrollWidth, left: window.scrollX,
                        articleWidth: document.getElementById('harmonic-reader-article').getBoundingClientRect().width,
                        blocks,
                    };
                });
                assert.ok(dimensions.content <= dimensions.width, JSON.stringify(dimensions));
                assert.equal(dimensions.left, 0);
                assert.ok(dimensions.articleWidth <= width - 40, JSON.stringify(dimensions));
                for (const block of dimensions.blocks) {
                    assert.ok(block.content > block.width, `${block.id} should retain wide content`);
                    assert.ok(block.left > 0, `${block.id} should scroll independently`);
                }
            }
            await disable(page);
            assert.equal(await page.locator('html').getAttribute('data-harmonic-reader'), null);
            assert.equal(await page.evaluate(() => getComputedStyle(document.body).display), 'grid');
        } finally { await page.close(); }
    });
}

// Native hosts update themes in-place while reader is open.
test('live theme switch recomputes source text contrast', async () => {
    const page = await article(false);
    try {
        await page.addStyleTag({ content: '#faint { color: pink !important; }' });
        await page.evaluate(() => document.querySelector('article').insertAdjacentHTML('beforeend',
            '<p><q id="faint">Source-colored text must stay readable when the user switches themes.</q></p>'));
        await enable(page);
        const getColor = () => page.locator('#faint').evaluate(node => getComputedStyle(node).color.match(/[\d.]+/g).map(Number));
        const lightColor = await getColor();
        assert.ok(contrast(lightColor, [250,250,250]) >= 4.5);
        await page.evaluate(() => HarmonicReaderMode.setTheme({ isLight: false }));
        const darkColor = await getColor();
        assert.ok(contrast(darkColor, [21,22,23]) >= 4.5,
            'The correction must be recalculated against the new background');
        assert.deepEqual(darkColor, [255, 192, 203], 'Readable source pink should be restored in dark mode');
        await page.evaluate(() => HarmonicReaderMode.setTheme({ isLight: true }));
        assert.deepEqual(await getColor(), lightColor, 'Theme round trips must not accumulate color corrections');
        await disable(page);
        assert.deepEqual(await getColor(), [255, 192, 203], 'The original article retains its source color');
    } finally { await page.close(); }
});

for (const useReadability of [false, true]) {
    test(`reader preserves wrapped video (${useReadability ? 'Readability' : 'fallback'})`, async () => {
        const page = await article(useReadability);
        try {
            await page.locator('article').evaluate(article => {
                article.insertAdjacentHTML('beforeend', `
                    <video id="direct-video" src="data:video/mp4;base64," controls></video>
                    <figure><video id="figure-video" src="data:video/mp4;base64," controls></video></figure>
                    <figure><video id="source-video" controls><source src="data:video/mp4;base64," type="video/mp4"></video></figure>
                    <div class="empty-wrapper"><span></span></div>`);
            });
            await enable(page);
            const content = page.locator('#harmonic-reader-article');
            assert.deepEqual(await content.locator('video').evaluateAll(nodes => nodes.map(node => node.id)),
                ['direct-video', 'figure-video', 'source-video']);
            assert.equal(await content.locator('#figure-video').evaluate(node => node.parentElement.tagName), 'FIGURE');
            assert.equal(await content.locator('#source-video source').count(), 1);
            assert.equal(await content.locator('.empty-wrapper').count(), 0);
        } finally { await page.close(); }
    });
}


test('read aloud only appears in reader mode and restores playback controls on return', async () => {
    const page = await article(true);
    const idle = {count:0,rate:1};
    try {
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), idle);
        assert.equal(await page.locator('harmonic-listen').count(), 0);
        await enable(page);
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), idle);
        await page.getByRole('button', {name:'Listen',exact:true}).click();
        await page.evaluate(() => HarmonicReaderMode.clearReadAloud());
        assert.equal(await page.locator('harmonic-listen').count(), 0);
        assert.equal(await page.evaluate(state => JSON.parse(HarmonicReaderMode.readAloudTick(state)), idle), null);
        await page.getByRole('button', {name:'Listen',exact:true}).click();
        const command = await page.evaluate(state => JSON.parse(HarmonicReaderMode.readAloudTick(state)), idle);
        assert.equal(command.action, 'start');
        assert.equal(command.title, 'Article title');
        assert.ok(command.text.includes('substantive article'));
        assert.ok(!command.text.includes('Listen'));
        const playing = {count:30,index:2,playing:true,rate:1.5,title:command.title};
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), playing);
        await page.getByRole('button', {name:'Pause',exact:true}).click();
        assert.equal(await page.evaluate(state => JSON.parse(HarmonicReaderMode.readAloudTick(state)).action, playing), 'pause');
        await disable(page);
        assert.equal(await page.locator('harmonic-listen').count(), 0);
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), playing);
        assert.equal(await page.locator('harmonic-listen').count(), 0);
        await enable(page);
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), {...playing,playing:false});
        await page.getByRole('button', {name:'Resume',exact:true}).click();
        assert.equal(await page.evaluate(state => JSON.parse(HarmonicReaderMode.readAloudTick(state)).action, playing), 'play');
        await disable(page);
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), {...playing,playing:false});
        assert.equal(await page.locator('harmonic-listen').count(), 0);
        await page.evaluate(state => HarmonicReaderMode.readAloudTick(state), idle);
        assert.equal(await page.locator('harmonic-listen').count(), 0);
    } finally { await page.close(); }
});


test('read aloud matches the reader theme and fits one control row on phones', async () => {
    const page = await article(true);
    try {
        await page.setViewportSize({width:393,height:750});
        const font = fs.readFileSync(path.resolve(web, '../../font/google_sans_flex_rounded_regular.ttf')).toString('base64');
        await page.evaluate(font => HarmonicReaderMode.setTheme({
            isLight:true,backgroundColor:'#ececf5',textColor:'#303038',headingColor:'#303038',
            secondaryTextColor:'#5b5d68',linkColor:'#50618f',dividerColor:'#c6c8d4',codeBackgroundColor:'#dedfe9',
            fontFamily:'HarmonicReaderFont, sans-serif',headingFontFamily:'HarmonicReaderFont, sans-serif',
            fontFaceCss:'@font-face{font-family:HarmonicReaderFont;src:url(data:font/ttf;base64,' + font + ') format("truetype");}',fontSizePx:17
        }),font);
        await enable(page);
        await page.evaluate(() => HarmonicReaderMode.readAloudTick({count:30,index:2,playing:true,rate:1,url:location.href,title:document.title}));
        await page.evaluate(() => document.fonts.ready);
        await page.waitForTimeout(300); // Measure settled spacing after the Listen entrance.
        const metrics = await page.evaluate(() => {
            const host=document.querySelector('harmonic-listen');
            const controls=[...host.shadowRoot.querySelectorAll('button,select')].filter(el=>!el.closest('[inert]'));
            return {tops:controls.map(el=>Math.round(el.getBoundingClientRect().top)),
                color:getComputedStyle(host.shadowRoot.querySelector('.controls')).backgroundColor,
                overflow:document.documentElement.scrollWidth>window.innerWidth,
                left:host.getBoundingClientRect().left,
                articleLeft:document.getElementById('harmonic-reader-article').getBoundingClientRect().left,
                right:host.getBoundingClientRect().right,
                articleRight:document.getElementById('harmonic-reader-article').getBoundingClientRect().right,
                headerGap:host.getBoundingClientRect().top-document.getElementById('harmonic-reader-byline').getBoundingClientRect().bottom,
                articleGap:document.getElementById('harmonic-reader-article').getBoundingClientRect().top-host.getBoundingClientRect().bottom};
        });
        assert.ok(Math.max(...metrics.tops)-Math.min(...metrics.tops)<=3, JSON.stringify(metrics));
        assert.equal(metrics.color,'rgb(222, 223, 233)');
        assert.equal(metrics.overflow,false);
        assert.equal(metrics.left,metrics.articleLeft);
        assert.equal(metrics.right,metrics.articleRight);
        assert.equal(metrics.headerGap,12);
        assert.equal(metrics.articleGap,24);
        if(process.env.PLAYWRIGHT_SCREENSHOT_DIR) await page.screenshot({path:path.join(process.env.PLAYWRIGHT_SCREENSHOT_DIR,'reader-player-light.png')});
        await page.evaluate(() => HarmonicReaderMode.setTheme({...window.__harmonicReaderModeTheme,isLight:false,backgroundColor:'#171821',textColor:'#e3e2ed',headingColor:'#e3e2ed',secondaryTextColor:'#c4c5d2',linkColor:'#b4c5f9',dividerColor:'#444652',codeBackgroundColor:'#2d2e39',fontFamily:'HarmonicReaderFont, sans-serif',fontSizePx:17}));
        await page.evaluate(() => HarmonicReaderMode.readAloudTick({count:30,index:2,playing:false,rate:1,url:location.href,title:document.title}));
        await page.waitForTimeout(250); // Let the control's theme transition finish.
        if(process.env.PLAYWRIGHT_SCREENSHOT_DIR) await page.screenshot({path:path.join(process.env.PLAYWRIGHT_SCREENSHOT_DIR,'reader-player-dark.png')});
    } finally { await page.close(); }
});

for (const reducedMotion of ['no-preference', 'reduce']) {
    test(`Listen transitions preserve controls and focus (${reducedMotion})`, async () => {
        const page = await article(true);
        const idle = {count:0,index:0,playing:false,rate:1};
        const playing = {count:30,index:2,playing:true,rate:1,url:'https://reader.test/article',title:'Article title'};
        const tick = state => page.evaluate(state => HarmonicReaderMode.readAloudTick(state), state);
        const metrics = () => page.locator('harmonic-listen').evaluate(host => ({
            height:host.getBoundingClientRect().height,
            active:host.shadowRoot.activeElement?.getAttribute('data-action'),
            animations:[...host.shadowRoot.querySelectorAll('*')].flatMap(el => el.getAnimations()).filter(a => a.playState === 'running').length,
        }));
        try {
            await page.emulateMedia({reducedMotion});
            await page.setViewportSize({width:393,height:750});
            await enable(page);
            await tick(idle);
            await page.getByRole('button', {name:'Listen',exact:true}).focus();
            await page.waitForTimeout(300);
            const collapsed = await metrics();
            await tick(playing);
            assert.equal(await page.getByRole('button', {name:'Listen',exact:true}).count(), 0);
            assert.equal(await page.getByRole('button', {name:'Pause',exact:true}).count(), 1);
            assert.equal((await metrics()).active, 'pause');
            await page.waitForTimeout(70);
            const opening = await metrics();
            await page.waitForTimeout(300);
            const expanded = await metrics();
            assert.ok(expanded.height > collapsed.height);
            if (reducedMotion === 'reduce') {
                assert.equal(opening.height, expanded.height);
                assert.equal(opening.animations, 0);
            } else {
                assert.ok(opening.height < expanded.height, 'The article should move gradually as the controls expand');
                assert.ok(opening.animations > 0);
            }
            // Routine host polling must not restart the animation.
            await tick(playing);
            assert.equal((await metrics()).animations, 0);
            await tick({...playing,playing:false});
            assert.equal(await page.getByRole('button', {name:'Resume',exact:true}).count(), 1);
            await page.getByRole('button', {name:'Stop',exact:true}).focus();
            await tick(idle);
            assert.equal((await metrics()).active, 'start');
            assert.equal(await page.getByRole('button', {name:'Stop',exact:true}).count(), 0);
            // Reverse the animation before it completes, then stop again.
            await tick(playing);
            await tick(idle);
            await page.waitForTimeout(350);
            assert.equal((await metrics()).height, collapsed.height);
            await page.getByRole('button', {name:'Listen',exact:true}).click();
            assert.equal(JSON.parse(await tick(idle)).action, 'start');
            await tick({...idle,error:'Audio is unavailable'});
            assert.equal(await page.getByRole('status').textContent(), 'Audio is unavailable');
        } finally { await page.close(); }
    });
}
