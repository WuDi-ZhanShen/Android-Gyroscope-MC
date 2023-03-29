package com.tile.tuoluoyi;

import static java.lang.Math.abs;

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
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;


public class tuoluoyiService extends Service {
    private SensorManager mSensorMgr;// 声明一个传感管理器对象
    private boolean isGyroEnabled = false;
    public IGamePad iGamePad;
    public boolean isServiceBinded = false;
    public static boolean isServiceOK = false;
    SharedPreferences sp;

    float sensityX, sensityY;
    int invertX, invertY, invertAll = 1;

    private WindowManager.LayoutParams params;
    ImageView view;

    private boolean exist = false, canmove;
    int size;
    float density;
    private int SCREEN_WIDTH, SCREEN_HEIGHT;
    boolean moved;
    long downTime, upTime;
    private WindowManager windowManager;


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "intent.tuoluoyi.exit":
                    stopSelf();
                    break;
                case "intent.tuoluoyi.sendBinder":
                    BinderContainer binderContainer = intent.getParcelableExtra("binder");
                    IBinder binder = binderContainer.getBinder();
                    //如果binder已经失去活性了，则不再继续解析
                    if (!binder.pingBinder()) return;

                    //将binder转换为接口
                    iGamePad = IGamePad.Stub.asInterface(binder);
                    try {
                        isServiceBinded = iGamePad.createUInput();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    if (isServiceBinded)
                        Toast.makeText(context, "成功连接陀螺仪服务", Toast.LENGTH_SHORT).show();
                    else Toast.makeText(context, "连接陀螺仪服务出错", Toast.LENGTH_SHORT).show();
            }

        }
    };
    //myListener用于实时更新设置项的值
    private final SharedPreferences.OnSharedPreferenceChangeListener myListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            if (s.equals("x") || s.equals("y")) return;
            if (exist) {
                size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sharedPreferences.getInt("size", 50), getResources().getDisplayMetrics()));
                params.width = size;
                params.height = size;
                params.alpha = sharedPreferences.getInt("tran", 90) * 0.01f;
                windowManager.updateViewLayout(view, params);
            }
            canmove = sharedPreferences.getBoolean("canmove", true);
            invertX = sharedPreferences.getBoolean("invertX", false) ? -1 : 1;
            invertY = sharedPreferences.getBoolean("invertY", false) ? -1 : 1;
            sensityX = (invertAll << 16) * invertX * sharedPreferences.getInt("sensityX", 100) / 100f;
            sensityY = (invertAll << 16) * invertY * sharedPreferences.getInt("sensityY", 100) / 100f;
        }
    };

    //gyroListener用于将陀螺仪数据转换为虚拟手柄的操控
    private final SensorEventListener gyroListener = new SensorEventListener() {
        float lastX = 0, lastY = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {

            //原始陀螺仪数据乘以灵敏度，再加上上次陀螺仪数据四舍五入的差值
            float nowX = sensityX * event.values[1] + lastX;
            float nowY = sensityY * -event.values[0] + lastY;

            //四舍五入之后的整数部分数值
            int roundX = Math.round(nowX);
            int roundY = Math.round(nowY);

            //lastX和lastY用来记录四舍五入的小数部分差值，下次获取的传感器数据会先加上此差值再参与计算
            lastX = nowX - roundX;
            lastY = nowY - roundY;

            //传入整数部分给虚拟手柄
            inputEvent(roundX, roundY);
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceOK = true;
        windowManager = (WindowManager) getSystemService(Service.WINDOW_SERVICE);

        //读取用户的设置项
        sp = getSharedPreferences("data", 0);
        invertX = sp.getBoolean("invertX", false) ? -1 : 1;
        invertY = sp.getBoolean("invertY", false) ? -1 : 1;
        sensityX = (invertAll << 16) * invertX * sp.getInt("sensityX", 100) / 100f;
        sensityY = (invertAll << 16) * invertY * sp.getInt("sensityY", 100) / 100f;


        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.exit"));
        registerReceiver(mBroadcastReceiver, new IntentFilter("intent.tuoluoyi.sendBinder"));
        //如果用户开启了”使用前台通知“，则发送前台通知
        if (sp.getBoolean("foreground", true)) {
            Notification.Builder notification = new Notification.Builder(getApplication())
                    .setAutoCancel(true)
                    .setContentText("点我打开陀螺仪设置")
                    .setContentTitle("陀螺仪服务运行中...")
                    .addAction(android.R.drawable.ic_delete, "停止服务", PendingIntent.getBroadcast(this, 0, new Intent("intent.tuoluoyi.exit"), PendingIntent.FLAG_IMMUTABLE))
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(Icon.createWithResource(this, R.drawable.tile))
                    .setColor(getColor(R.color.bg))
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
        boolean hasGyroSope = mSensorMgr.registerListener(gyroListener, mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);
        if (!hasGyroSope) {
            Toast.makeText(this, "您的设备不支持陀螺仪", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }
        isGyroEnabled = true;
        //如果用户开启了”悬浮球“，则展示一个悬浮球。
        view = new ImageView(this);
        if (sp.getBoolean("floatWindow", true)) {
            GetWidthHeight();
            size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sp.getInt("size", 50), getResources().getDisplayMetrics()));
            params = new WindowManager.LayoutParams(size, size, Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, 1);
            params.alpha = sp.getInt("tran", 90) * 0.01f;
            params.x = sp.getInt("x", 0);
            params.y = sp.getInt("y", 0);
            canmove = sp.getBoolean("canmove", true);

            //设置悬浮球的触摸响应
            view.setOnTouchListener(new View.OnTouchListener() {
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
                            windowManager.updateViewLayout(view, params);

                            break;
                        case MotionEvent.ACTION_UP:
                            upTime = System.currentTimeMillis();

                            //如果是单击，则绑定/解绑服务
                            if (!moved && upTime - downTime < 200) {
                                if (isGyroEnabled) {
                                    mSensorMgr.unregisterListener(gyroListener);
                                    isGyroEnabled = false;
                                    Toast.makeText(tuoluoyiService.this, "暂时停止陀螺仪服务", Toast.LENGTH_SHORT).show();
                                    inputEvent(0, 0);
                                } else {
                                    invertAll = windowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_270 ? -1 : 1;
                                    mSensorMgr.registerListener(gyroListener, mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                                            SensorManager.SENSOR_DELAY_FASTEST);
                                    isGyroEnabled = true;
                                    Toast.makeText(tuoluoyiService.this, "恢复陀螺仪服务", Toast.LENGTH_SHORT).show();
                                }

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
                            windowManager.updateViewLayout(view, params);

                            //存储悬浮球位置
                            sp.edit().putInt("x", params.x).putInt("y", params.y).apply();
                    }
                    return false;
                }
            });

            //悬浮球只会在横屏时显示。
            view.setVisibility(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? View.GONE : View.VISIBLE);

            //设置悬浮球的View
            view.setImageResource(R.drawable.icon);


            //显示悬浮球
            windowManager.addView(view, params);
            exist = true;
        }

        sp.registerOnSharedPreferenceChangeListener(myListener);

    }


    //获取设备的真实宽高(会计算导航栏和刘海区域。并且横竖屏时得到的宽高是相反的)。并获取像素密度，方便统一手机和平板上的显示效果
    void GetWidthHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        SCREEN_WIDTH = metrics.widthPixels;
        SCREEN_HEIGHT = metrics.heightPixels;
        density = metrics.density;
    }

    private void inputEvent(int xValue, int yValue) {

        try {
            if (isServiceBinded) {
                iGamePad.inputEvent(xValue, yValue);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isGyroEnabled) mSensorMgr.unregisterListener(gyroListener);
        try {
            if (isServiceBinded) {
                iGamePad.inputEvent(0, 0);
                iGamePad.closeUInput();
            }
        } catch (Exception ignored) {
        }
        isServiceOK = false;
        if (exist) windowManager.removeView(view);
        unregisterReceiver(mBroadcastReceiver);
        sp.unregisterOnSharedPreferenceChangeListener(myListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        invertAll = windowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_270 ? -1 : 1;
        if (sp.getBoolean("floatWindow", true)) {
            GetWidthHeight();
            if (exist) {
                view.setVisibility(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT ? View.GONE : View.VISIBLE);
                view.setImageResource(R.drawable.icon);
                windowManager.updateViewLayout(view, params);
            }
        }
        super.onConfigurationChanged(newConfig);
    }
}
