/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.browser;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class for handling preload requests.
 */
public class Preloader {

    private final static String LOGTAG = "browser.preloader";
    private final static boolean LOGD_ENABLED = com.android.browser.Browser.LOGD_ENABLED;

    private static final int PRERENDER_TIMEOUT_MILLIS = 30 * 1000; // 30s

    private static Preloader sInstance;

    private final Context mContext;
    private final Handler mHandler;
    private final BrowserWebViewFactory mFactory;
    private final HashMap<String, PreloaderSession> mSessions;

    public static void initialize(Context context) {
        sInstance = new Preloader(context);
    }

    public static Preloader getInstance() {
        return sInstance;
    }

    private Preloader(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mSessions = new HashMap<String, PreloaderSession>();
        mFactory = new BrowserWebViewFactory(context);

    }

    private PreloaderSession getSession(String id) {
        PreloaderSession s = mSessions.get(id);
        if (s == null) {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Create new preload session " + id);
            s = new PreloaderSession(id);
            mSessions.put(id, s);
            WebViewTimersControl.getInstance().onPrerenderStart(s.getWebView());
        }
        return s;
    }

    private PreloaderSession takeSession(String id) {
        PreloaderSession s = mSessions.remove(id);
        if (s != null) {
            s.cancelTimeout();
        }
        if (mSessions.size() == 0) {
            WebViewTimersControl.getInstance().onPrerenderDone(s == null ? null : s.getWebView());
        }
        return s;
    }

    public void handlePreloadRequest(String id, String url, Map<String, String> headers,
            String searchBoxQuery) {
        PreloaderSession s = getSession(id);
        s.touch(); // reset timer
        PreloadedTabControl tab = s.getTabControl();
        if (searchBoxQuery != null) {
            tab.loadUrlIfChanged(url, headers);
            tab.setQuery(searchBoxQuery);
        } else {
            tab.loadUrl(url, headers);
        }
    }

    public void discardPreload(String id) {
        PreloaderSession s = takeSession(id);
        if (s != null) {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Discard preload session " + id);
            PreloadedTabControl t = s.getTabControl();
            t.destroy();
        } else {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Ignored discard request " + id);
        }
    }

    /**
     * Return a preloaded tab, and remove it from the preloader. This is used when the
     * view is about to be displayed.
     */
    public PreloadedTabControl getPreloadedTab(String id) {
        PreloaderSession s = takeSession(id);
        if (LOGD_ENABLED) Log.d(LOGTAG, "Showing preload session " + id + "=" + s);
        return s == null ? null : s.getTabControl();
    }

    private class PreloaderSession {
        private final String mId;
        private final PreloadedTabControl mTabControl;

        private final Runnable mTimeoutTask = new Runnable(){
            @Override
            public void run() {
                if (LOGD_ENABLED) Log.d(LOGTAG, "Preload session timeout " + mId);
                discardPreload(mId);
            }};

        public PreloaderSession(String id) {
            mId = id;
            mTabControl = new PreloadedTabControl(
                    new Tab(new PreloadController(mContext), mFactory.createWebView(false)));
            touch();
        }

        public void cancelTimeout() {
            mHandler.removeCallbacks(mTimeoutTask);
        }

        public void touch() {
            cancelTimeout();
            mHandler.postDelayed(mTimeoutTask, PRERENDER_TIMEOUT_MILLIS);
        }

        public PreloadedTabControl getTabControl() {
            return mTabControl;
        }

        public WebView getWebView() {
            Tab t = mTabControl.getTab();
            return t == null? null : t.getWebView();
        }

    }

}
