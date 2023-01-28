package com.tile.tuoluoyi;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    void InputTouch(in InputEvent B,int x) = 2;

    void InputKey(in KeyEvent keyEvent) = 3;

}