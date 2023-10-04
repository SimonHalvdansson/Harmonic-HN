package com.simon.harmonichackernews.network;



import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.MainActivity;
import com.simon.harmonichackernews.Manifest;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.SubmissionsActivity;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.htmltextview.OnClickATagListener;
import org.w3c.dom.Comment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class RepliesChecker {

    private final static String REPLY_INACTIVE_IDS_KEY = "REPLY_INACTIVE_IDS_KEY";
    public static final String CHANNEL_ID = "REPLY_CHANNEL";


    private final static int MAX_SUBMISSIONS = 10;

    public static void loadUserReplies(final Context ctx, String username1) {
        final String username = "pg";

        //We first load the user's profile as it contains all the "submitted", then we check the
        // children of all those. We need a list of the creation dates for these so we only do this
        //once so we don't go through the entire ancient comment history each time

        RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);

        //we first load the HN API for the user
        StringRequest userRequest = new StringRequest(Request.Method.GET, "https://hacker-news.firebaseio.com/v0/user/" + username + ".json",
                userResponse -> {
                    try {
                        JSONObject jsonObject = new JSONObject(userResponse);

                        //to get all submissions
                        if (jsonObject.has("submitted")) {
                            JSONArray submitted = jsonObject.getJSONArray("submitted");
                            //and put them in an int[]
                            int[] submittedIds = new int[Math.min(submitted.length(), MAX_SUBMISSIONS)];
                            //but only the first (latest) 100, we don't want to dig too deep
                            Utils.log("User had submissions");
                            for (int i = 0; i < submitted.length() && i < MAX_SUBMISSIONS; i++) {
                                submittedIds[i] = submitted.getInt(i);
                                Utils.log(submittedIds[i]);
                            }

                            //we now have all submissions, let's clear out the old ones
                            Set<Integer> submittedActive = getActiveIds(ctx, submittedIds);
                            Utils.log("Below are the active");
                            for (int id : submittedActive) {
                                Utils.log(id);
                                //for each id, let's load the object
                                StringRequest submissionRequest = new StringRequest(Request.Method.GET, "https://hacker-news.firebaseio.com/v0/item/" + id + ".json",
                                        submissionResponse -> {
                                            Utils.log("Got a response for ID: " + id);
                                            try {
                                                //note that this is a story/comment by the user
                                                Story story = new Story();
                                                //check so things look good
                                                if (JSONParser.updateStoryWithHNJson(submissionResponse, story)) {
                                                    Utils.log("Parsing successful");
                                                    //now that we have the story/comment, we first check so that it is not old so
                                                    //we don't need to load it next time in that case
                                                    if (Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time)) {
                                                        Utils.log("Post was too old, marking inactive");
                                                        markInactive(ctx, id);
                                                    } else if (story.kids != null) {
                                                        Utils.log("Post was fresh and has kids");
                                                        //if the submission is fresh, let's check its list of children
                                                        // as these are the replies we are looking for
                                                        for (int kidId : story.kids) {
                                                            Utils.log("Found kid " + kidId);
                                                            if (!isInactive(ctx, kidId)) {
                                                                Utils.log("It was active!");
                                                                //if we haven't notified about this, let's do so now
                                                                //first we need to load the kid though
                                                                StringRequest kidRequest = new StringRequest(Request.Method.GET, "https://hacker-news.firebaseio.com/v0/item/" + kidId + ".json",
                                                                        kidResponse -> {
                                                                            try {
                                                                                Utils.log("Loaded kid " + kidId);
                                                                                //this should be the reply we are interested in
                                                                                Story reply = new Story();
                                                                                //let's check that it parses
                                                                                if (JSONParser.updateStoryWithHNJson(kidResponse, reply)) {
                                                                                    Utils.log("... and parsed it!");
                                                                                    //it does, let's add this to the list of things we want to notify about
                                                                                    //provided it is not by the user
                                                                                    if (!reply.by.equals(username)) {
                                                                                        Utils.log("It was not by the user, let's notify");
                                                                                        addToNotifyList(ctx, reply);
                                                                                    }
                                                                                }
                                                                            } catch (JSONException e) {
                                                                                e.printStackTrace();
                                                                            }
                                                                        }, Throwable::printStackTrace);

                                                                queue.add(kidRequest);
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    //if parsing fails, comment might be deleted, let's mark inactive
                                                    markInactive(ctx, id);
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }, Throwable::printStackTrace);

                                queue.add(submissionRequest);
                            }
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }

                }, Throwable::printStackTrace);

        queue.add(userRequest);
    }

    /*
    * Takes a list of all the user submissions ids as input and returns a filtered list
    * after removing all ids which have been marked as "old"*/
    private static Set<Integer> getActiveIds(Context ctx, int[] allSubmitted) {
        Set<Integer> inactiveIds =  SettingsUtils.readIntSetFromSharedPreferences(ctx, REPLY_INACTIVE_IDS_KEY);
        Set<Integer> filtered = new HashSet<>();

        for (int id : allSubmitted) {
            if (!inactiveIds.contains(id)) {
                filtered.add(id);
            }
        }

        return filtered;
    }

    /*
    * Adds an ID to the list of old IDS so we can filter it out in the future
    * */
    private static void markInactive(Context ctx, int id) {
        Set<Integer> inactiveIds =  SettingsUtils.readIntSetFromSharedPreferences(ctx, REPLY_INACTIVE_IDS_KEY);
        inactiveIds.add(id);
        SettingsUtils.saveIntSetToSharedPreferences(ctx, REPLY_INACTIVE_IDS_KEY, inactiveIds);
    }

    /*
    * Checks the list of items already notified for and says if we're in it
    * */
    private static boolean isInactive(Context ctx, int kidId) {
        Set<Integer> inactiveIds =  SettingsUtils.readIntSetFromSharedPreferences(ctx, REPLY_INACTIVE_IDS_KEY);
        return inactiveIds.contains(kidId);
    }

    /*
    * The replies roll in at different times so we gather them all here
    * */
    private static void addToNotifyList(Context ctx, Story reply) {

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(ctx, CommentsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_api)
                .setContentTitle("My notification")
                .setContentText("Hello World!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);



        //notificationManager.notify(0, builder.build());
    }

    public static boolean notificationsAreActive(Context ctx) {
        return true;
    }

    public static void setupNotifications(Context ctx) {

    }

    public static void createNotificationChannel(Activity activity) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Replies";
            String description = "Replies to both comments and stories you have posted";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = activity.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

}
