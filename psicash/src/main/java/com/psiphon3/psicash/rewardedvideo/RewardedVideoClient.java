package com.psiphon3.psicash.rewardedvideo;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.mopub.common.MoPub;
import com.mopub.common.MoPubReward;
import com.mopub.common.SdkConfiguration;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideoListener;
import com.mopub.mobileads.MoPubRewardedVideos;
import com.psiphon3.psicash.util.TunnelConnectionStatus;
import com.psiphon3.psicash.psicash.PsiCashClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class RewardedVideoClient {
    private Context context;
    // TODO: replace test values
    // Test video
    public static final String MOPUB_VIDEO_AD_UNIT_ID = "15173ac6d3e54c9389b9a5ddca69b34b";

    private static final String TAG = "PsiCashRewardedVideo";
    //Test video
    private static final String ADMOB_VIDEO_AD_ID = "ca-app-pub-3940256099942544/5224354917";

    private static RewardedVideoClient INSTANCE = null;
    private RewardedVideoAd rewardedVideoAd = null;

    private RewardedVideoClient(Context context) {
        this.context = context;
    }

    public static synchronized RewardedVideoClient getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new RewardedVideoClient(context);
        }
        return INSTANCE;
    }

    private Observable<? extends RewardedVideoModel> loadMoPubVideos(final String customData) {
        return Observable.<RewardedVideoModel>create(emitter -> {
            final int rewardAmount;
            final Set<MoPubReward> rewardsSet = MoPubRewardedVideos.getAvailableRewards(MOPUB_VIDEO_AD_UNIT_ID);

            // Get first value
            if (rewardsSet.iterator().hasNext()) {
                rewardAmount = rewardsSet.iterator().next().getAmount();
            } else {
                rewardAmount = 0;
            }


            MoPubRewardedVideoListener rewardedVideoListener = new MoPubRewardedVideoListener() {
                @Override
                public void onRewardedVideoLoadSuccess(String adUnitId) {
                    if (!emitter.isDisposed()) {
                        // Called when the video for the given adUnitId has loaded. At this point you should be able to call MoPubRewardedVideos.showRewardedVideo(String) to show the video.
                        if (adUnitId == MOPUB_VIDEO_AD_UNIT_ID) {
                            emitter.onNext(RewardedVideoModel.VideoReady.create(() ->
                                    RewardedVideoClient.getInstance(context).playMoPubVideo(customData)));
                        } else {
                            emitter.onError(new RuntimeException("MoPub video failed, wrong ad unit id, expect: " + MOPUB_VIDEO_AD_UNIT_ID + ", got: " + adUnitId));
                        }
                    }
                }

                @Override
                public void onRewardedVideoLoadFailure(String adUnitId, MoPubErrorCode errorCode) {
                    if (!emitter.isDisposed()) {
                        emitter.onError(new RuntimeException("MoPub video failed with error: " + errorCode.toString()));
                    }
                }

                @Override
                public void onRewardedVideoStarted(String adUnitId) {
                }

                @Override
                public void onRewardedVideoPlaybackError(String adUnitId, MoPubErrorCode errorCode) {
                }

                @Override
                public void onRewardedVideoClicked(@NonNull String adUnitId) {
                }

                @Override
                public void onRewardedVideoClosed(String adUnitId) {
                    if(!emitter.isDisposed()) {
                        emitter.onNext(RewardedVideoModel.VideoClosed.create());
                    }
                }

                @Override
                public void onRewardedVideoCompleted(Set<String> adUnitIds, MoPubReward reward) {
                    // TODO We may reward in the onRewardedVideoClosed instead?
                    // since MoPub videos are not closeable
                    // check https://developers.mopub.com/docs/ui/apps/rewarded-server-side-setup/ for the web hook docs
                    if (!emitter.isDisposed()) {
                        PsiCashClient.getInstance(context).putVideoReward(reward.getAmount());
                        emitter.onNext(RewardedVideoModel.Reward.create(reward.getAmount()));
                    }
                }
            };

            MoPubRewardedVideos.setRewardedVideoListener(rewardedVideoListener);
            MoPubRewardedVideos.loadRewardedVideo(MOPUB_VIDEO_AD_UNIT_ID);
        });
    }

    Observable<? extends RewardedVideoModel> loadAdMobVideos(String customData) {
        return Observable.<RewardedVideoModel>create(emitter -> {
            RewardedVideoAdListener listener = new RewardedVideoAdListener() {
                @Override
                public void onRewarded(RewardItem reward) {
                    if (!emitter.isDisposed()) {
                        PsiCashClient.getInstance(context).putVideoReward(reward.getAmount());
                        emitter.onNext(RewardedVideoModel.Reward.create(reward.getAmount()));
                    }
                }

                @Override
                public void onRewardedVideoAdLeftApplication() {
                }

                @Override
                public void onRewardedVideoAdClosed() {
                    if(!emitter.isDisposed()) {
                        emitter.onNext(RewardedVideoModel.VideoClosed.create());
                    }
                }

                @Override
                public void onRewardedVideoAdFailedToLoad(int errorCode) {
                    if (!emitter.isDisposed()) {
                        emitter.onError(new RuntimeException("AdMob video ad failed with code: " + errorCode));
                    }
                }

                @Override
                public void onRewardedVideoAdLoaded() {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(RewardedVideoModel.VideoReady.create(() ->
                                RewardedVideoClient.getInstance(context).playAdMobVideo()));
                    }
                }

                @Override
                public void onRewardedVideoAdOpened() {
                }

                @Override
                public void onRewardedVideoStarted() {
                }

                @Override
                public void onRewardedVideoCompleted() {
                    Log.d(TAG, "onRewardedVideoCompleted");
                }
            };

            rewardedVideoAd.setCustomData(customData);
            rewardedVideoAd.setRewardedVideoAdListener(listener);
            rewardedVideoAd.loadAd(ADMOB_VIDEO_AD_ID, new AdRequest.Builder()
                    .build());

        });
    }


    public Observable<? extends RewardedVideoModel> loadRewardedVideo(TunnelConnectionStatus status) {
        if (!PsiCashClient.getInstance(context).hasEarnerToken()) {
            return Observable.error(new IllegalStateException("PsiCash lib has no earner token"));
        }

        String customData = PsiCashClient.getInstance(context).rewardedVideoCustomData();

        Observable<? extends RewardedVideoModel> videoLoadObservable;

        if (status == TunnelConnectionStatus.DISCONNECTED) {
            videoLoadObservable = loadAdMobVideos(customData);
        } else if (status == TunnelConnectionStatus.CONNECTED) {
            videoLoadObservable = loadMoPubVideos(customData);
        } else {
            throw new IllegalArgumentException("Loading video for " + status + " is not implemented");
        }

        return videoLoadObservable
                .subscribeOn(AndroidSchedulers.mainThread())
                // if error retry once with 2 seconds delay and complete
                .retryWhen(throwableObservable -> {
                    AtomicInteger counter = new AtomicInteger();
                    return throwableObservable
                            .takeWhile(e -> counter.getAndIncrement() < 1)
                            .flatMap(err -> {
                                Log.d(TAG, "Ad loading error:" + err);
                                return Observable.timer(2, TimeUnit.SECONDS);
                            });
                });
    }

    public void initAdsWithActivity(Activity activity, String admobId) {
        // MoPub init
        if (!MoPub.isSdkInitialized()) {
            SdkConfiguration sdkConfiguration;
            List<String> networksToInit = new ArrayList<String>();
            // TODO: networks and mediation settings
//                    networksToInit.add("com.mopub.mobileads.VungleRewardedVideo");
            sdkConfiguration = new SdkConfiguration.Builder(RewardedVideoClient.MOPUB_VIDEO_AD_UNIT_ID)
//                            .withMediationSettings("MEDIATION_SETTINGS")
                    .withNetworksToInit(networksToInit)
                    .build();
            MoPub.initializeSdk(activity, sdkConfiguration, () -> {
            });
        }

        if (rewardedVideoAd == null) {
            MobileAds.initialize(activity, admobId);
            rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);
        }
    }

    public void playAdMobVideo() {
        if (rewardedVideoAd != null) {
            rewardedVideoAd.show();
        }
    }

    public void playMoPubVideo(String customData) {
        if (MoPub.isSdkInitialized()) {
            MoPubRewardedVideos.showRewardedVideo(MOPUB_VIDEO_AD_UNIT_ID, customData);
        }
    }

    // Forwarded activity lifecycle methods
    public void onCreate(Activity activity) {
        MoPub.onCreate(activity);
    }

    public void onPause(Activity activity) {
        MoPub.onPause(activity);
        if (rewardedVideoAd != null) {
            rewardedVideoAd.pause(activity);
        }

    }

    public void onStop(Activity activity) {
        MoPub.onStop(activity);
    }

    public void onResume(Activity activity) {
        MoPub.onResume(activity);
        if (rewardedVideoAd != null) {
            rewardedVideoAd.resume(activity);
        }
    }

    public void onStart(Activity activity) {
        MoPub.onStart(activity);
    }

    public void onRestart(Activity activity) {
        MoPub.onRestart(activity);
    }

    public void onDestroy(Activity activity) {
        MoPub.onDestroy(activity);
    }

    public void onBackPressed(Activity activity) {
        MoPub.onBackPressed(activity);
    }
}
