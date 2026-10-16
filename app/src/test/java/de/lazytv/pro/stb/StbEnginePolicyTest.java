package de.lazytv.pro.stb;
import org.junit.Test;import static org.junit.Assert.*;import de.lazytv.pro.stb.StbEnginePolicy.Engine;
public class StbEnginePolicyTest {
 @Test public void autoTriesAllEnginesOnceAndStops(){StbEnginePolicy p=new StbEnginePolicy();p.reset(Engine.AUTO);for(Engine expected:new Engine[]{Engine.MEDIA3,Engine.VLC,Engine.IJK,Engine.ANDROID})assertEquals(expected,p.next());for(int i=0;i<10;i++)assertNull(p.next());}
 @Test public void manualEngineIsRespected(){for(Engine engine:Engine.values()){if(engine==Engine.AUTO)continue;StbEnginePolicy p=new StbEnginePolicy();p.reset(engine);assertEquals(engine,p.next());assertNull(p.next());}}
 @Test public void nextChannelGetsFreshBudget(){StbEnginePolicy p=new StbEnginePolicy();p.reset(Engine.AUTO);p.next();p.next();p.reset(Engine.AUTO);assertEquals(Engine.MEDIA3,p.next());}
}
