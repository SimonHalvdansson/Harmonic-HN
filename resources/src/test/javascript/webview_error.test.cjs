// Run with node --test resources/src/test/javascript/webview_error.test.cjs and Playwright installed.
const { before, after, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const html = fs.readFileSync(path.resolve(__dirname,
    '../../commonMain/composeResources/files/web/webview_error.html'), 'utf8');
let browser;
before(async () => {
    browser = await chromium.launch({
        headless: true,
        executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined,
    });
});
after(async () => { await browser?.close(); });

async function colors(page) {
    return page.evaluate(() => ({
        background: getComputedStyle(document.body).backgroundColor,
        foreground: getComputedStyle(document.body).color,
        icon: getComputedStyle(document.querySelector(
            '#' + document.documentElement.className + ' svg path')).fill,
    }));
}
const light = { background: 'rgb(245, 245, 245)', foreground: 'rgb(51, 51, 51)', icon: 'rgb(51, 51, 51)' };
const dark = { background: 'rgb(18, 18, 18)', foreground: 'rgb(230, 230, 230)', icon: 'rgb(230, 230, 230)' };

for (const variant of ['offline', 'dns', 'ssl', 'generic']) {
    test(`${variant} follows app and system theme without reloading`, async () => {
        const page = await browser.newPage({ colorScheme: 'light' });
        try {
            await page.route('https://error.test/**', route => route.fulfill({ contentType: 'text/html', body: html }));
            await page.goto(`https://error.test/page?theme=dark#${variant}`);
            assert.equal(await page.locator('.error-page:visible').getAttribute('id'), variant);
            assert.deepEqual(await colors(page), dark);
            await page.evaluate(() => { window.originalErrorDocument = document; });
            let navigations = 0;
            page.on('framenavigated', () => navigations++);
            for (const theme of ['light', 'dark', 'light']) {
                await page.evaluate(theme => HarmonicErrorPage.setTheme(theme), theme);
                assert.deepEqual(await colors(page), theme === 'dark' ? dark : light);
            }
            // Native hosts without an explicit override use the live CSS media query.
            await page.evaluate(() => HarmonicErrorPage.setTheme(null));
            for (const colorScheme of ['dark', 'light']) {
                await page.emulateMedia({ colorScheme });
                assert.deepEqual(await colors(page), colorScheme === 'dark' ? dark : light);
            }
            assert.equal(await page.evaluate(() => document === originalErrorDocument), true);
            assert.equal(navigations, 0);
        } finally { await page.close(); }
    });
}
