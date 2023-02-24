package com.tile.tuoluoyi;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {


    Button B;
    int m;
    SharedPreferences sp;
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            check();
        }
    };

    private void showPrivacy() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("隐私政策")
                .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sp.edit().putBoolean("first", false).apply();
                    }
                })
                .setCancelable(false)
                .setMessage("本应用不会收集您的任何信息，且完全不包含任何联网功能。继续使用则代表您同意上述隐私政策。\n")
                .setNegativeButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })
                .create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //限定一下横屏时的窗口宽度,让其不铺满屏幕。否则太丑
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            getWindow().getAttributes().width = (getWindowManager().getDefaultDisplay().getHeight());
        if (!((PowerManager) getSystemService(Service.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
        }
        B = findViewById(R.id.b);
        m = B.getCurrentTextColor();
        float density = getResources().getDisplayMetrics().density;
        ShapeDrawable oval = new ShapeDrawable(new RoundRectShape(new float[]{40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density}, null, null));
        oval.getPaint().setColor(getColor(R.color.a));
        B.setBackground(oval);
        check();
        sp = getSharedPreferences("data", 0);
        if (sp.getBoolean("first", true)) {
            showPrivacy();
        }
        Switch s1 = findViewById(R.id.s1);
        s1.setChecked(tuoluoyiService.isServiceOK);
        s1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {

                if (!((PowerManager) getSystemService(Service.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));

                if (isChecked)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sp.getBoolean("foreground", true)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled())
                            return;
                        startForegroundService(new Intent(MainActivity.this, tuoluoyiService.class));
                    } else
                        startService(new Intent(MainActivity.this, tuoluoyiService.class));
                else
                    stopService(new Intent(MainActivity.this, tuoluoyiService.class));
            }

        });

        Switch s2 = findViewById(R.id.s2);
        s2.setChecked(sp.getBoolean("foreground", true));
        s2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                sp.edit().putBoolean("foreground", isChecked).apply();
                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
                }
                Toast.makeText(MainActivity.this, "重启服务后生效", Toast.LENGTH_SHORT).show();
            }
        });

        Switch s3 = findViewById(R.id.s3);
        s3.setChecked(sp.getBoolean("invertX", false));
        s3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                sp.edit().putBoolean("invertX", isChecked).apply();
            }
        });

        Switch s4 = findViewById(R.id.s4);
        s4.setChecked(sp.getBoolean("invertY", false));
        s4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                sp.edit().putBoolean("invertY", isChecked).apply();
            }
        });
        final LinearLayout linearLayout1 = (LinearLayout) findViewById(R.id.l);
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(200L);
        ObjectAnimator animator = ObjectAnimator.ofFloat(null, "scaleX", 0.0f, 1.0f);
        transition.setAnimator(2, animator);
        linearLayout1.setLayoutTransition(transition);
        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.ll);
        Switch s5 = findViewById(R.id.s5);
        s5.setChecked(sp.getBoolean("floatWindow", false));
        s5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {

                    try {
                        Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
                        DataOutputStream o = new DataOutputStream(p.getOutputStream());
                        o.writeBytes("appops set com.tile.tuoluoyi SYSTEM_ALERT_WINDOW allow");
                        o.flush();
                        o.close();
                        p.waitFor();
                        if (p.exitValue() != 0)
                            startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            p.destroyForcibly();
                        } else {
                            p.destroy();
                        }
                    } catch (IOException | InterruptedException ignored) {
                        startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
                    }
                }
                sp.edit().putBoolean("floatWindow", isChecked).apply();
                if (isChecked) {
                    linearLayout1.addView(linearLayout);
                } else {
                    linearLayout1.removeView(linearLayout);
                }
            }
        });

        Switch s6 = findViewById(R.id.s6);
        s6.setChecked(!sp.getBoolean("canmove", true));
        s6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                sp.edit().putBoolean("canmove", !isChecked).apply();
            }
        });
        EditText e = findViewById(R.id.e);
        SeekBar sb = findViewById(R.id.sb);
        e.setText(String.format(Locale.getDefault(), "%d", sp.getInt("tran", 90)));
        sb.setProgress(sp.getInt("tran", 90));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                e.setText(String.format(Locale.getDefault(), "%d", progress));
                sp.edit().putInt("tran", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        e.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN && e.getText().length() > 0) {
                    int value = Integer.parseInt(e.getText().toString());
                    if (value >= 0 && value <= 100) {
                        sp.edit().putInt("tran", value).apply();
                        sb.setProgress(value);
                    } else {
                        Toast.makeText(MainActivity.this, "请输入0~100的整数", Toast.LENGTH_SHORT).show();
                        e.setText(String.format(Locale.getDefault(), "%d", sp.getInt("tran", 90)));
                    }
                }
                return false;
            }
        });

        EditText e3 = findViewById(R.id.e3);
        SeekBar sb3 = findViewById(R.id.sb3);
        e3.setText(String.format(Locale.getDefault(), "%d", sp.getInt("size", 50)));
        sb3.setProgress(sp.getInt("size", 50));
        sb3.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                e3.setText(String.format(Locale.getDefault(), "%d", progress));
                sp.edit().putInt("size", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        e3.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN && e3.getText().length() > 0) {
                    int value = Integer.parseInt(e3.getText().toString());
                    if (value >= 0 && value <= 100) {
                        sp.edit().putInt("size", value).apply();
                        sb3.setProgress(value);
                    } else {
                        Toast.makeText(MainActivity.this, "请输入0~100的整数", Toast.LENGTH_SHORT).show();
                        e3.setText(String.format(Locale.getDefault(), "%d", sp.getInt("size", 50)));
                    }
                }
                return false;
            }
        });

        EditText e1 = findViewById(R.id.e1);
        SeekBar sb1 = findViewById(R.id.sb1);
        e1.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityX", 100)));
        sb1.setProgress(sp.getInt("sensityX", 100));
        sb1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                e1.setText(String.format(Locale.getDefault(), "%d", i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sp.edit().putInt("sensityX", seekBar.getProgress()).apply();
            }
        });
        e1.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN && e1.getText().length() > 0) {
                    int value = Integer.parseInt(e1.getText().toString());
                    if (value >= 0 && value <= 400) {
                        sp.edit().putInt("sensityX", value).apply();
                        sb1.setProgress(value);
                    } else {
                        Toast.makeText(MainActivity.this, "请输入0~400的整数", Toast.LENGTH_SHORT).show();
                        e1.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityX", 100)));
                    }
                }
                return false;
            }
        });


        EditText e2 = findViewById(R.id.e2);
        SeekBar sb2 = findViewById(R.id.sb2);
        e2.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityY", 100)));
        sb2.setProgress(sp.getInt("sensityY", 100));
        sb2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                e2.setText(String.format(Locale.getDefault(), "%d", i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sp.edit().putInt("sensityY", seekBar.getProgress()).apply();
            }
        });
        e2.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN && e2.getText().length() > 0) {
                    int value = Integer.parseInt(e2.getText().toString());
                    if (value >= 0 && value <= 400) {
                        sp.edit().putInt("sensityY", value).apply();
                        sb2.setProgress(value);
                    } else {
                        Toast.makeText(MainActivity.this, "请输入0~400的整数", Toast.LENGTH_SHORT).show();
                        e2.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityY", 100)));
                    }
                }
                return false;
            }
        });
        if (!sp.getBoolean("floatWindow", false)) {
            linearLayout1.removeView(linearLayout);
        }
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }


    private void check() {
        //本函数用于检查shizuku状态，b代表shizuk是否运行，c代表shizuku是否授权
        boolean b = true, c = false;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                Shizuku.requestPermission(0);
            else c = true;
        } catch (Exception e) {
            if (checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED)
                c = true;
            if (e.getClass() == IllegalStateException.class) {
                b = false;
                Toast.makeText(this, "Shizuku未运行", Toast.LENGTH_SHORT).show();
            }
        }

        B.setText(String.format("Shizuku: %s", b ? c ? "已激活" : "未授权" : "未运行"));
        B.setTextColor(b & c ? m : 0xaaff0000);
        B.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showHelp();
            }
        });
    }

    private void showHelp() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("使用帮助")
                .setPositiveButton("明白!",null)
                .setCancelable(true)
                .setMessage("本应用可以虚拟出一个连接在手机上的手柄，并实时将手机的陀螺仪数据转换为手柄的右摇杆移动。\n\n简单的说，就是可以用手机的陀螺仪玩MC等支持手柄的游戏。这些游戏会误认为有一个手柄在控制视角转动，但是实际上是手机陀螺仪在控制视角。\n\n")
                .create().show();
    }

    public void ch(View view) {
        check();
    }


    @Override
    protected void onPause() {
        super.onPause();
        // 注销当前活动的传感监听器
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    @Override
    public void onBackPressed() {
        finish();
    }


}