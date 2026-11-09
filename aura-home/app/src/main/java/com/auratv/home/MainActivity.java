package com.auratv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  static final int WHITE=0xfff8f7ff, MUTED=0xffaab0c5, PURPLE=0xff9b61ff;
  private LinearLayout sourceRow, appRow;
  private TvInputManager tv;
  private final LinkedHashMap<String,ResolveInfo> allApps=new LinkedHashMap<>();
  private String filter="";
  private final TvInputManager.TvInputCallback callback=new TvInputManager.TvInputCallback(){
    @Override public void onInputAdded(String id){runOnUiThread(()->refreshSources());}
    @Override public void onInputRemoved(String id){runOnUiThread(()->refreshSources());}
    @Override public void onInputStateChanged(String id,int state){runOnUiThread(()->refreshSources());}
  };
  int dp(int d){return (int)(getResources().getDisplayMetrics().density*d+0.5f);}
  LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
  LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  GradientDrawable bg(int a,int b,int radius,int stroke){
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
    d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;
  }
  TextView label(String s,int size,int color,boolean bold){
    TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
    v.setGravity(Gravity.CENTER_VERTICAL);
    if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return v;
  }
  LinearLayout.LayoutParams params(int w,int h,int right,int bottom){
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.rightMargin=dp(right);p.bottomMargin=dp(bottom);return p;
  }
  TextView card(String s,int width,int height, View.OnClickListener action){
    TextView t=label(s,15,WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);
    t.setBackground(bg(0xff252536,0xff171722,15,0xff3e3b55));t.setFocusable(true);
    t.setOnClickListener(action);
    t.setOnFocusChangeListener((view,focused)->{
      t.setBackground(focused?bg(0xff50317c,0xff222036,15,0xffc6a8ff):bg(0xff252536,0xff171722,15,0xff3e3b55));
      t.animate().scaleX(focused?1.04f:1).scaleY(focused?1.04f:1).setDuration(120).start();
    });
    return t;
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    tv=(TvInputManager)getSystemService(Context.TV_INPUT_SERVICE);
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    ScrollView sc=new ScrollView(this);sc.setBackground(bg(0xff0c0d18,0xff111126,0,0));sc.setFillViewport(true);
    LinearLayout base=col();base.setPadding(dp(25),dp(17),dp(25),dp(25));sc.addView(base);
    LinearLayout top=row();
    TextView logo=label("◢ AURA  TV OS",21,WHITE,true);
    top.addView(logo,new LinearLayout.LayoutParams(0,dp(62),1));
    LinearLayout clockWrap=col();clockWrap.setGravity(Gravity.CENTER);
    TextClock clock=new TextClock(this);clock.setFormat24Hour("HH:mm");clock.setTextSize(26);clock.setTextColor(WHITE);clock.setGravity(Gravity.CENTER);
    TextClock date=new TextClock(this);date.setFormat24Hour("dd.MM.yyyy");date.setTextColor(MUTED);date.setTextSize(12);date.setGravity(Gravity.CENTER);
    clockWrap.addView(clock);clockWrap.addView(date);
    top.addView(clockWrap,new LinearLayout.LayoutParams(0,dp(62),1));
    LinearLayout right=row();right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    right.addView(card("⌕ Поиск",92,41,v->search()),params(92,41,8,0));
    right.addView(card("⚙",48,41,v->settings()),params(48,41,0,0));
    top.addView(right,new LinearLayout.LayoutParams(0,dp(62),1));
    base.addView(top,params(-1,65,0,19));
    LinearLayout hero=col();hero.setPadding(dp(25),dp(22),dp(25),dp(22));hero.setBackground(bg(0xff382552,0xff15182b,22,0xff67518c));
    hero.addView(label("AURA · ПРЕДПРОСМОТР",11,0xffbda1ff,true),params(-1,27,0,4));
    hero.addView(label("Твой экран. Твои правила.",27,WHITE,true),params(-1,49,0,4));
    hero.addView(label("Без рекламы и китайских рекомендаций.\nТолько подключённые источники и твои приложения.",15,0xffd7d0e8,false),params(-1,61,0,10));
    LinearLayout actions=row();
    actions.addView(card("Приложения",145,40,v->{if(appRow.getChildCount()>0)appRow.getChildAt(0).requestFocus();}),params(145,40,8,0));
    actions.addView(card("О системе",145,40,v->about()),params(145,40,0,0));
    hero.addView(actions);base.addView(hero,params(-1,220,0,22));
    base.addView(label("Источники",20,WHITE,true),params(-1,32,0,8));
    HorizontalScrollView inputScroll=new HorizontalScrollView(this);inputScroll.setHorizontalScrollBarEnabled(false);
    sourceRow=row();inputScroll.addView(sourceRow);base.addView(inputScroll,params(-1,77,0,16));
    base.addView(label("Мои приложения",20,WHITE,true),params(-1,32,0,8));
    HorizontalScrollView appsScroll=new HorizontalScrollView(this);appsScroll.setHorizontalScrollBarEnabled(false);
    appRow=row();appsScroll.addView(appRow);base.addView(appsScroll,params(-1,79,0,15));
    base.addView(label("AURA Home 0.1 • тестовая оболочка • режим только чтение для HDMI",11,MUTED,false));
    setContentView(sc);
    refreshSources();refreshApps();
  }
  @Override protected void onResume(){
    super.onResume();
    if(tv!=null)try{tv.registerCallback(callback,new Handler(Looper.getMainLooper()));}catch(Exception ignored){}
    refreshSources();refreshApps();
  }
  @Override protected void onPause(){
    if(tv!=null)try{tv.unregisterCallback(callback);}catch(Exception ignored){}
    super.onPause();
  }
  void refreshSources(){
    if(sourceRow==null)return;sourceRow.removeAllViews();int count=0;
    try{
      if(tv!=null)for(TvInputInfo info:tv.getTvInputList()){
        if(info.getType()!=TvInputInfo.TYPE_HDMI)continue;
        if(tv.getInputState(info.getId())!=TvInputManager.INPUT_STATE_CONNECTED)continue;
        String name=String.valueOf(info.loadLabel(this));
        TextView tile=card("▱  "+name,175,65,v->hdmi(info));
        tile.setTag(info);sourceRow.addView(tile,params(175,65,9,0));count++;
      }
    }catch(Exception ignored){}
    if(count==0)sourceRow.addView(label("Подтверждённых активных HDMI сейчас нет",14,MUTED,false),params(-1,65,0,0));
  }
  void refreshApps(){
    if(appRow==null)return;appRow.removeAllViews();allApps.clear();
    PackageManager pm=getPackageManager();
    Intent it=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
    List<ResolveInfo> available=pm.queryIntentActivities(it,0);
    available.sort((a,b)->String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
    for(ResolveInfo app:available){
      if(app.activityInfo==null)continue;
      String pkg=app.activityInfo.packageName;
      if(pkg.equals(getPackageName())||pkg.startsWith("com.mitv.")||pkg.startsWith("com.xiaomi."))continue;
      String name=String.valueOf(app.loadLabel(pm));
      if(!filter.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(filter))continue;
      allApps.putIfAbsent(pkg,app);
    }
    int n=0;
    for(ResolveInfo app:allApps.values()){
      if(n++>=8)break;
      appRow.addView(card(String.valueOf(app.loadLabel(pm)),154,66,v->openApp(app)),params(154,66,9,0));
    }
    if(n==0)appRow.addView(label("Приложения по запросу не найдены",14,MUTED,false),params(-2,66,9,0));
    appRow.addView(card("▦ Ещё",110,66,v->allApps()),params(110,66,0,0));
  }
  void openApp(ResolveInfo app){
    try{Intent it=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
      it.setClassName(app.activityInfo.packageName,app.activityInfo.name);startActivity(it);
    }catch(Exception e){Toast.makeText(this,"Ошибка запуска приложения",Toast.LENGTH_SHORT).show();}
  }
  void allApps(){
    ArrayList<ResolveInfo> list=new ArrayList<>(allApps.values());String[] names=new String[list.size()];
    for(int i=0;i<list.size();i++)names[i]=String.valueOf(list.get(i).loadLabel(getPackageManager()));
    new AlertDialog.Builder(this).setTitle("Приложения").setItems(names,(d,n)->openApp(list.get(n))).setNegativeButton("Закрыть",null).show();
  }
  void search(){
    EditText edit=new EditText(this);edit.setSingleLine(true);edit.setHint("Название приложения");
    new AlertDialog.Builder(this).setTitle("Поиск").setView(edit)
      .setPositiveButton("Искать",(d,n)->{filter=edit.getText().toString().trim().toLowerCase(Locale.ROOT);refreshApps();})
      .setNeutralButton("Сбросить",(d,n)->{filter="";refreshApps();})
      .setNegativeButton("Закрыть",null).show();
  }
  void settings(){
    try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
    catch(Exception e){Toast.makeText(this,"Настройки недоступны",Toast.LENGTH_SHORT).show();}
  }
  void hdmi(TvInputInfo input){
    String name=String.valueOf(input.loadLabel(this));
    LinearLayout box=col();box.setPadding(dp(25),dp(20),dp(25),dp(20));box.setBackground(bg(0xff2f2842,0xff151520,20,0xffab8aff));
    box.addView(label("▱  "+name,24,WHITE,true),params(-1,50,0,7));
    box.addView(label("Сигнал: подключение обнаружено",16,0xffa3efb7,true),params(-1,45,0,9));
    box.addView(label("HDMI 2.0 и HDR: состояние драйверов ещё не подтверждено.\nНастройки источника появятся после аппаратных тестов.",14,MUTED,false),params(-1,78,0,0));
    AlertDialog dialog=new AlertDialog.Builder(this).setView(box).setPositiveButton("Закрыть",null).create();dialog.show();
    dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.65f),-2);
  }
  void about(){
    new AlertDialog.Builder(this).setTitle("AURA Home v0.1")
      .setMessage("Безопасный прототип интерфейса Android 9.\nНет системной прошивки, изменения HDMI, удаления PatchWall или китайских сервисов.\n\nОфлайн-голосовой поиск появится отдельным модулем после проверки микрофона.")
      .setPositiveButton("Закрыть",null).show();
  }
  @Override public boolean onKeyDown(int code,KeyEvent event){
    if(code==KeyEvent.KEYCODE_MENU){
      View focused=getCurrentFocus();
      if(focused!=null && focused.getTag() instanceof TvInputInfo){hdmi((TvInputInfo)focused.getTag());return true;}
    }
    return super.onKeyDown(code,event);
  }
}