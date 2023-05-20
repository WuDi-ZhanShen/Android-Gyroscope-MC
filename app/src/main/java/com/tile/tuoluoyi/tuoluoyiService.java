package com.tile.tuoluoyi;

import static java.lang.Math.abs;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.Toast;


public class tuoluoyiService extends AccessibilityService {
    public IGamePad iGamePad;
    public IBinder binder;
    public boolean isBroadcastRegistered = false, isGamePadCreated = false, isGyroEnabled = false, invertX = false, invertY = false, isFloatWindowExist = false, canFloatWindowMove = true, isSharedPreferenceRegistered = false, isThumbLPressed = false;
    public SharedPreferences sp;
    public int sensityX, sensityY;
    public WindowManager windowManager;
    public WindowManager.LayoutParams params;
    public int floatWindowSize;
    public int SCREEN_WIDTH, SCREEN_HEIGHT;
    public ImageView view;
    public SensorManager mSensorMgr;// 声明一个传感管理器对象
    public final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "intent.tuoluoyi.exit":
                    disableSelf();
                    break;
                case "android.intent.action.CONFIGURATION_CHANGED":
                    if (isFloatWindowExist) {
                        GetWidthHeight();
                        int rotation = windowManager.getDefaultDisplay().getRotation();
                        view.setVisibility(rotation == 0 || rotation == 2 ? View.GONE : View.VISIBLE);
                        view.setImageResource(R.drawable.icon);
                        windowManager.updateViewLayout(view, params);
                    }
                    break;
                case "intent.tuoluoyi.sendBinder":
                    BinderContainer binderContainer = intent.getParcelableExtra("binder");
                    IBinder binder = binderContainer.getBinder();

                    //如果binder已经失去活性了，则不再继续解析
                    if (!binder.pingBinder()) return;
                    tuoluoyiService.this.binder = binder;
                    //将binder转换为接口
                    iGamePad = IGamePad.Stub.asInterface(binder);
                    try {
                        iGamePad.changeMode(sp.getInt("currentMode", 0));
                        iGamePad.syncPrefs(invertX, invertY, sensityX, sensityY);
                        isGamePadCreated = iGamePad.create();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    if (isGamePadCreated) {
                        Toast.makeText(context, R.string.connect_success, Toast.LENGTH_SHORT).show();
                        //注册传感器监听器
                        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                        boolean hasGyroSope = mSensorMgr.registerListener(gyroListener, mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                                SensorManager.SENSOR_DELAY_FASTEST);
                        if (!hasGyroSope) {
                            Toast.makeText(context, R.string.gyro_notfound, Toast.LENGTH_SHORT).show();
                            disableSelf();
                            return;
                        }
                        isGyroEnabled = true;

                        //如果用户开启了”悬浮球“，则展示一个悬浮球。
                        if (sp.getBoolean("floatWindow", true)) {
                            showFloatWindow();
                        }

                    } else
                        Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show();
            }

        }
    };
    //myListener用于实时更新设置项的值
    public final SharedPreferences.OnSharedPreferenceChangeListener myListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            if (s.equals("x") || s.equals("y")) return;
            if (isFloatWindowExist) {
                floatWindowSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sharedPreferences.getInt("size", 50), getResources().getDisplayMetrics()));
                params.width = floatWindowSize;
                params.height = floatWindowSize;
                params.alpha = sharedPreferences.getInt("tran", 90) * 0.01f;
                windowManager.updateViewLayout(view, params);
            }
            canFloatWindowMove = sharedPreferences.getBoolean("canmove", true);
            invertX = sharedPreferences.getBoolean("invertX", false);
            invertY = sharedPreferences.getBoolean("invertY", false);
            sensityX = sharedPreferences.getInt("sensityX", 100);
            sensityY = sharedPreferences.getInt("sensityY", 100);

            try {
                iGamePad.syncPrefs(invertX, invertY, sensityX, sensityY);
            } catch (RemoteException ignored) {
            }
        }
    };

    //gyroListener用于将陀螺仪数据转换为虚拟手柄的操控
    public final SensorEventListener gyroListener = new SensorEventListener() {


        @Override
        public void onSensorChanged(SensorEvent event) {

            try {
                iGamePad.inputEvent(event.values[1], -event.values[0]);
            } catch (Exception ignored) {
                if (isGyroEnabled) {
                    mSensorMgr.unregisterListener(this);
                    isGyroEnabled = false;
                }
                if (!binder.pingBinder())
                    Toast.makeText(tuoluoyiService.this, "Binder Died!", Toast.LENGTH_SHORT).show();

            }

        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(Service.WINDOW_SERVICE);

        //读取用户的灵敏度设置项等等
        sp = getSharedPreferences("data", 0);
        invertX = sp.getBoolean("invertX", false);
        invertY = sp.getBoolean("invertY", false);
        sensityX = sp.getInt("sensityX", 100);
        sensityY = sp.getInt("sensityY", 100);


        //注册广播接收器，用来接收陀螺仪进程发来的广播
        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.exit"));
        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.sendBinder"));
        registerReceiver(mBroadcastReceiver, new IntentFilter("android.intent.action.CONFIGURATION_CHANGED"));

        isBroadcastRegistered = true;

        //如果用户开启了”使用前台通知“，则发送前台通知
        if (sp.getBoolean("foreground", true)) {
            sendNotification();
        }

        //注册偏好变动监视器，用来实时更新用户的灵敏度设置等等
        sp.registerOnSharedPreferenceChangeListener(myListener);
        isSharedPreferenceRegistered = true;
    }

    private void showFloatWindow() {
        GetWidthHeight();
        floatWindowSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sp.getInt("size", 50), getResources().getDisplayMetrics()));
        params = new WindowManager.LayoutParams(floatWindowSize, floatWindowSize, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, 1);
        params.alpha = sp.getInt("tran", 90) * 0.01f;
        params.x = sp.getInt("x", 0);
        params.y = sp.getInt("y", 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        canFloatWindowMove = sp.getBoolean("canmove", true);
        view = new ImageView(this);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        view.setVisibility(rotation == 0 || rotation == 2 ? View.GONE : View.VISIBLE);
        view.setImageResource(R.drawable.icon);//设置悬浮球的View
        //设置悬浮球的触摸响应
        view.setOnTouchListener(new View.OnTouchListener() {
            float lastX = 0, lastY = 0;
            long downTime;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {


                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        moved = false;
                        lastX = motionEvent.getRawX();
                        lastY = motionEvent.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!canFloatWindowMove) return true;
                        float rawX = motionEvent.getRawX();
                        float rawY = motionEvent.getRawY();
                        int dx = Math.round(rawX - lastX);
                        int dy = Math.round(rawY - lastY);
                        lastX += dx;
                        lastY += dy;
                        if (abs(dx) > 4 || abs(dy) > 4)
                            moved = true;
                        params.x += dx;
                        params.y += dy;
                        windowManager.updateViewLayout(view, params);

                        break;
                    case MotionEvent.ACTION_UP:

                        //如果是单击，则暂停/恢复陀螺仪服务
                        if (!moved) {
                            if (System.currentTimeMillis() - downTime < 200) {

                                if (isGyroEnabled) {
                                    mSensorMgr.unregisterListener(gyroListener);
                                    isGyroEnabled = false;
                                    Toast.makeText(tuoluoyiService.this, R.string.pause, Toast.LENGTH_SHORT).show();
                                    try {
                                        iGamePad.inputEvent(0, 0);
                                    } catch (RemoteException ignored) {
                                    }

                                } else {
                                    mSensorMgr.registerListener(gyroListener, mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
                                    isGyroEnabled = true;
                                    Toast.makeText(tuoluoyiService.this, R.string.resume, Toast.LENGTH_SHORT).show();
                                }


                            } else {
                                isThumbLPressed = !isThumbLPressed;
                                try {
                                    iGamePad.pressThumbL(isThumbLPressed);
                                } catch (RemoteException ignored) {
                                }
                                Toast.makeText(tuoluoyiService.this, "已帮您" + (isThumbLPressed ? "按下" : "抬起") + "左摇杆键", Toast.LENGTH_SHORT).show();
                                view.setBackgroundColor(isThumbLPressed ? Color.DKGRAY : Color.TRANSPARENT);
                            }
                        }
                }
                //自动贴边
                params.x = Math.min(Math.max(params.x, -(SCREEN_WIDTH - floatWindowSize) / 2), (SCREEN_WIDTH - floatWindowSize) / 2);
                params.y = Math.min(Math.max(params.y, -(SCREEN_HEIGHT - floatWindowSize) / 2), (SCREEN_HEIGHT - floatWindowSize) / 2);

                windowManager.updateViewLayout(view, params);

                //存储悬浮球位置
                sp.edit().putInt("x", params.x).putInt("y", params.y).apply();

                return false;
            }
        });
        windowManager.addView(view, params);//显示悬浮球
        isFloatWindowExist = true;
    }

    private void sendNotification() {
        Notification.Builder notification = new Notification.Builder(this)
                .setContentText(getString(R.string.noti_text))
                .setContentTitle(getString(R.string.noti_title))
                .addAction(new Notification.Action(android.R.drawable.ic_delete, getString(R.string.noti_action), PendingIntent.getBroadcast(this, 0, new Intent("intent.tuoluoyi.exit"), PendingIntent.FLAG_IMMUTABLE)))
                .setSmallIcon(Icon.createWithResource(this, R.drawable.tile))
                .setColor(getColor(R.color.bg))
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel("daemon", getString(R.string.noti_channel), NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
            notification.setChannelId("daemon");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        startForeground(1, notification.build());
    }


    //获取设备的真实宽高(会计算导航栏和刘海区域。并且横竖屏时得到的宽高是相反的)。
    void GetWidthHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        SCREEN_WIDTH = metrics.widthPixels;
        SCREEN_HEIGHT = metrics.heightPixels;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isGyroEnabled) mSensorMgr.unregisterListener(gyroListener);
        try {
            iGamePad.inputEvent(0, 0);
            iGamePad.close();
        } catch (Exception ignored) {
        }
        if (isFloatWindowExist) windowManager.removeView(view);
        if (isBroadcastRegistered) unregisterReceiver(mBroadcastReceiver);
        if (isSharedPreferenceRegistered) sp.unregisterOnSharedPreferenceChangeListener(myListener);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            try {
                iGamePad.pressTL(event.getAction() == KeyEvent.ACTION_DOWN);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            try {
                iGamePad.pressTR(event.getAction() == KeyEvent.ACTION_DOWN);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isFloatWindowExist) {
            GetWidthHeight();
            view.setVisibility(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? View.VISIBLE : View.GONE);
            view.setImageResource(R.drawable.icon);
            windowManager.updateViewLayout(view, params);
        }
        super.onConfigurationChanged(newConfig);
    }
}
