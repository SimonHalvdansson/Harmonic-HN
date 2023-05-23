package com.gw.swipeback.tools;


import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.Stack;

/**
 * Created by GongWen on 17/9/4.
 */

public class WxSwipeBackActivityManager extends ActivityLifecycleCallbacksAdapter {
    private static final WxSwipeBackActivityManager instance = new WxSwipeBackActivityManager();
    private Stack<Activity> mActivityStack = new Stack<>();

    private WxSwipeBackActivityManager() {
    }

    public static WxSwipeBackActivityManager getInstance() {
        return instance;
    }

    public void init(Application mApplication) {
        mApplication.registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        mActivityStack.add(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mActivityStack.remove(activity);
    }

    /**
     * 获取倒数第二个Activity
     *
     * @return
     */
    public Activity getPenultimateActivity() {
        return mActivityStack.size() >= 2 ? mActivityStack.get(mActivityStack.size() - 2) : null;
    }


}
