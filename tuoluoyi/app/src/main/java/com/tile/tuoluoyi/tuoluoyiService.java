package com.tile.tuoluoyi;

import static java.lang.Math.abs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import rikka.shizuku.Shizuku;


public class tuoluoyiService extends Service {


    private SensorManager mSensorMgr;// 声明一个传感管理器对象
    public IUserService userService;
    public boolean isServiceBinded = false;
    public static boolean isServiceOK = false;
    SharedPreferences sp;

    float sensityX, sensityY;
    boolean invertX, invertY, invertAll;
    private BroadcastReceiver mBroadcastReceiver;

    private WindowManager.LayoutParams params;
    LinearLayout layout;
    ImageView view;

    private boolean exist = false, canmove, volume;
    int size;
    float density;
    private int SCREEN_WIDTH, SCREEN_HEIGHT;
    boolean moved;
    long downTime, upTime, downTime24, upTime24, downTime25, upTime25;
    private WindowManager windowManager;
    private boolean hasGyrpSope;

    //myListener用于实时更新设置项的值
    private final SharedPreferences.OnSharedPreferenceChangeListener myListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            if (s.equals("x") || s.equals("y")) return;
            size = (int) (sharedPreferences.getInt("size", 50) * density);
            params.width = size;
            params.height = size;
            params.alpha = sharedPreferences.getInt("tran", 90) * 0.01f;
            if (exist) windowManager.updateViewLayout(layout, params);
            canmove = sharedPreferences.getBoolean("canmove", true);
            sensityX = sharedPreferences.getInt("sensityX", 100) / 100f;
            sensityY = sharedPreferences.getInt("sensityY", 100) / 100f;
            invertX = sharedPreferences.getBoolean("invertX", false);
            invertY = sharedPreferences.getBoolean("invertY", false);
        }
    };
    
    //gyrpSopeListener用于将陀螺仪数据转换为虚拟手柄的操控
    private final SensorEventListener gyrpSopeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            injectTouch(event.values[1], -event.values[0]);
        }

        //当传感器精度改变时回调该方法，一般无需处理
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    
    //将服务绑定至Shizuku，从而可以让服务以ADB身份运行，我们能够虚拟手柄操控的原理就是ADB拥有INJECT_EVENT权限
    private void bindUserService() {
        invertAll = windowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_270;
        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection);
        } catch (Throwable ignored) {
        }
        Toast.makeText(tuoluoyiService.this, "bind success", Toast.LENGTH_SHORT).show();
        isServiceBinded = true;
    }

    //解绑服务，在服务不需要用的时候解绑，不然服务一直运行着占内存
    private void unbindUserService() {
        injectTouch(0, 0);
        try {
            Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true);
        } catch (Throwable ignored) {
        }
        Toast.makeText(tuoluoyiService.this, "unbind success", Toast.LENGTH_SHORT).show();
        isServiceBinded = false;
    }

    //这个监听器可以监听到Shizuku进程的启动。这里的意思就是，Shizuku一旦被激活，我们就赶快绑定我们的服务
    private final Shizuku.OnBinderReceivedListener BINDER_RECEIVED_LISTENER = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            bindUserService();
        }
    };

    //这个监听器可以监听到Shizuku进程的停止。我们需要在Shizuku进程停止时告诉用户这件事儿。
    private final Shizuku.OnBinderDeadListener BINDER_DEAD_LISTENER = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            Toast.makeText(tuoluoyiService.this, "Shizuku进程被停止", Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        isServiceOK = true;
        windowManager = (WindowManager) getSystemService(Service.WINDOW_SERVICE);
        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED_LISTENER);
        Shizuku.addBinderDeadListener(BINDER_DEAD_LISTENER);
        
        //读取用户的设置项
        sp = getSharedPreferences("data", 0);
        sp.registerOnSharedPreferenceChangeListener(myListener);
        sensityX = sp.getInt("sensityX", 100) / 100f;
        sensityY = sp.getInt("sensityY", 100) / 100f;
        invertX = sp.getBoolean("invertX", false);
        invertY = sp.getBoolean("invertY", false);

        //创建一个广播接收器，用于接收前台通知的"停止服务"按下事件
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopSelf();
            }
        };
        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.exit"));
        
        //如果用户开启了”使用前台通知“，则发送前台通知
        if (sp.getBoolean("foreground", true)) {
            Notification.Builder notification = new Notification.Builder(getApplication())
                    .setAutoCancel(true)
                    .setContentText("此通知有助于保证陀螺仪服务的后台运行")
                    .setContentTitle("陀螺仪服务运行中...")
                    .addAction(android.R.drawable.ic_delete, "停止服务", PendingIntent.getBroadcast(this, 0, new Intent("intent.tuoluoyi.exit"), PendingIntent.FLAG_IMMUTABLE))
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(Icon.createWithResource(this, R.drawable.tile))
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel("daemon", "陀螺仪服务", NotificationManager.IMPORTANCE_DEFAULT);
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
        
        
        //注册传感器监听器
        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        hasGyrpSope = mSensorMgr.registerListener(gyrpSopeListener, mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);
        if (!hasGyrpSope) {
            Toast.makeText(this, "您的设备不支持陀螺仪", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        //如果用户开启了”悬浮球“，则展示一个悬浮球。
        if (sp.getBoolean("floatWindow", false)) {
            GetWidthHeight();
            size = (int) (sp.getInt("size", 50) * density);
            params = new WindowManager.LayoutParams(size, size, Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, 1);
            params.alpha = sp.getInt("tran", 90) * 0.01f;
            params.x = sp.getInt("x", 0);
            params.y = sp.getInt("y", 0);
            layout = new LinearLayout(this);
            canmove = sp.getBoolean("canmove", true);
            volume = sp.getBoolean("volume", false);
            
            //设置悬浮球的触摸响应
            layout.setOnTouchListener(new View.OnTouchListener() {
                int lastX = 0;
                int lastY = 0;
                int paramX = 0;
                int paramY = 0;

                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {


                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downTime = System.currentTimeMillis();
                            moved = false;
                            lastX = (int) motionEvent.getRawX();
                            lastY = (int) motionEvent.getRawY();
                            paramX = params.x;
                            paramY = params.y;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (!canmove) return true;
                            int dx = (int) motionEvent.getRawX() - lastX;
                            int dy = (int) motionEvent.getRawY() - lastY;
                            if (abs(dx) > 4 || abs(dy) > 4)
                                moved = true;
                            params.x = paramX + dx;
                            params.y = paramY + dy;
                            windowManager.updateViewLayout(layout, params);

                            break;
                        case MotionEvent.ACTION_UP:
                            upTime = System.currentTimeMillis();
                            
                            //如果是单击，则绑定/解绑服务
                            if (!moved && upTime - downTime < 200) {
                                if (isServiceBinded) unbindUserService();
                                else bindUserService();
                            }
                            
                            //自动贴边
                            if (params.x > (SCREEN_WIDTH - size) * 0.43)
                                params.x = (SCREEN_WIDTH - size) / 2;
                            if (params.x < (SCREEN_WIDTH - size) * -0.43)
                                params.x = -(SCREEN_WIDTH - size) / 2;
                            params.x = Math.min(params.x, (SCREEN_WIDTH - size) / 2);
                            params.x = Math.max(params.x, -(SCREEN_WIDTH - size) / 2);
                            params.y = Math.min(params.y, (SCREEN_HEIGHT - size) / 2);
                            params.y = Math.max(params.y, -(SCREEN_HEIGHT - size) / 2);
                            windowManager.updateViewLayout(layout, params);

                            //存储悬浮球位置
                            sp.edit().putInt("x", params.x).putInt("y", params.y).apply();
                    }
                    return false;
                }
            });
            
            //悬浮球只会在横屏时显示。
            layout.setVisibility(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? View.GONE : View.VISIBLE);

            //设置悬浮球的View
            view = new ImageView(this);
            ShapeDrawable oval = new ShapeDrawable(new OvalShape());
            oval.getPaint().setColor(Color.DKGRAY);
            view.setBackground(oval);
            view.setImageResource(R.drawable.icon);

            layout.addView(view);
//            if (volume) {
//                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
//                view.setFocusableInTouchMode(true);
//                view.requestFocus();
//                view.setOnKeyListener(new View.OnKeyListener() {
//                    @Override
//                    public boolean onKey(View view, int i, KeyEvent keyEvent) {
//                        int keyCode = keyEvent.getKeyCode();
//                        if (keyCode != 24 && keyCode != 25) return false;
//                        long now = System.currentTimeMillis();
//                        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
//                            if (keyCode == 24) {
//                                downTime24 = now;
//                            } else {
//                                downTime25 = now;
//                            }
//                            return true;
//                        }
//                        if (keyCode == 24) {
//                            KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0);
//                            try {
//                                if (isServiceBinded) {
//                                    userService.InputKey(event);
//                                }
//                            } catch (Exception ignored) {
//                            }
//                            new Handler().postDelayed(new Runnable() {
//                                @Override
//                                public void run() {
//                                    KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0);
//                                    try {
//                                        if (isServiceBinded) {
//                                            userService.InputKey(event);
//                                        }
//                                    } catch (Exception ignored) {
//                                    }
//                                }
//                            }, 100);
//                        } else {
//                            KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0);
//                            try {
//                                if (isServiceBinded) {
//                                    userService.InputKey(event);
//                                }
//                            } catch (Exception ignored) {
//                            }
//                            new Handler().postDelayed(new Runnable() {
//                                @Override
//                                public void run() {
//                                    KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0);
//                                    try {
//                                        if (isServiceBinded) {
//                                            userService.InputKey(event);
//                                        }
//                                    } catch (Exception ignored) {
//                                    }
//                                }
//                            }, 100);
//                        }
//                        return true;
//                    }
//                });
//            }
            
            //显示悬浮球
            windowManager.addView(layout, params);
            exist = true;
        }
    }

    
    //获取设备的真实宽高(会计算导航栏和刘海区域。并且横竖屏时得到的宽高是相反的)。并获取像素密度，方便统一手机和平板上的显示效果
    void GetWidthHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        SCREEN_WIDTH = metrics.widthPixels;
        SCREEN_HEIGHT = metrics.heightPixels;
        density = metrics.density;
    }

    
    //绑定服务用到的东西。可以自定义onServiceConnected（函数，不过没啥用。
    public final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            if (binder != null && binder.pingBinder()) {
                userService = IUserService.Stub.asInterface(binder);
                isServiceBinded = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isServiceBinded = false;
        }
    };

    public final Shizuku.UserServiceArgs userServiceArgs = new Shizuku
            .UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID,
            UserService.class.getName())).processNameSuffix("service");


    private void injectTouch(float xValue, float yValue) {

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[0].toolType = 0;
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];
        pointerCoords[0] = new MotionEvent.PointerCoords();
        pointerCoords[0].clear();
        pointerCoords[0].setAxisValue(MotionEvent.AXIS_RZ, (invertAll ? 1 : -1) * (invertX ? 1 : -1) * xValue * sensityX);
        pointerCoords[0].setAxisValue(MotionEvent.AXIS_Z, (invertAll ? 1 : -1) * (invertY ? 1 : -1) * yValue * sensityY);
        //InputDevice.SOURCE_JOYSTICK为手柄摇杆
        MotionEvent event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 1, properties, pointerCoords,
                0, 0, 0, 0, 0, 0, InputDevice.SOURCE_JOYSTICK, 0);

        try {
            if (isServiceBinded) {
                userService.InputTouch(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hasGyrpSope) mSensorMgr.unregisterListener(gyrpSopeListener);
        unbindUserService();
        isServiceOK = false;
        if (exist) windowManager.removeView(layout);
        unregisterReceiver(mBroadcastReceiver);
        sp.unregisterOnSharedPreferenceChangeListener(myListener);
        Shizuku.removeBinderReceivedListener(BINDER_RECEIVED_LISTENER);
        Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (sp.getBoolean("floatWindow", false)) {
            GetWidthHeight();
            boolean isLand = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
            layout.setVisibility(isLand ? View.GONE : View.VISIBLE);
            if (exist) windowManager.updateViewLayout(layout, params);
        }
        super.onConfigurationChanged(newConfig);
    }
}
