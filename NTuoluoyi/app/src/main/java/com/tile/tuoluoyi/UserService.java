package com.tile.tuoluoyi;

public class UserService extends IUserService.Stub {

    // Used to load the 'tuoluoyi' library on application startup.
    static {
        System.loadLibrary("tuoluoyi");
    }


    public native boolean createUInput();

    public native boolean closeUInput();

    public native void InputControl(int xValue, int yValue);

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        System.exit(0);
    }


    @Override
    public boolean CreateUInput() {
        return createUInput();
    }

    @Override
    public boolean CloseUInput() {
        return closeUInput();
    }

    @Override
    public void InputEvent(int xValue, int yValue) {
        InputControl(xValue, yValue);
    }



}


