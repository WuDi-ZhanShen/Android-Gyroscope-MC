package com.tile.tuoluoyi;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    boolean isGyroOK = false, isListenerAdded = false, isBroadcastRegistered = false;
    Button B;
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = (requestCode, grantResult) -> check();
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BinderContainer binderContainer = intent.getParcelableExtra("binder");
            IBinder binder = binderContainer.getBinder();
            //如果binder已经失去活性了，则不再继续解析
            if (!binder.pingBinder()) return;
            isGyroOK = true;
            B.setText("已连接陀螺仪服务");
            B.setTextColor(getColor(R.color.right));
            B.setOnClickListener(view -> showHelp());
            B.setOnLongClickListener(view -> {
                showExit(binder);
                return true;
            });
            findViewById(R.id.s1).setEnabled(true);
        }
    };

    private void showExit(IBinder binder) {
        new AlertDialog.Builder(this)
                .setTitle("关闭陀螺仪服务")
                .setMessage("要关闭陀螺仪服务并退出APP吗？\n\n关闭之后，您需要在下次使用本APP时再次启动陀螺仪服务。")
                .setNeutralButton("返回", null)
                .setPositiveButton("关闭并退出", (dialogInterface, i) -> {
                    try {
                        IGamePad.Stub.asInterface(binder).closeAndExit();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    B.setText("点我启动陀螺仪服务");
                    B.setTextColor(getColor(R.color.wrong));
                    Switch s1 = findViewById(R.id.s1);
                    s1.setEnabled(false);
                    s1.setChecked(false);
                    Toast.makeText(this, "成功关闭，即将退出", Toast.LENGTH_SHORT).show();
                    if (tuoluoyiService.isServiceOK)
                        stopService(new Intent(this, tuoluoyiService.class));
                    new Handler().postDelayed(this::finish, 1500);

                })
                .show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("隐私政策")
                .setPositiveButton("同意", (dialogInterface, i) -> getSharedPreferences("data", 0).edit().putBoolean("first", false).apply())
                .setCancelable(false)
                .setMessage("本应用不会收集您的任何信息，且完全不包含任何联网功能。继续使用则代表您同意上述隐私政策。\n")
                .setNegativeButton("退出", (dialogInterface, i) -> finish())
                .create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //限定一下横屏时的窗口宽度,让其不铺满屏幕。否则太丑
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().getAttributes().width = getResources().getDisplayMetrics().heightPixels;
        }

        //如果设备没有陀螺仪传感器，则退出程序
        if (!isDeviceHasGyro()) return;

        //如果是第一次打开APP，则展示隐私政策弹窗
        if (getSharedPreferences("data", 0).getBoolean("first", true)) showPrivacy();

        unzipFiles(); // 将启动陀螺仪进程需要的lib文件、sh文件和dex文件解压至Android/data，方便后续adb激活。

        //注册广播接收器，用来接收高权限的陀螺仪进程发来的含有binder的广播。收到广播就意味着陀螺仪进程启动了
        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.sendBinder"));
        isBroadcastRegistered = true;

        checkNotiPowerPermission();//这个函数会申请授权通知权限和忽略电池优化

        setViewsOnClick(); //设定主界面的按钮们和进度条们的点击事件
    }

    private void setViewsOnClick() {
        SharedPreferences sp = getSharedPreferences("data", 0);
        B = findViewById(R.id.b);
        String cmd = "sh " + getExternalFilesDir(null).getPath() + "/starter.sh";
        B.setOnClickListener(view -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("启动陀螺仪服务")
                .setMessage("您可以任选一种方式来启动陀螺仪服务：\n\n\t1.adb\n\t2.Shizuku\n\t3.root\n")
                .setPositiveButton("root", (dialogInterface, i) -> {
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        OutputStream outputStream = process.getOutputStream();
                        outputStream.write((cmd + "\nexit\n").getBytes());
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                })
                .setNeutralButton("复制adb命令", (dialogInterface, i) -> {
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("c", "adb shell " + cmd));
                    Toast.makeText(MainActivity.this, "adb命令已复制到剪切板：\nadb shell " + cmd, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("shizuku", (dialogInterface, i) -> check())
                .show());
        float density = getResources().getDisplayMetrics().density;
        ShapeDrawable oval = new ShapeDrawable(new RoundRectShape(new float[]{40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density}, null, null));
        oval.getPaint().setColor(getColor(R.color.a));
        B.setBackground(oval);

        Switch s1 = findViewById(R.id.s1);
        s1.setChecked(tuoluoyiService.isServiceOK);
        s1.setOnCheckedChangeListener((compoundButton, isChecked) -> {

            if (!((PowerManager) getSystemService(Service.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            if (!isGyroOK) {
                s1.setChecked(false);
                Toast.makeText(MainActivity.this, "请先连接陀螺仪服务", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isChecked)
                if (sp.getBoolean("floatWindow", true) && !Settings.canDrawOverlays(MainActivity.this)) {
                    startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
                    s1.setChecked(false);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sp.getBoolean("foreground", true)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
                        s1.setChecked(false);
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
                        return;
                    }
                    startForegroundService(new Intent(MainActivity.this, tuoluoyiService.class));
                } else
                    startService(new Intent(MainActivity.this, tuoluoyiService.class));
            else
                stopService(new Intent(MainActivity.this, tuoluoyiService.class));
        });

        Switch s2 = findViewById(R.id.s2);
        s2.setChecked(sp.getBoolean("foreground", true));
        s2.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            sp.edit().putBoolean("foreground", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
            }
            Toast.makeText(MainActivity.this, "重启服务后生效", Toast.LENGTH_SHORT).show();
        });

        Switch s3 = findViewById(R.id.s3);
        s3.setChecked(sp.getBoolean("invertX", false));
        s3.setOnCheckedChangeListener((compoundButton, isChecked) -> sp.edit().putBoolean("invertX", isChecked).apply());

        Switch s4 = findViewById(R.id.s4);
        s4.setChecked(sp.getBoolean("invertY", false));
        s4.setOnCheckedChangeListener((compoundButton, isChecked) -> sp.edit().putBoolean("invertY", isChecked).apply());
        final LinearLayout linearLayout1 = findViewById(R.id.l);
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(200L);
        ObjectAnimator animator = ObjectAnimator.ofFloat(null, "scaleX", 0.0f, 1.0f);
        transition.setAnimator(2, animator);
        linearLayout1.setLayoutTransition(transition);
        final LinearLayout linearLayout = findViewById(R.id.ll);
        Switch s5 = findViewById(R.id.s5);
        s5.setChecked(sp.getBoolean("floatWindow", true));
        s5.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + MainActivity.this.getPackageName())));
                sp.edit().putBoolean("floatWindow", false).apply();
                s5.setChecked(false);
                try {
                    linearLayout1.removeView(linearLayout);
                } catch (Exception ignored) {
                }
                return;
            }
            sp.edit().putBoolean("floatWindow", isChecked).apply();
            try {
                if (isChecked) {
                    linearLayout1.addView(linearLayout);
                } else {
                    linearLayout1.removeView(linearLayout);
                }
            } catch (Exception ignored) {
            }

        });

        Switch s6 = findViewById(R.id.s6);
        s6.setChecked(!sp.getBoolean("canmove", true));
        s6.setOnCheckedChangeListener((compoundButton, isChecked) -> sp.edit().putBoolean("canmove", !isChecked).apply());
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
        e.setOnKeyListener((view, i, keyEvent) -> {
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
        });
        e.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                int value = Integer.parseInt(e.getText().toString());
                if (value >= 0 && value <= 100) {
                    sp.edit().putInt("tran", value).apply();
                    sb.setProgress(value);
                } else {
                    Toast.makeText(MainActivity.this, "请输入0~100的整数", Toast.LENGTH_SHORT).show();
                    e.setText(String.format(Locale.getDefault(), "%d", sp.getInt("tran", 90)));
                }
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
        e3.setOnKeyListener((view, i, keyEvent) -> {
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
        });
        e3.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                int value = Integer.parseInt(e3.getText().toString());
                if (value >= 0 && value <= 100) {
                    sp.edit().putInt("size", value).apply();
                    sb3.setProgress(value);
                } else {
                    Toast.makeText(MainActivity.this, "请输入0~100的整数", Toast.LENGTH_SHORT).show();
                    e3.setText(String.format(Locale.getDefault(), "%d", sp.getInt("size", 50)));
                }
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
        e1.setOnKeyListener((view, i, keyEvent) -> {
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
        });
        e1.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                int value = Integer.parseInt(e1.getText().toString());
                if (value >= 0 && value <= 400) {
                    sp.edit().putInt("sensityX", value).apply();
                    sb1.setProgress(value);
                } else {
                    Toast.makeText(MainActivity.this, "请输入0~400的整数", Toast.LENGTH_SHORT).show();
                    e1.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityX", 100)));
                }
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
        e2.setOnKeyListener((view, i, keyEvent) -> {
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
        });
        e2.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                int value = Integer.parseInt(e2.getText().toString());
                if (value >= 0 && value <= 400) {
                    sp.edit().putInt("sensityY", value).apply();
                    sb2.setProgress(value);
                } else {
                    Toast.makeText(MainActivity.this, "请输入0~400的整数", Toast.LENGTH_SHORT).show();
                    e2.setText(String.format(Locale.getDefault(), "%d", sp.getInt("sensityY", 100)));
                }
            }
        });
        if (!sp.getBoolean("floatWindow", true)) {
            linearLayout1.removeView(linearLayout);
        }
    }

    private void checkNotiPowerPermission() {
        if (!((PowerManager) getSystemService(Service.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
        }
    }

    private void unzipFiles() {
        String file1 = getExternalFilesDir(null).getPath() + "/starter.sh";
        try {
            InputStream is = getAssets().open("starter.sh");
            FileOutputStream fileOutputStream = new FileOutputStream(file1);
            byte[] buffer = new byte[1024];
            int byteRead;
            while (-1 != (byteRead = is.read(buffer))) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            is.close();
            fileOutputStream.close();
        } catch (IOException ignored) {
        }
        String file2 = getExternalFilesDir(null).getPath() + "/self.apk";

        try {
            FileInputStream is = new FileInputStream(getPackageResourcePath());
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            byte[] buffer = new byte[1024];
            int byteRead;
            while (-1 != (byteRead = is.read(buffer))) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            is.close();
            fileOutputStream.close();
        } catch (IOException ignored) {
        }
        String file3 = getExternalFilesDir(null).getPath() + "/libtuoluoyi.so";
        try {
            FileInputStream in = new FileInputStream(getApplicationInfo().nativeLibraryDir + "/libtuoluoyi.so");
            FileOutputStream out = new FileOutputStream(file3);
            byte[] buffer = new byte[1024];
            int length;

            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isDeviceHasGyro() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> gyroSensors = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
        if (gyroSensors.size() == 0) {
            new AlertDialog.Builder(this)
                    .setMessage("您的设备不支持陀螺仪!")
                    .setCancelable(false)
                    .show();
            new Handler().postDelayed(this::finish, 3000);
            return false;
        }
        return true;
    }


    private void check() {
        //本函数用于检查shizuku状态，b代表shizuku是否运行，c代表shizuku是否授权
        if (!isListenerAdded) {
            Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
            isListenerAdded = true;
        }
        boolean isShizukuRunning = true, isShizukuGranted = false;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                Shizuku.requestPermission(0);
            else isShizukuGranted = true;
        } catch (Exception e) {
            if (checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED)
                isShizukuGranted = true;
            if (e.getClass() == IllegalStateException.class) {
                isShizukuRunning = false;
                Toast.makeText(this, "Shizuku未运行", Toast.LENGTH_SHORT).show();
            }
        }

        if (isShizukuRunning & isShizukuGranted) {
            try {
                Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
                OutputStream o = p.getOutputStream();
                o.write(("sh " + getExternalFilesDir(null).getPath() + "/starter.sh\nexit\n").getBytes());
                o.flush();
                o.close();
                p.waitFor();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    p.destroyForcibly();
                } else {
                    p.destroy();
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("使用帮助")
                .setPositiveButton("明白!", null)
                .setCancelable(true)
                .setMessage("本应用可以虚拟出一个连接在手机上的手柄，并将手机的陀螺仪数据实时转换为虚拟手柄的右摇杆移动。一次转换的耗时小于0.05ms。\n\n简单地说，就是可以用陀螺仪玩MC等支持手柄的游戏。这些游戏会误认为有一个手柄在控制视角移动，而手机的陀螺仪才是真正的控制者。\n\n以下是用此APP来玩MC时的一些问题和解答：\n\n1.走路和停下时的陀螺仪灵敏度不一样？\n答：请找到MC内的设置--控制器--鼠标灵敏度，调整为25左右即可。\n\n2.打不开背包、工作台、聊天框、村民交易等窗口？\n答：点击悬浮球，暂时停止陀螺仪。\n\n3.关闭陀螺仪后游戏视角向某个方向一直偏转？\n答：先点击悬浮球暂停陀螺仪，然后再关闭陀螺仪。\n\n4.陀螺仪的操作延迟很高？\n答：如果您的手机没有高刷新率，那么触控和陀螺仪的操作延迟就都会较大。您可以手指左右快速划屏，以此确定触控操作是否也存在延迟，从而判断延迟是来自设备本身还是来自我的程序。如果您确认了触控操作没有延迟，但陀螺仪操作却有延迟，您可以向我反馈问题。\n\n5.开着陀螺仪去在线PVP会被检测到外挂吗？\n答：应该只能检测到外设。\n")
                .create().show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBroadcastRegistered) unregisterReceiver(mBroadcastReceiver);
        if (isListenerAdded)
            Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    @Override
    public void onBackPressed() {
        finish();
    }


}