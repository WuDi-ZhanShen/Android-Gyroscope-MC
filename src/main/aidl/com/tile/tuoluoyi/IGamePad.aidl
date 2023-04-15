package com.tile.tuoluoyi;

interface IGamePad {

    void changeMode(boolean isMode1);

    boolean getCurrentMode();

    void inputEvent(int xValue,int yValue);

    void inputEventMode1(float xValue,float yValue);

    void pressTL(boolean pressed);

    void pressTR(boolean pressed);

    void pressThumbL(boolean pressed);

    void pressHat0Y(boolean pressed);

    boolean createUInput();

    boolean closeUInput();

    void closeAndExit();
}