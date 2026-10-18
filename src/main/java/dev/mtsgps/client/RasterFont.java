package dev.mtsgps.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Locale;

/** Small legible bitmap font, avoiding offscreen framebuffer and GL state changes. */
final class RasterFont {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:-+/!?()<>=";
    private static final String[] GLYPHS = {
        "0E111F1111","1E111E111E","0F1010100F","1E1111111E","1F101E101F","1F101E1010",
        "0F1013110F","11111F1111","1F0404041F","010101110E","11121C1211","101010101F",
        "111B151111","1119151311","0E1111110E","1E111E1010","0E1111150B","1E111E1211",
        "0F100E011E","1F04040404","111111110E","1111110A04","1111151B11","110A040A11",
        "110A040404","1F0204081F","0E1315190E","040C04040E","1E010E101F","1E0106011E",
        "12121F0202","1F101E011E","0E101E110E","1F01020408","0E110E110E","0E110F010E",
        "0000000000","0000000004","0000000408","0004000400","00001F0000","00041F0400",
        "0102040810","0E01060004","0404040004","0204040402","0804040408","0204080402",
        "0804020408","001F001F00"
    };
    static void text(NativeImage image,String value,int x,int y,int color,int scale) {
        int start=x;
        for (char c : value.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c=='\n') { y+=7*scale; x=start; continue; }
            int index=CHARS.indexOf(c);
            String glyph=GLYPHS[index<0?CHARS.indexOf('?'):index];
            for (int row=0;row<5;row++) {
                int bits=Integer.parseInt(glyph.substring(row*2,row*2+2),16);
                for(int col=0;col<5;col++) if((bits&(1<<(4-col)))!=0)
                    for(int dy=0;dy<scale;dy++) for(int dx=0;dx<scale;dx++)
                        GpsMapTexture.pixel(image,x+col*scale+dx,y+row*scale+dy,color);
            }
            x+=6*scale;
        }
    }
    private RasterFont() {}
}
