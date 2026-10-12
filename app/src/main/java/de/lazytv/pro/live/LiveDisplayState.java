package de.lazytv.pro.live;
/** UI state only: opening a browser never creates/resolves/releases a player. */
final class LiveDisplayState {
 enum Mode { PREVIEW, FULLSCREEN, BROWSER }
 private Mode mode=Mode.PREVIEW;private boolean controls;
 boolean controls(){return controls;}
 boolean showControls(){if(mode!=Mode.FULLSCREEN)return false;controls=true;return true;}
 boolean hideControls(){boolean visible=controls;controls=false;return visible;}
 Mode mode(){return mode;}
 void fullscreen(boolean value){controls=false;mode=value?Mode.FULLSCREEN:Mode.PREVIEW;}
 boolean openBrowser(){if(mode!=Mode.FULLSCREEN)return false;controls=false;mode=Mode.BROWSER;return true;}
 boolean closeBrowser(){if(mode!=Mode.BROWSER)return false;controls=false;mode=Mode.FULLSCREEN;return true;}
}
