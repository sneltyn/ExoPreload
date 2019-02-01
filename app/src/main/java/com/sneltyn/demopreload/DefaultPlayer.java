package com.sneltyn.demopreload;

import android.content.Context;
import android.media.session.PlaybackState;
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
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class DefaultPlayer
    extends
    RelativeLayout {

  public static final String TAG = "SNELTYN";

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

  private final ArrayList<String> urls = new ArrayList<>();
  private int k = 0;
  private Handler mHandler;
  private PlayerView playerView;
  private DefaultDataSourceFactory mDataSourceFactory;
  private SimpleExoPlayer mPlayer;
  private String mUserAgent;
  private ConcatenatingMediaSource mConcatVideos;
  private final Deque<String> mQue = new LinkedList<>();

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

    DataSpec dataSpec = new DataSpec(RawResourceDataSource.buildRawResourceUri(R.raw.test));
    final RawResourceDataSource rawResourceDataSource = new RawResourceDataSource(getContext());
    try {
      rawResourceDataSource.open(dataSpec);
      urls.add(rawResourceDataSource.getUri().toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
    urls.add("https://video-dev.github.io/streams/x36xhzz/x36xhzz.m3u8");
    mUserAgent = Util.getUserAgent(getContext(), "snplayer");
    mDataSourceFactory = new DefaultDataSourceFactory(getContext(),
        mUserAgent, BANDWIDTH_METER);
    mConcatVideos = new ConcatenatingMediaSource();
    initializePlayer();
  }

  public void nextPlay() {
    playVideo(urls.get(k++), 10);
    k %= urls.size();
  }

  public void playVideo(int resourceId) {
    try {

      DataSpec dataSpec = new DataSpec(RawResourceDataSource.buildRawResourceUri(resourceId));
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
      Log.d(TAG, "Problem play video: " + resourceId);
    }
  }

  public void preloadVideo(String path, double duration) {
    if (mConcatVideos.getSize() == 0) return;
    Log.d(TAG, "Preload video: " + path);
    try {
      MediaSource source = buildMediaSource(Uri.parse(path));
      int seconds = (int) duration;
      int millis = (int) ((duration - seconds) * 1000) + seconds * 1000;
      mConcatVideos.addMediaSource(new ClippingMediaSource(source, 0,
          TimeUnit.MILLISECONDS.toMicros(millis)));
      mQue.add(path);
    } catch (Exception ignore) {
      Log.d(TAG, "Problem preload video: " + path);
    }
  }

  public void playVideo(String path, double duration) {
    Log.d(TAG, "Play video: " + path);
    if (mConcatVideos.getSize() > 0
        && path.equals(mQue.peek())
        && mPlayer.getPlaybackState() == PlaybackState.STATE_PLAYING) {
      Log.d(TAG, "PRELOADED VIDEO");
      return;
    }

    try {
      Log.d(TAG, "WITHOUT PRELOAD");
      mQue.clear();
      mConcatVideos.clear();
      MediaSource source = buildMediaSource(Uri.parse(path));
      int seconds = (int) duration;
      int millis = (int) ((duration - seconds) * 1000) + seconds * 1000;
      mConcatVideos.addMediaSource(
          new ClippingMediaSource(source, 0, TimeUnit.MILLISECONDS.toMicros(millis)));
      mQue.add(path);
      mPlayer.setVolume(1);
      mPlayer.prepare(mConcatVideos);
      mPlayer.setPlayWhenReady(true);
    } catch (Exception ex) {
      Log.d(TAG, "Problem preload video: " + path);
    }
  }

  private void initializePlayer() {
    if (mPlayer != null) return;
    mPlayer = ExoPlayerFactory.newSimpleInstance(getContext());
    mConcatVideos = new ConcatenatingMediaSource();
    mPlayer.addListener(new PlayerEventListener());
    playerView.setPlayer(mPlayer);
    playerView.hideController();
    playerView.setUseController(false);
    playerView.requestFocus();
  }

  private void releasePlayer() {
    if (mPlayer == null) return;
    removeCallback();
    mPlayer.release();
    mPlayer = null;
    mConcatVideos = null;
    mQue.clear();
  }

  private MediaSource buildMediaSource(Uri uri) {
    int type = Util.inferContentType(uri);
    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(mDataSourceFactory).createMediaSource(uri);
      case C.TYPE_SS:
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(mDataSourceFactory),
            buildDataSourceFactory())
            .createMediaSource(uri);
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(mDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(uri);
      case C.TYPE_OTHER:
        return new ExtractorMediaSource.Factory(mDataSourceFactory).createMediaSource(uri);
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

  private void nextPreload() {
    preloadVideo(urls.get(k++), 10);
    k %= urls.size();
  }

  private HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSourceFactory(mUserAgent, null);
  }

  private class PlayerEventListener implements Player.EventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

      Log.d(TAG, "STATE playWhenReady = " + playWhenReady + ", "
          + "playbackState = " + playbackState);

      if (playbackState == Player.STATE_READY) {
        nextPreload();
      } else if (playbackState == Player.STATE_ENDED) {
        Log.d(TAG, "STATE_ENDED");
        nextPlay();
      }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
      //super.onPositionDiscontinuity(reason);
      //println(DEBUG, MediaView.class, "WTF? O_o");
      Log.d(TAG, "onPositionDiscontinuity");
      if (mConcatVideos.getSize() > 0) {
        mConcatVideos.removeMediaSource(0);
        mQue.remove();
      }
      nextPreload();
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

      error.printStackTrace();
      mConcatVideos.clear();
      mQue.clear();
      
      //releasePlayer();
      //initializePlayer();
      nextPlay();
    }
  }
}