package de.lazytv.pro.stb;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.widget.Button;
final class StbUi {
 static void insets(android.view.View root,int padding){root.setPadding(padding,padding,padding,padding);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(padding+i.getSystemWindowInsetLeft(),padding+i.getSystemWindowInsetTop(),padding+i.getSystemWindowInsetRight(),padding+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();}
 static StateListDrawable focus(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},box(0xff12648b,0xffffd452));s.addState(new int[]{android.R.attr.state_selected},box(0xff17425b,0xff59d5ff));s.addState(new int[]{},box(0xff102b40,0xff31536a));return s;}
 private static GradientDrawable box(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(8);d.setStroke(3,stroke);return d;}
 static Button button(Context c,String text){Button b=new Button(c);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setFocusable(true);b.setBackground(focus());return b;}
}
