package de.lazytv.pro.stb;
/** Independent channel selection fence; old link/EPG/logo callbacks cannot mutate current playback. */
public final class StbGeneration {
 private long value;private boolean closed;
 public synchronized long next(){return ++value;}
 public synchronized boolean accepts(long ticket){return !closed&&ticket==value;}
 public synchronized void close(){closed=true;value++;}
}
