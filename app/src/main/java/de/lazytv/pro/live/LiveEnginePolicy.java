package de.lazytv.pro.live;
/** Authentication/network errors belong to the provider resolver, not another decoder. */
final class LiveEnginePolicy {
 static boolean nativeFallback(int mediaError,int httpStatus,boolean tried){return !tried&&httpStatus==0&&(mediaError>=3000&&mediaError<4000||mediaError>=4000&&mediaError<=4005);}
}
