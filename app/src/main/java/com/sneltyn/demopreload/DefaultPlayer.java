package com.sneltyn.demopreload;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.RelativeLayout;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.util.Util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class DefaultPlayer
        extends
        RelativeLayout {

    public static final String TAG  = "SNELTYN";

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private Handler mHandler;
    private PlayerView playerView;
    private DefaultDataSourceFactory mMediaDataSourceFactory;
    private SimpleExoPlayer mPlayer;
    private DefaultTrackSelector mTrackSelector;
    private String mUserAgent;
    private PlayerEventListener mPlayerListener;
    private ConcatenatingMediaSource mConcatVideos;

    private boolean ended;

    public DefaultPlayer(Context context) {
        super(context);
        init();
    }

    public DefaultPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DefaultPlayer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_player, this);
        mHandler = new Handler();
        playerView = findViewById(R.id.video_render);
        mUserAgent = Util.getUserAgent(getContext(), "snplayer");
        mMediaDataSourceFactory = new DefaultDataSourceFactory(getContext(),
                mUserAgent, BANDWIDTH_METER);
        mTrackSelector = new DefaultTrackSelector(
                new AdaptiveTrackSelection.Factory(BANDWIDTH_METER));
        mConcatVideos = new ConcatenatingMediaSource();
        mPlayer = ExoPlayerFactory.newSimpleInstance(getContext(), mTrackSelector);
        mPlayerListener = new PlayerEventListener();
        mPlayer.addListener(mPlayerListener);
        playerView.setPlayer(mPlayer);
        playerView.hideController();
        playerView.setUseController(false);
        playerView.requestFocus();
    }


    public void preloadVideo(String path) {
        try {
            MediaSource source = buildMediaSource(Uri.parse(path));
            if(mConcatVideos.getSize() == 0)
                mConcatVideos = new ConcatenatingMediaSource();
            mConcatVideos.addMediaSource(source);
        } catch (Exception ignore) {
           Log.e(TAG, "Problem preload video: " + path);
        }
    }

    public void preloadVideo(String path, int duration) {
        try {
            MediaSource source = buildMediaSource(Uri.parse(path));
            if (mConcatVideos.getSize() == 0)
                mConcatVideos = new ConcatenatingMediaSource();
            mConcatVideos.addMediaSource(new ClippingMediaSource(source, 0,
                    TimeUnit.SECONDS.toMicros(duration)));
        } catch (Exception ignore) {
            Log.e(TAG, "Problem play stream: " + path);
        }
    }

    public void playVideo(int resourceId){
        try {

            DataSpec dataSpec = new  DataSpec(RawResourceDataSource.buildRawResourceUri(resourceId));
            final RawResourceDataSource rawResourceDataSource = new RawResourceDataSource(getContext());
            rawResourceDataSource.open(dataSpec);

            DataSource.Factory factory = new DataSource.Factory() {
                @Override
                public DataSource createDataSource() {
                    return rawResourceDataSource;
                }
            };

            mConcatVideos = new ConcatenatingMediaSource();
            MediaSource source = new ExtractorMediaSource.Factory(factory)
                    .createMediaSource(rawResourceDataSource.getUri());
            mConcatVideos.addMediaSource(source);
            mPlayer.setPlayWhenReady(true);
            mPlayer.prepare(mConcatVideos);
        } catch (Exception ignored) {
            Log.e(TAG, "Problem play video: " + resourceId);
        }
    }

    public void playVideo(String path) {
        try {
            mConcatVideos = new ConcatenatingMediaSource();
            MediaSource source = buildMediaSource(Uri.parse(path));
            mConcatVideos.addMediaSource(source);
            mPlayer.setPlayWhenReady(true);
            mPlayer.prepare(mConcatVideos);
        } catch (Exception ignored) {
            Log.e(TAG, "Problem play video: " + path);
        }
    }

    public void playVideo(String path, int duration) {
        try {
            mConcatVideos = new ConcatenatingMediaSource();
            MediaSource source = buildMediaSource(Uri.parse(path));
            mConcatVideos.addMediaSource(new ClippingMediaSource(source, 0, TimeUnit.SECONDS.toMicros(duration)));
            mPlayer.setPlayWhenReady(true);
            mPlayer.prepare(mConcatVideos);
        } catch (Exception ignored) {
            Log.e(TAG, "Problem play stream: " + path);
        }
    }


    public void stop() {
        mPlayer.stop();
        removeCallback();
    }

    public void release() {
        stop();
        playerView.setPlayer(null);
        mPlayer.removeListener(mPlayerListener);
        mPlayer.release();
        mPlayer = null;
        mTrackSelector = null;
    }

    private MediaSource buildMediaSource(
            Uri uri) {
        int type = Util.inferContentType(uri);
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(
                        new DefaultDashChunkSource.Factory(mMediaDataSourceFactory),
                        buildDataSourceFactory())
                        .createMediaSource(uri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(mMediaDataSourceFactory),
                        buildDataSourceFactory())
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(mMediaDataSourceFactory)
                        .createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource.Factory(mMediaDataSourceFactory)
                        .createMediaSource(uri);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    public void removeCallback() {
        mHandler.removeCallbacksAndMessages(null);
    }

    private DataSource.Factory buildDataSourceFactory() {
        return new DefaultDataSourceFactory(getContext(), null,
                buildHttpDataSourceFactory());
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSourceFactory(mUserAgent, null);
    }

    private class PlayerEventListener extends Player.DefaultEventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == Player.STATE_READY) {
                preloadVideo("https://video-dev.github.io/streams/x36xhzz/x36xhzz.m3u8", 10);
            } else if (playbackState == Player.STATE_ENDED) {
                ended = true;
                Log.d(TAG, "STATE_ENDED");
                mConcatVideos.releaseSourceInternal();
                mConcatVideos.clear();
                stop();
            }
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
            ended = false;
            //super.onPositionDiscontinuity(reason);
            //println(DEBUG, MediaView.class, "WTF? O_o");
            Log.d(TAG, "onPositionDiscontinuity");
            mConcatVideos.removeMediaSource(0);
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {

            error.printStackTrace();

            //super.onPlayerError(error);
            //println(DEBUG, MediaView.class, "ExoPlayer error: %s", error.printStackTrace(););
            //printException(error.fillInStackTrace());
            //mListener.onCompletion();

//            String errorString = null;
//            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
//                Exception cause = e.getRendererException();
//                if (cause instanceof DecoderInitializationException) {
//                    // Special case for decoder initialization failures.
//                    DecoderInitializationException decoderInitializationException =
//                            (DecoderInitializationException) cause;
//                    if (decoderInitializationException.decoderName == null) {
//                        if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
//                            errorString = getString(R.string.error_querying_decoders);
//                        } else if (decoderInitializationException.secureDecoderRequired) {
//                            errorString = getString(R.string.error_no_secure_decoder,
//                                    decoderInitializationException.mimeType);
//                        } else {
//                            errorString = getString(R.string.error_no_decoder,
//                                    decoderInitializationException.mimeType);
//                        }
//                    } else {
//                        errorString = getString(R.string.error_instantiating_decoder,
//                                decoderInitializationException.decoderName);
//                    }
//                }
//            }
//            if (errorString != null) {
//                showToast(errorString);
//            }
//            inErrorState = true;
//            if (isBehindLiveWindow(e)) {
//                clearResumePosition();
//                initializePlayer();
//            } else {
//                updateResumePosition();
//                updateButtonVisibilities();
//                showControls();
//            }
        }
    }
}