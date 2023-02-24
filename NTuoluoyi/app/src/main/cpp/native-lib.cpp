#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <linux/uinput.h>
#include <linux/input.h>

static struct uinput_user_dev uinput_dev;
static int uinput_fd;
static struct input_event inputEventX,inputEventY,inputEventSYN;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_UserService_createUInput(
        JNIEnv *env,
        jobject ) {




    uinput_fd = open("/dev/uinput", O_RDWR | O_NDELAY);
    if (uinput_fd < 0) {
        return false;//error process.
    }

    //to set uinput dev
    memset(&uinput_dev, 0, sizeof(struct uinput_user_dev));
    strcpy(uinput_dev.name, "Xbox Wireless Controller");

    uinput_dev.id.version = 1;
    //BUS_VIRTUAL   BUS_USB时对于游戏来说这个设备是真实存在通过usb连接的
    uinput_dev.id.bustype = BUS_VIRTUAL;
    uinput_dev.id.vendor = 0x1;
    uinput_dev.id.product = 0x1;
    //min 和 max必须不同
    uinput_dev.absmin[ABS_X] = -1;
    uinput_dev.absmax[ABS_X] = 1;
    uinput_dev.absmin[ABS_Y] = -1;
    uinput_dev.absmax[ABS_Y] = 1;
    uinput_dev.absmin[ABS_Z] = -8192;
    uinput_dev.absmax[ABS_Z] = 8192;
    uinput_dev.absmin[ABS_RZ] = -8192;
    uinput_dev.absmax[ABS_RZ] = 8192;
    uinput_dev.absmin[ABS_THROTTLE] = -1;
    uinput_dev.absmax[ABS_THROTTLE] = 1;
    uinput_dev.absmin[ABS_BRAKE] = -1;
    uinput_dev.absmax[ABS_BRAKE] = 1;
    uinput_dev.absmin[ABS_HAT0X] = -1;
    uinput_dev.absmax[ABS_HAT0X] = 1;
    uinput_dev.absmin[ABS_HAT0Y] = -1;
    uinput_dev.absmax[ABS_HAT0Y] = 1;


    ioctl(uinput_fd, UI_SET_EVBIT, EV_SYN);

    ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_MSC);


//    int i;
//    for (i = 0; i < 256; i++) {
//        ioctl(uinput_fd, UI_SET_KEYBIT, i);
//    }

    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_THUMBL);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_THUMBR);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_X);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_A);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_Y);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_B);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_SELECT);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_START);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_DPAD_LEFT);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_DPAD_RIGHT);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_DPAD_UP);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_DPAD_DOWN);


    ioctl(uinput_fd, UI_SET_EVBIT, EV_ABS);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_HAT0X);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_HAT0Y);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_X);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_Y);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_Z);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_RZ);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_THROTTLE);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_BRAKE);

    if (write(uinput_fd, &uinput_dev, sizeof(struct uinput_user_dev)) < 0) {
        return false;//error process.
    }

    if (ioctl(uinput_fd, UI_DEV_CREATE) < 0) {
        close(uinput_fd);
        return false;//error process.
    }
    memset(&inputEventX,0,sizeof(struct input_event));
    inputEventX.type = EV_ABS;
    inputEventX.code = ABS_RZ;
    memset(&inputEventY,0,sizeof(struct input_event));
    inputEventY.type = EV_ABS;
    inputEventY.code = ABS_Z;
    memset(&inputEventSYN,0,sizeof(struct input_event));
    inputEventSYN.type = EV_SYN;
    inputEventSYN.code = SYN_REPORT;
    inputEventSYN.value = 0;
    return true;

}





extern "C"
JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_UserService_closeUInput(JNIEnv *env, jobject thiz) {
    if (close(uinput_fd) < 0) {
        return false;//error process.
    }
    return true;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_UserService_InputControl(JNIEnv *env, jobject thiz, jfloat x_value,jfloat y_value) {


    gettimeofday(&inputEventX.time,nullptr);
    gettimeofday(&inputEventY.time,nullptr);
    gettimeofday(&inputEventSYN.time,nullptr);


    inputEventX.value = x_value;
    write(uinput_fd,&inputEventX,sizeof(struct input_event));


    inputEventY.value = y_value;
    write(uinput_fd,&inputEventY,sizeof(struct input_event));


    write(uinput_fd,&inputEventSYN,sizeof(struct input_event));

}