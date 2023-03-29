package com.tile.tuoluoyi;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import android.app.IApplicationThread;
import android.content.IIntentReceiver;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.util.Scanner;

public class GamePadNative {
    static boolean isCreated = false;

    static {
        System.loadLibrary("tuoluoyi");
    }

    public static void main(String[] args) {
        //check if user has Root permission
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) {
            System.err.printf("Insufficient permission! Need to be launched by adb or root, but you are %d now\n", uid);
            System.exit(255);
            return;
        }
        System.out.println("Start GamePad Service. Enter \"exit\" here at any time to exit.");
        sendBinderToAppByStickyBroadcast();


        //加入JVM异常关闭时的处理程序
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isCreated)
                    nativeCloseUInput();
            }
        });

        try {
            Scanner scanner = new Scanner(System.in);
            //用来保持进程不退出，同时如果用户输入exit则程序退出
            String inline;
            while ((inline = scanner.nextLine()) != null) {
                if (inline.equals("exit"))
                    break;
            }
            scanner.close();
        } catch (Exception e) {
            //用户使用nohup命令启动，scanner捕捉不到任何输入,会抛出异常
            while (true) {
            }
        }
        if (isCreated)
            nativeCloseUInput();
        System.out.println("Stop GamePad Service.\n");
        System.exit(0);
    }

    static native void nativeInputEvent(int xValue, int yValue);

    static native boolean nativeCloseUInput();

    static native boolean nativeCreateUInput();

    private static void sendBinderToAppByStickyBroadcast() {

        try {
            //生成binder
            IBinder binder = new IGamePad.Stub() {
                @Override
                public void inputEvent(int xValue, int yValue) throws RemoteException {
                    nativeInputEvent(xValue, yValue);
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
