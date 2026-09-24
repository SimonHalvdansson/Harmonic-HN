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
