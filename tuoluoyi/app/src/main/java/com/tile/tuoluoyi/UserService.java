package com.tile.tuoluoyi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.view.InputEvent;
import android.view.KeyEvent;

public class UserService extends IUserService.Stub  {

    private InputManager inputManager;

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public void InputTouch(InputEvent inputEvent, int injectMode) {
            if (inputManager == null) {
                try {
                    Method getInstanceMethod = android.hardware.input.InputManager.class.getDeclaredMethod("getInstance");
                    android.hardware.input.InputManager im = (android.hardware.input.InputManager) getInstanceMethod.invoke(null);
                    inputManager = new InputManager(im);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new AssertionError(e);
                }
            }
        inputManager.injectInputEvent(inputEvent, injectMode);
    }


    @Override
    public void InputKey(KeyEvent keyEvent) {
        if (inputManager == null) {
            try {
                Method getInstanceMethod = android.hardware.input.InputManager.class.getDeclaredMethod("getInstance");
                android.hardware.input.InputManager im = (android.hardware.input.InputManager) getInstanceMethod.invoke(null);
                inputManager = new InputManager(im);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError(e);
            }
        }
        inputManager.injectKeyEvent(keyEvent);
    }

    }


