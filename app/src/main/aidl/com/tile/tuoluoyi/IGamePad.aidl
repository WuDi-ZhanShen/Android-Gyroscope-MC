package com.tile.tuoluoyi;

interface IGamePad {

    void changeMode(int mode);

    int getCurrentMode();

    void inputEvent(float xValue,float yValue);

    void pressTL(boolean pressed);

    void pressTR(boolean pressed);

    void pressThumbL(boolean pressed);

    boolean create();

    boolean close();

    void closeAndExit();

    void syncPrefs(boolean invX,boolean invY,int sensityX,int sensityY);
}