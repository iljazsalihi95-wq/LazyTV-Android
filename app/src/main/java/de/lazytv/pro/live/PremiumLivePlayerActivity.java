package de.lazytv.pro.live;
/** Dedicated Premium entry; session and link recovery belong to LiveSources.Premium. */
public final class PremiumLivePlayerActivity extends LiveTvActivity {
 @Override protected String requiredSourceScope(){return "SERVER_10";}
}
