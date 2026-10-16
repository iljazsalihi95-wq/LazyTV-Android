package de.lazytv.pro.stb;

import java.util.*;

/** A finite engine sequence per explicit channel selection. No fallback can wrap to the start. */
public final class StbEnginePolicy {
    public enum Engine { AUTO, MEDIA3, VLC, IJK, ANDROID }
    private List<Engine> sequence=Collections.emptyList();
    private int index;
    public void reset(Engine selected){sequence=selected==Engine.AUTO?Arrays.asList(Engine.MEDIA3,Engine.VLC,Engine.IJK,Engine.ANDROID):Collections.singletonList(selected);index=0;}
    public Engine next(){return index<sequence.size()?sequence.get(index++):null;}
}
