package com.tile.tuoluoyi;

import android.app.IApplicationThread;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.IRotationWatcher;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Scanner;

public class GamePadNative {
    static boolean isCreated = false;
    static boolean invertAll = false;
    static boolean isMode1 = false;
    static android.hardware.input.InputManager im;
    static Method injectInputEventMethod;
    static MotionEvent.PointerProperties[] properties;
    static MotionEvent.PointerCoords[] pointerCoords;

    static {
        System.loadLibrary("tuoluoyi");
    }


    public static void main(String[] args) {
        //检查权限
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) {
            System.err.printf("Insufficient permission! Need to be launched by adb (uid 2000) or root (uid 0), but your uid is %d \n", uid);
            System.exit(255);
            return;
        }


        System.out.println("Start GamePad Service. Enter \"exit\" here at any time to exit.");
        sendBinderToAppByStickyBroadcast();//发送binder给APP
        getInputManagerForMode1();


        //加入JVM异常关闭时的处理程序
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isCreated)
                    nativeCloseUInput();
            }
        });

        watchDeviceRotation(); //监测设备是否位于逆向横屏状态，如果是逆向横屏则将陀螺仪数据乘以-1

        try {
            Scanner scanner = new Scanner(System.in);
            //用来保持进程不退出，同时如果用户输入exit则程序退出
            String inline;
            while ((inline = scanner.nextLine()) != null) {
                if (inline.equals("exit"))
                    break;
            }
            scanner.close();
        } catch (Exception unused) {
            //用户使用nohup命令启动，scanner捕捉不到任何输入,会抛出异常。
            while (true) ;
        }
        if (isCreated)
            nativeCloseUInput();
        System.out.println("Stop GamePad Service.\n");
    }

    private static void getInputManagerForMode1() {
        try {
            Method getInstanceMethod = android.hardware.input.InputManager.class.getDeclaredMethod("getInstance");
            im = (android.hardware.input.InputManager) getInstanceMethod.invoke(null);
            if (im == null) return;
            injectInputEventMethod = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEventMethod.setAccessible(true);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        properties = new MotionEvent.PointerProperties[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[0].toolType = 0;
        pointerCoords = new MotionEvent.PointerCoords[1];
        pointerCoords[0] = new MotionEvent.PointerCoords();
        pointerCoords[0].clear();
    }


    private static void watchDeviceRotation() {

        //注册旋转观测器
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "window");
            Class<?> windowManagerStub = Class.forName("android.view.IWindowManager$Stub");
            Method asInterface = windowManagerStub.getMethod("asInterface", IBinder.class);
            IInterface windowManager = (IInterface) asInterface.invoke(null, binder);
            if (windowManager == null) {
                System.err.println("Unable to watch rotation. Skip it.");
                return;
            }
            Class<?> cls = windowManager.getClass();
            try {
                invertAll = (int) cls.getMethod("getDefaultDisplayRotation").invoke(windowManager) == 3;
            } catch (NoSuchMethodException unused) {
                invertAll = (int) cls.getMethod("getRotation").invoke(windowManager) == 3;
            }
            //新建旋转观测器
            IRotationWatcher rotationWatcher = new IRotationWatcher.Stub() {
                @Override
                public void onRotationChanged(int rotation) throws RemoteException {
                    try {
                        invertAll = rotation == 3;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            try {
                cls.getMethod("watchRotation", IRotationWatcher.class, int.class).invoke(windowManager, rotationWatcher, 0);
            } catch (NoSuchMethodException e) {
                cls.getMethod("watchRotation", IRotationWatcher.class).invoke(windowManager, rotationWatcher);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    static native void nativeInputEvent(int xValue, int yValue);

    static native void nativePressTL(boolean pressed);

    static native void nativePressTR(boolean pressed);

    static native void nativePressThumbL(boolean pressed);

    static native void nativePressHat0Y(boolean pressed);

    static native boolean nativeCloseUInput();

    static native boolean nativeCreateUInput();

    private static void sendBinderToAppByStickyBroadcast() {

        try {
            //生成binder
            IBinder binder = new IGamePad.Stub() {

                @Override
                public void changeMode(boolean mode) throws RemoteException {
                    isMode1 = mode;
                }

                @Override
                public boolean getCurrentMode() throws RemoteException {
                    return isMode1;
                }

                @Override
                public void inputEvent(int xValue, int yValue) throws RemoteException {
                    if (invertAll)
                        nativeInputEvent(-xValue, -yValue);
                    else
                        nativeInputEvent(xValue, yValue);
                }

                @Override
                public void inputEventMode1(float xValue, float yValue) throws RemoteException {
                    if (invertAll) {
                        xValue = -xValue;
                        yValue = -yValue;
                    }
                    pointerCoords[0].setAxisValue(MotionEvent.AXIS_RZ, xValue);
                    pointerCoords[0].setAxisValue(MotionEvent.AXIS_Z, yValue);
                    MotionEvent event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 1, properties, pointerCoords,
                            0, 0, 0, 0, 0, 0, InputDevice.SOURCE_JOYSTICK, 0);
                    //InputDevice.SOURCE_JOYSTICK为手柄摇杆
                    try {
                        injectInputEventMethod.invoke(im, event, 0);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                    event.recycle();
                }

                @Override
                public void pressTL(boolean pressed) throws RemoteException {
                    if (isMode1) {
                        final long now = SystemClock.uptimeMillis();
                        KeyEvent TLEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1, 0, 0, 0, 0, 0, InputDevice.SOURCE_JOYSTICK);
                        try {
                            if (pressed) {
                                TLEvent = KeyEvent.changeAction(TLEvent, KeyEvent.ACTION_DOWN);
                                injectInputEventMethod.invoke(im, TLEvent, 0);
                                TLEvent = KeyEvent.changeTimeRepeat(TLEvent, now, 1, KeyEvent.FLAG_LONG_PRESS);
                                injectInputEventMethod.invoke(im, TLEvent, 0);
                            } else {
                                TLEvent = KeyEvent.changeAction(TLEvent, KeyEvent.ACTION_UP);
                                injectInputEventMethod.invoke(im, TLEvent, 0);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else
                        nativePressTL(pressed);
                }

                @Override
                public void pressTR(boolean pressed) throws RemoteException {
                    if (isMode1) {
                        final long now = SystemClock.uptimeMillis();
                        KeyEvent TREvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1, 0, 0, 0, 0, 0, InputDevice.SOURCE_JOYSTICK);
                        try {
                            if (pressed) {
                                TREvent = KeyEvent.changeAction(TREvent, KeyEvent.ACTION_DOWN);
                                injectInputEventMethod.invoke(im, TREvent, 0);
                                TREvent = KeyEvent.changeTimeRepeat(TREvent, now, 1, KeyEvent.FLAG_LONG_PRESS);
                                injectInputEventMethod.invoke(im, TREvent, 0);
                            } else {
                                TREvent = KeyEvent.changeAction(TREvent, KeyEvent.ACTION_UP);
                                injectInputEventMethod.invoke(im, TREvent, 0);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else
                        nativePressTR(pressed);
                }

                @Override
                public void pressThumbL(boolean pressed) throws RemoteException {
                    if (isMode1) {
                        final long now = SystemClock.uptimeMillis();
                        KeyEvent LSEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBL, 0, 0, 0, 0, 0, InputDevice.SOURCE_JOYSTICK);
                        try {
                            if (pressed) {
                                LSEvent = KeyEvent.changeAction(LSEvent, KeyEvent.ACTION_DOWN);
                                injectInputEventMethod.invoke(im, LSEvent, 0);
                                LSEvent = KeyEvent.changeTimeRepeat(LSEvent, now, 1, KeyEvent.FLAG_LONG_PRESS);
                                injectInputEventMethod.invoke(im, LSEvent, 0);
                            } else {
                                LSEvent = KeyEvent.changeAction(LSEvent, KeyEvent.ACTION_UP);
                                injectInputEventMethod.invoke(im, LSEvent, 0);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else
                        nativePressThumbL(pressed);
                }

                @Override
                public void pressHat0Y(boolean pressed) throws RemoteException {
                    nativePressHat0Y(pressed);
                }

                @Override
                public boolean closeUInput() throws RemoteException {
                    if (isCreated)
                        isCreated = !nativeCloseUInput();
                    return !isCreated;
                }

                @Override
                public boolean createUInput() throws RemoteException {
                    if (!isCreated)
                        isCreated = nativeCreateUInput();
                    return isCreated;
                }

                @Override
                public void closeAndExit() throws RemoteException {
                    if (isCreated)
                        isCreated = !nativeCloseUInput();
                    System.out.println("Stop GamePad Service.\n");
                    System.exit(0);
                }
            };
            //把binder填到一个可以用Intent来传递的容器中
            BinderContainer binderContainer = new BinderContainer(binder);
            // 创建 Intent 对象，并将binder作为附加参数
            Intent intent = new Intent("intent.tuoluoyi.sendBinder");
            intent.putExtra("binder", binderContainer);

            Object iActivityManagerObj; // 获取 IActivityManager 类
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                iActivityManagerObj = Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class).invoke(null, "activity"));
            } else {
                Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
                Method getDefaultMethod = activityManagerNativeClass.getMethod("getDefault");
                iActivityManagerObj = getDefaultMethod.invoke(activityManagerNativeClass);
            }
            // 获取 broadcastIntent 方法
            Method broadcastIntentMethod = Class.forName("android.app.IActivityManager").getDeclaredMethod(
                    "broadcastIntent",
                    IApplicationThread.class,
                    Intent.class,
                    String.class,
                    IIntentReceiver.class,
                    int.class,
                    String.class,
                    Bundle.class,
                    String[].class,
                    int.class,
                    Bundle.class,
                    boolean.class,
                    boolean.class,
                    int.class
            );
            // 调用 broadcastIntent 方法，发送粘性广播
            broadcastIntentMethod.invoke(
                    iActivityManagerObj,
                    null,
                    intent,
                    null,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    0,
                    null,
                    false,
                    true,
                    -1
            );

        } catch (
                Exception e) {
            e.printStackTrace();
        }
    }
}
