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
import android.content.ComponentName;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
            B.setText(R.string.service_actived);
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
        IGamePad gamePad = IGamePad.Stub.asInterface(binder);
        SharedPreferences sp = getSharedPreferences("data", 0);
        int currentMode = 0;
        try {
            gamePad.changeMode(sp.getInt("currentMode", 0));
            currentMode = gamePad.getCurrentMode();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.deactive_service)
                .setMessage(R.string.deactive_service_text)
                .setPositiveButton(getString(R.string.change_mode) + (currentMode == 2 ? 1 : currentMode + 2), (dialogInterface, i) -> {
                    try {
                        int mode = gamePad.getCurrentMode();
                        mode = (mode == 2 ? 0 : mode + 1);
                        gamePad.changeMode(mode);
                        sp.edit().putInt("currentMode", mode).apply();
                        Toast.makeText(this, getString(R.string.change_mode) + (mode+1), Toast.LENGTH_SHORT).show();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                })
                .setNeutralButton(R.string.deactive, (dialogInterface, i) -> {
                    try {
                        gamePad.closeAndExit();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    B.setText(R.string.click_to_active);
                    B.setTextColor(getColor(R.color.wrong));
                    Switch s1 = findViewById(R.id.s1);
                    s1.setEnabled(false);
                    s1.setChecked(false);
                    Toast.makeText(this, R.string.deactive_success, Toast.LENGTH_SHORT).show();
                    sendBroadcast(new Intent("intent.tuoluoyi.exit"));
                    new Handler().postDelayed(this::finish, 1000);

                })
                .show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.privacy_title)
                .setPositiveButton(R.string.agree, (dialogInterface, i) -> getSharedPreferences("data", 0).edit().putBoolean("first", false).apply())
                .setCancelable(false)
                .setMessage(R.string.privacy_text)
                .setNegativeButton(R.string.exit, (dialogInterface, i) -> finish())
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
                .setTitle(R.string.active_title)
                .setMessage(R.string.active_text)
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
                .setNeutralButton(R.string.copy_cmd, (dialogInterface, i) -> {
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("c", "adb shell " + cmd));
                    Toast.makeText(MainActivity.this, getString(R.string.cmd_copied) + cmd, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("shizuku", (dialogInterface, i) -> check())
                .show());
        float density = getResources().getDisplayMetrics().density;
        ShapeDrawable oval = new ShapeDrawable(new RoundRectShape(new float[]{40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density, 40 * density}, null, null));
        oval.getPaint().setColor(getColor(R.color.a));
        B.setBackground(oval);

        Switch s1 = findViewById(R.id.s1);
        String set = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        s1.setChecked(set != null && set.contains(getPackageName()));
        s1.setOnCheckedChangeListener((compoundButton, isChecked) -> {

            if (!((PowerManager) getSystemService(Service.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            if (!isGyroOK) {
                s1.setChecked(false);
                Toast.makeText(MainActivity.this, R.string.service_not_active, Toast.LENGTH_SHORT).show();
                return;
            }

            if (sp.getBoolean("foreground", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
                s1.setChecked(false);
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
                return;
            }
            if (isChecked) {
                final String serviceName = new ComponentName(getPackageName(), tuoluoyiService.class.getName()).flattenToString();
                final String oldSetting = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                final String newSetting = oldSetting == null ? serviceName : serviceName + ":" + oldSetting;
                try {
                    Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                    Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newSetting);
                } catch (Exception e) {
                    s1.setChecked(false);
                    Toast.makeText(MainActivity.this, R.string.manaual_open, Toast.LENGTH_SHORT).show();
                    Bundle bundle = new Bundle();
                    bundle.putString(":settings:fragment_args_key", serviceName);
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(":settings:fragment_args_key", serviceName).putExtra(":settings:show_fragment_args", bundle));
                }
            } else {
                sendBroadcast(new Intent("intent.tuoluoyi.exit"));
            }

        });

        Switch s2 = findViewById(R.id.s2);
        s2.setChecked(sp.getBoolean("foreground", true));
        s2.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            sp.edit().putBoolean("foreground", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
            }
            Toast.makeText(MainActivity.this, R.string.need_restart, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_100, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_100, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_100, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_100, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_400, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_400, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_400, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.input_400, Toast.LENGTH_SHORT).show();
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


        String file2 = getExternalFilesDir(null).getPath() + "/GyroNative.dex";
        try {
            ZipFile zipFile = new ZipFile(getPackageResourcePath());
            // 遍历zip文件中的所有条目
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // 如果条目名称为classes.dex，则解压该条目到指定目录
                if (entry.getName().equals("classes.dex")) {
                    InputStream inputStream = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(file2);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    break;
                }
            }

            // 关闭ZipFile对象
            zipFile.close();
        } catch (IOException e) {
            e.printStackTrace();
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
                    .setMessage(R.string.gyro_notfound)
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
                Toast.makeText(this, R.string.shizuku_notready, Toast.LENGTH_SHORT).show();
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
                .setTitle(R.string.help_title)
                .setPositiveButton(R.string.ok, null)
                .setMessage(R.string.help_text)
                .show();
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            Switch s1 = findViewById(R.id.s1);
            String set = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            s1.setChecked(set != null && set.contains(getPackageName()));
        }
        super.onWindowFocusChanged(hasFocus);
    }
}