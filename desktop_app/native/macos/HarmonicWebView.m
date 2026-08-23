#import <AppKit/AppKit.h>
#import <WebKit/WebKit.h>
#import <dispatch/dispatch.h>
#import <limits.h>
#import <string.h>

@interface HarmonicWebViewHost : NSObject <WKNavigationDelegate, WKUIDelegate>
@property(nonatomic, strong) WKWebView *webView;
@end

@implementation HarmonicWebViewHost

- (nullable WKWebView *)webView:(WKWebView *)webView
    createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration
               forNavigationAction:(WKNavigationAction *)navigationAction
                    windowFeatures:(WKWindowFeatures *)windowFeatures {
    // Desktop article links commonly use target=_blank. Keep them inside Harmonic so the shared
    // toolbar and navigation history continue to describe the visible page.
    if (navigationAction.targetFrame == nil) {
        [webView loadRequest:navigationAction.request];
    }
    return nil;
}

- (void)webViewWebContentProcessDidTerminate:(WKWebView *)webView {
    [webView reload];
}

@end

static void HarmonicOnMainSync(dispatch_block_t block) {
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_sync(dispatch_get_main_queue(), block);
    }
}

static HarmonicWebViewHost *HarmonicHost(void *pointer) {
    return (__bridge HarmonicWebViewHost *)pointer;
}

static NSRect HarmonicFrame(NSView *parent, double x, double top, double width, double height) {
    double resolvedWidth = MAX(1.0, width);
    double resolvedHeight = MAX(1.0, height);
    double y = NSHeight(parent.bounds) - top - resolvedHeight;
    return NSMakeRect(x, y, resolvedWidth, resolvedHeight);
}

static void HarmonicCopyString(NSString *value, char *buffer, int capacity) {
    if (buffer == NULL || capacity <= 0) return;
    if (value == nil) {
        buffer[0] = '\0';
        return;
    }
    NSData *data = [value dataUsingEncoding:NSUTF8StringEncoding];
    NSUInteger count = MIN(data.length, (NSUInteger)(capacity - 1));
    if (count > 0) memcpy(buffer, data.bytes, count);
    buffer[count] = '\0';
}

__attribute__((visibility("default")))
void *harmonic_webview_create(
    void *parentPointer,
    double x,
    double top,
    double width,
    double height
) {
    if (parentPointer == NULL) return NULL;
    __block void *result = NULL;
    HarmonicOnMainSync(^{
        @autoreleasepool {
            NSView *parent = (__bridge NSView *)parentPointer;
            WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
            WKWebView *webView = [[WKWebView alloc]
                initWithFrame:HarmonicFrame(parent, x, top, width, height)
                configuration:configuration];
            HarmonicWebViewHost *host = [[HarmonicWebViewHost alloc] init];
            host.webView = webView;
            webView.navigationDelegate = host;
            webView.UIDelegate = host;
            webView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
            [parent addSubview:webView];
            result = (__bridge_retained void *)host;
        }
    });
    return result;
}

__attribute__((visibility("default")))
void harmonic_webview_destroy(void *hostPointer) {
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{
        @autoreleasepool {
            HarmonicWebViewHost *host = (__bridge_transfer HarmonicWebViewHost *)hostPointer;
            host.webView.navigationDelegate = nil;
            host.webView.UIDelegate = nil;
            [host.webView stopLoading];
            [host.webView removeFromSuperview];
            host.webView = nil;
        }
    });
}

__attribute__((visibility("default")))
void harmonic_webview_load_url(void *hostPointer, const char *url) {
    if (hostPointer == NULL || url == NULL) return;
    NSString *urlString = [NSString stringWithUTF8String:url];
    HarmonicOnMainSync(^{
        NSURL *parsedUrl = [NSURL URLWithString:urlString];
        if (parsedUrl != nil) {
            [HarmonicHost(hostPointer).webView loadRequest:[NSURLRequest requestWithURL:parsedUrl]];
        }
    });
}

__attribute__((visibility("default")))
void harmonic_webview_go_back(void *hostPointer) {
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{
        WKWebView *webView = HarmonicHost(hostPointer).webView;
        if (webView.canGoBack) [webView goBack];
    });
}

__attribute__((visibility("default")))
void harmonic_webview_go_forward(void *hostPointer) {
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{
        WKWebView *webView = HarmonicHost(hostPointer).webView;
        if (webView.canGoForward) [webView goForward];
    });
}

__attribute__((visibility("default")))
void harmonic_webview_reload(void *hostPointer, const char *fallbackUrl) {
    if (hostPointer == NULL) return;
    NSString *fallback = fallbackUrl == NULL ? nil : [NSString stringWithUTF8String:fallbackUrl];
    HarmonicOnMainSync(^{
        WKWebView *webView = HarmonicHost(hostPointer).webView;
        if (webView.URL != nil) {
            [webView reload];
        } else if (fallback != nil) {
            NSURL *url = [NSURL URLWithString:fallback];
            if (url != nil) [webView loadRequest:[NSURLRequest requestWithURL:url]];
        }
    });
}

__attribute__((visibility("default")))
void harmonic_webview_set_visible(void *hostPointer, int visible) {
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{ HarmonicHost(hostPointer).webView.hidden = visible == 0; });
}

__attribute__((visibility("default")))
void harmonic_webview_set_frame(
    void *hostPointer,
    double x,
    double top,
    double width,
    double height
) {
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{
        WKWebView *webView = HarmonicHost(hostPointer).webView;
        if (webView.superview != nil) {
            NSRect frame = HarmonicFrame(webView.superview, x, top, width, height);
            if (!NSEqualRects(webView.frame, frame)) webView.frame = frame;
        }
    });
}

__attribute__((visibility("default")))
void harmonic_webview_evaluate_javascript(void *hostPointer, const char *script) {
    if (hostPointer == NULL || script == NULL) return;
    NSString *source = [NSString stringWithUTF8String:script];
    HarmonicOnMainSync(^{
        [HarmonicHost(hostPointer).webView evaluateJavaScript:source completionHandler:nil];
    });
}

__attribute__((visibility("default")))
void harmonic_webview_snapshot(
    void *hostPointer,
    int *state,
    char *url,
    int urlCapacity,
    char *title,
    int titleCapacity
) {
    if (state != NULL) memset(state, 0, sizeof(int) * 3);
    if (url != NULL && urlCapacity > 0) url[0] = '\0';
    if (title != NULL && titleCapacity > 0) title[0] = '\0';
    if (hostPointer == NULL) return;
    HarmonicOnMainSync(^{
        WKWebView *webView = HarmonicHost(hostPointer).webView;
        if (state != NULL) {
            state[0] = webView.loading ? 1 : 0;
            state[1] = webView.canGoBack ? 1 : 0;
            state[2] = webView.canGoForward ? 1 : 0;
        }
        HarmonicCopyString(webView.URL.absoluteString, url, urlCapacity);
        HarmonicCopyString(webView.title, title, titleCapacity);
    });
}
