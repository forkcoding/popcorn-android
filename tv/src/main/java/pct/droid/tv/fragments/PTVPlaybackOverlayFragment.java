/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package pct.droid.tv.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.app.PlaybackOverlaySupportFragment;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRow.ClosedCaptioningAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.FastForwardAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.MoreActions;
import android.support.v17.leanback.widget.PlaybackControlsRow.PlayPauseAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.RewindAction;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Timer;
import java.util.TimerTask;

import de.greenrobot.event.EventBus;
import pct.droid.base.providers.media.models.Media;
import pct.droid.tv.R;
import pct.droid.tv.events.PausePlaybackEvent;
import pct.droid.tv.events.ScaleVideoEvent;
import pct.droid.tv.events.SeekBackwardEvent;
import pct.droid.tv.events.SeekForwardEvent;
import pct.droid.tv.events.StartPlaybackEvent;

/*
 * Class for video playback with media control
 */
public class PTVPlaybackOverlayFragment extends PlaybackOverlaySupportFragment {
    private static final String TAG = "PlaybackOverlayFragment";
    private static final boolean SHOW_DETAIL = true;
    private static final boolean HIDE_MORE_ACTIONS = false;
    private static final int BACKGROUND_TYPE = PlaybackOverlayFragment.BG_LIGHT;
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 240;
    private static final int DEFAULT_UPDATE_PERIOD = 1000;
    private static final int UPDATE_PERIOD = 16;
    private static final int SIMULATED_BUFFERED_TIME = 10000;
    private static final int CLICK_TRACKING_DELAY = 1000;
    private static final int INITIAL_SPEED = 10000;

    private final Handler mClickTrackingHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mPrimaryActionsAdapter;
    private ArrayObjectAdapter mSecondaryActionsAdapter;
    private ClosedCaptioningAction mClosedCaptioningAction;
    private FastForwardAction mFastForwardAction;
    private ScaleVideoAction mScaleVideoAction;
    private MoreActions mMoreActions;
    private PlayPauseAction mPlayPauseAction;
    private RewindAction mRewindAction;
    private PlaybackControlsRow mPlaybackControlsRow;
    private int mCurrentItem;
    private Handler mHandler;
    private Runnable mRunnable;
    private Media mSelectedMedia;
    private int mFfwRwdSpeed = INITIAL_SPEED;
    private Timer mClickTrackingTimer;
    private int mClickCount;

    private int mCurrentPlaybackState;
    private PlaybackOverlayCallback mListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        mSelectedMedia = mListener.getMedia();

        if (null!=mSelectedMedia){
            updateVideoImage(mSelectedMedia.image);
        }

        mHandler = new Handler();

        setBackgroundType(BACKGROUND_TYPE);
        setFadingEnabled(false);

        setupRows();

        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                       RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemSelected: " + item + " row " + row);
            }
        });
//        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mListener = (PlaybackOverlayCallback) activity;
    }

    @Override
    public void onStop() {
        stopProgressAutomation();
        mRowsAdapter = null;
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    public void onEvent(Object event){

    }

    private void setupRows() {
        ClassPresenterSelector ps = new ClassPresenterSelector();

        PlaybackControlsRowPresenter playbackControlsRowPresenter;

        if (SHOW_DETAIL) {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter(
                    new DescriptionPresenter());
        } else {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter();
        }

        playbackControlsRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            public void onActionClicked(Action action) {
                if (action.getId() == mPlayPauseAction.getId()) {
                    togglePlayback(mPlayPauseAction.getIndex() == PlayPauseAction.PLAY);
                } else if (action.getId() == mFastForwardAction.getId()) {
                    fastForward();
                } else if (action.getId() == mRewindAction.getId()) {
                    fastRewind();
                } else if (action.getId() == mScaleVideoAction.getId()) {
                    scaleVideo();
                }
                if (action instanceof PlaybackControlsRow.MultiAction) {
                    notifyChanged(action);
                }
            }
        });

        playbackControlsRowPresenter.setSecondaryActionsHidden(HIDE_MORE_ACTIONS);

        ps.addClassPresenter(PlaybackControlsRow.class, playbackControlsRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(ps);

        addPlaybackControlsRow();

        setAdapter(mRowsAdapter);
    }

    public void togglePlayback(boolean play) {
        if (play) {
            EventBus.getDefault().post(new StartPlaybackEvent());
        } else {
            EventBus.getDefault().post(new PausePlaybackEvent());
        }
    }

    private void addPlaybackControlsRow() {
        if (SHOW_DETAIL) {
            mPlaybackControlsRow = new PlaybackControlsRow(mSelectedMedia);
        } else {
            mPlaybackControlsRow = new PlaybackControlsRow();
        }
        mRowsAdapter.add(mPlaybackControlsRow);

        updatePlaybackRow();

        ControlButtonPresenterSelector presenterSelector = new ControlButtonPresenterSelector();
        mPrimaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mSecondaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mPlaybackControlsRow.setPrimaryActionsAdapter(mPrimaryActionsAdapter);
        mPlaybackControlsRow.setSecondaryActionsAdapter(mSecondaryActionsAdapter);

        Activity activity = getActivity();
        mPlayPauseAction = new PlayPauseAction(activity);
        mFastForwardAction = new FastForwardAction(activity);
        mRewindAction = new RewindAction(activity);
        mScaleVideoAction = new ScaleVideoAction(activity);
        mClosedCaptioningAction = new ClosedCaptioningAction(activity);
        mMoreActions = new MoreActions(activity);

        // Add main controls to primary adapter.
        mPrimaryActionsAdapter.add(mRewindAction);
        mPrimaryActionsAdapter.add(mPlayPauseAction);
        mPrimaryActionsAdapter.add(mFastForwardAction);

        // Add rest of controls to secondary adapter.
        mSecondaryActionsAdapter.add(mScaleVideoAction);
        mSecondaryActionsAdapter.add(mClosedCaptioningAction);
        mSecondaryActionsAdapter.add(mMoreActions);
    }

    private void notifyChanged(Action action) {
        ArrayObjectAdapter adapter = mPrimaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
        adapter = mSecondaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
    }

    private void updatePlaybackRow() {
        mPlaybackControlsRow.setCurrentTime(0);
        mPlaybackControlsRow.setBufferedProgress(0);
        mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
    }

    private int getUpdatePeriod() {
        if (getView() == null || mPlaybackControlsRow.getTotalTime() <= 0) {
            return DEFAULT_UPDATE_PERIOD;
        }
        return Math.max(UPDATE_PERIOD, mPlaybackControlsRow.getTotalTime() / getView().getWidth());
    }

    private void startProgressAutomation() {
        if (mRunnable == null) {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    int updatePeriod = getUpdatePeriod();
                    int currentTime = mPlaybackControlsRow.getCurrentTime() + updatePeriod;
                    int totalTime = mPlaybackControlsRow.getTotalTime();
                    mPlaybackControlsRow.setCurrentTime(currentTime);
                    mPlaybackControlsRow.setBufferedProgress(currentTime + SIMULATED_BUFFERED_TIME);

                    if (totalTime > 0 && totalTime <= currentTime) {
                        stopProgressAutomation();
//                        next(true);
                    } else {
                        mHandler.postDelayed(this, updatePeriod);
                    }
                }
            };
            mHandler.postDelayed(mRunnable, getUpdatePeriod());
        }
    }

    private void fastForward() {
        startClickTrackingTimer();

        EventBus.getDefault().post(new SeekForwardEvent());
    }

    private void fastRewind() {
        startClickTrackingTimer();
        EventBus.getDefault().post(new SeekBackwardEvent());
    }

    private void scaleVideo() {
        EventBus.getDefault().post(new ScaleVideoEvent());
    }

    private void stopProgressAutomation() {
        if (mHandler != null && mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
            mRunnable = null;
        }
    }

    protected void updateVideoImage(String uri) {
        Picasso.with(getActivity())
                .load(uri)
                .centerCrop()
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        mPlaybackControlsRow.setImageBitmap(getActivity(),bitmap);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {

                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                });
    }

    private void startClickTrackingTimer() {
        if (null != mClickTrackingTimer) {
            mClickCount++;
            mClickTrackingTimer.cancel();
        } else {
            mClickCount = 0;
            mFfwRwdSpeed = INITIAL_SPEED;
        }
        mClickTrackingTimer = new Timer();
        mClickTrackingTimer.schedule(new UpdateFfwRwdSpeedTask(), CLICK_TRACKING_DELAY);
    }

    static class DescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            viewHolder.getTitle().setText(((Media) item).title);
            viewHolder.getSubtitle().setText(((Media) item).imdbId);
        }
    }

    private class UpdateFfwRwdSpeedTask extends TimerTask {

        @Override
        public void run() {
            mClickTrackingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mClickCount == 0) {
                        mFfwRwdSpeed = INITIAL_SPEED;
                    } else if (mClickCount == 1) {
                        mFfwRwdSpeed *= 2;
                    } else if (mClickCount >= 2) {
                        mFfwRwdSpeed *= 4;
                    }
                    mClickCount = 0;
                    mClickTrackingTimer = null;
                }
            });
        }
    }

    public interface PlaybackOverlayCallback {
        Media getMedia();
    }

    public static class ScaleVideoAction extends Action {
        public ScaleVideoAction(Context context) {
            super(R.id.control_scale);

            setIcon(context.getResources().getDrawable(R.drawable.ic_av_aspect_ratio));
            setLabel1(context.getString(R.string.scale));
        }
    }

}
