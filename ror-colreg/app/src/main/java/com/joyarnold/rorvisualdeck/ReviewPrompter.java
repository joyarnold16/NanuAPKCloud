package com.joyarnold.rorvisualdeck;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/**
 * Asks Play to show its in-app review card, on a launch where the user has
 * already got some value out of the app.
 *
 * Play owns the decision and the UI: requestReviewFlow may hand back a flow
 * that displays nothing (the user has rated already, or the per-user quota is
 * spent), and there is no callback that says whether a review was left. So
 * "asked" here means "we handed the moment to Play", not "a rating happened".
 *
 * The card only ever appears in a build installed from Play. A debug or
 * sideloaded build fails the request and shows nothing, which is why this
 * cannot be verified by hand off-store.
 */
public final class ReviewPrompter {
    private static final String PREFS = "ror_review";
    private static final String KEY_LAUNCHES = "launch_count";
    private static final String KEY_NEXT_PROMPT_AT = "next_prompt_at";

    /** Launches before the first ask: enough use to have an opinion. */
    private static final int FIRST_PROMPT_AT = 4;
    /** Launches to wait after Play has been given a turn. */
    private static final int GAP_AFTER_SHOWN = 40;
    /** Shorter wait when the request failed outright - offline, say. */
    private static final int GAP_AFTER_FAILURE = 3;
    /**
     * Let the session run before interrupting it. Play's own guidance is to ask
     * after the user has experienced the app, never on top of a cold start.
     */
    private static final long DWELL_MILLIS = 90_000L;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;

    public ReviewPrompter(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Counts this launch and, if it qualifies, arms the dwell timer. */
    public void onLaunch() {
        final int launches = prefs.getInt(KEY_LAUNCHES, 0) + 1;
        prefs.edit().putInt(KEY_LAUNCHES, launches).apply();

        int nextPromptAt = prefs.getInt(KEY_NEXT_PROMPT_AT, FIRST_PROMPT_AT);
        if (launches < nextPromptAt) {
            return;
        }

        pending = () -> {
            pending = null;
            request(launches);
        };
        handler.postDelayed(pending, DWELL_MILLIS);
    }

    /** Drops the timer so a finished Activity is never handed to Play. */
    public void cancel() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
    }

    private void request(int launches) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnCompleteListener(request -> {
            if (!request.isSuccessful()) {
                backOff(launches, GAP_AFTER_FAILURE);
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ReviewInfo info = request.getResult();
            manager.launchReviewFlow(activity, info)
                    .addOnCompleteListener(flow -> backOff(launches, GAP_AFTER_SHOWN));
        });
    }

    private void backOff(int launches, int gap) {
        prefs.edit().putInt(KEY_NEXT_PROMPT_AT, launches + gap).apply();
    }
}
