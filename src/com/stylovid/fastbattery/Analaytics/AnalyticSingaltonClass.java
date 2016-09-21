package com.stylovid.fastbattery.Analaytics;

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.stylovid.fastbattery.R;


public class AnalyticSingaltonClass {

    private static String PROPERTY_ID; // Property Id.
    private static AnalyticSingaltonClass mAnalyticsSingaltonClass;
    private Context mContext;

    public static AnalyticSingaltonClass getInstance(Context context) {
        if (mAnalyticsSingaltonClass == null) {
            mAnalyticsSingaltonClass = new AnalyticSingaltonClass(context);
        }

        PROPERTY_ID = context.getResources().getString(R.string.google_analytics_id);

        return mAnalyticsSingaltonClass;
    }

    public AnalyticSingaltonClass(Context context) {
        mContext = context.getApplicationContext();
    }

    private synchronized Tracker getTracker() {
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(mContext);
        return analytics.newTracker(PROPERTY_ID);
    }

    public void sendScreenAnalytics(String screenName) {
        Tracker screenTracker = getTracker();
        screenTracker.setScreenName(screenName);
        screenTracker.send(new HitBuilders.AppViewBuilder().build());
    }

    public void sendEventAnalytics(String eventCategory, String eventAction) {
        Tracker eventTracker = getTracker();
        eventTracker.send(new HitBuilders.EventBuilder().setCategory(eventCategory).setAction(eventAction).build());
    }
}