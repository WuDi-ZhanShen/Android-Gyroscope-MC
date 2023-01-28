package com.tile.tuoluoyi;

import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InputManager {

    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private final android.hardware.input.InputManager manager;
    private Method injectInputEventMethod,injectKeyEventMethod;


    public InputManager(android.hardware.input.InputManager manager) {
        this.manager = manager;
    }

    private Method getInjectInputEventMethod() throws NoSuchMethodException {
        if (injectInputEventMethod == null) {
            injectInputEventMethod = manager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        }
        return injectInputEventMethod;
    }

    private Method getInjectKeyEventMethod() throws NoSuchMethodException {
        if (injectKeyEventMethod == null) {
            injectKeyEventMethod = manager.getClass().getMethod("injectKeyEvent", InputEvent.class, int.class);
        }
        return injectKeyEventMethod;
    }

    public boolean injectInputEvent(InputEvent inputEvent, int mode) {
        try {
            Method method = getInjectInputEventMethod();
            return (boolean) method.invoke(manager, inputEvent, mode);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            return false;
        }
    }

    public boolean injectKeyEvent(KeyEvent keyEvent) {
        try {
            Method method = getInjectKeyEventMethod();
            return (boolean) method.invoke(manager, keyEvent);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            return false;
        }
    }
}
