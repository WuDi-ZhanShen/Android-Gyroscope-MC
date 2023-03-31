package com.tile.tuoluoyi;

interface IGamePad {

    void inputEvent(int xValue,int yValue);

    boolean createUInput();

    boolean closeUInput();

    void closeAndExit();
}