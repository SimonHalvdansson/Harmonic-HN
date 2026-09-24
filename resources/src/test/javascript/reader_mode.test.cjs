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
        executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined,
    });
});
after(async () => { await browser?.close(); });

async function article(useReadability = false) {
    const page = await browser.newPage();
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
