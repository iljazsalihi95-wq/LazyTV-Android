package de.lazytv.pro.stb;
/** Native surface/cleanup calls may race a decoder error; failures must not tear down the Activity. */
final class StbNativeCalls {
 private StbNativeCalls(){}
 static boolean run(Runnable operation){try{operation.run();return true;}catch(RuntimeException|LinkageError unavailable){return false;}}
}
