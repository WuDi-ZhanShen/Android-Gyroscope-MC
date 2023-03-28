package com.tile.tuoluoyi;



interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    void InputEvent(int xValue,int yValue) = 2;

    boolean CreateUInput() = 4;

    boolean CloseUInput() = 5;

}