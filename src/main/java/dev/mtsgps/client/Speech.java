package dev.mtsgps.client;

import com.mojang.text2speech.Narrator;
import dev.mtsgps.GpsConfig;
import dev.mtsgps.MtsGps;

/** Dedicated local narrator instance; independent of Minecraft's accessibility narrator setting. */
final class Speech {
    private Narrator narrator;
    private boolean attempted;
    boolean available() { ensure(); return narrator!=null && narrator.active(); }
    private void ensure() {
        if(attempted) return;
        attempted=true;
        try { narrator=Narrator.getNarrator(); }
        catch(Throwable failure) { MtsGps.LOGGER.warn("MTS GPS speech initialization failed",failure); }
    }
    void say(String text) {
        if(!GpsConfig.SPEECH.get()) return;
        if(available()) {
            try { narrator.say(text,true); }
            catch(Throwable failure) { MtsGps.LOGGER.warn("MTS GPS speech failed",failure); narrator=null; }
        }
    }
    void clear() { if(narrator!=null) narrator.clear(); }
    void close() { if(narrator!=null) narrator.destroy(); narrator=null; attempted=false; }
}
