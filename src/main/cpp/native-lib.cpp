#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <linux/uinput.h>
#include <linux/input.h>
#include <jni.h>

static struct uinput_user_dev uinput_dev;
static int uinput_fd;
static struct input_event inputEventX, inputEventY, inputEventSYN, inputEventTL, inputEventTR,inputEventThumbL,inputEventHat0Y;

extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativeInputEvent(JNIEnv *env, jclass clazz, jint x_value,
                                                      jint y_value) {

    inputEventX.value = x_value;
    write(uinput_fd, &inputEventX, sizeof(struct input_event));

    inputEventY.value = y_value;
    write(uinput_fd, &inputEventY, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativeCloseUInput(JNIEnv *env, jclass clazz) {
    ioctl(uinput_fd, UI_DEV_DESTROY);
    if (close(uinput_fd) < 0) return false;
    return true;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativeCreateUInput(JNIEnv *env, jclass clazz) {

    if ((uinput_fd = open("/dev/uinput", O_RDWR | O_NDELAY)) < 0) {
        return false;//error process.
    }

    //注册虚拟手柄设备
    memset(&uinput_dev, 0, sizeof(struct uinput_user_dev));
    strcpy(uinput_dev.name, "Xbox Wireless Controller");
    uinput_dev.id.version = 1;
    uinput_dev.id.bustype = BUS_VIRTUAL;
    uinput_dev.id.vendor = 0x1;
    uinput_dev.id.product = 0x1;
    //UInput机制中,轴事件的最小值是-32768，最大值是32767。但是我为了正负对称，就没有把最小值设为-32768。
    //设定好了最小值和最大值的作用是，后续我们发送轴事件时安卓系统会自动将轴事件归一化至-1到1之间。比如后续发一个-666，安卓系统就将这个数据归一化为-666/32767
    uinput_dev.absmin[ABS_X] = -1;
    uinput_dev.absmax[ABS_X] = 1;
    uinput_dev.absmin[ABS_Y] = -1;
    uinput_dev.absmax[ABS_Y] = 1;
    uinput_dev.absmin[ABS_Z] = -32767;
    uinput_dev.absmax[ABS_Z] = 32767;
    uinput_dev.absmin[ABS_RZ] = -32767;
    uinput_dev.absmax[ABS_RZ] = 32767;
    uinput_dev.absmin[ABS_THROTTLE] = -1;
    uinput_dev.absmax[ABS_THROTTLE] = 1;
    uinput_dev.absmin[ABS_BRAKE] = -1;
    uinput_dev.absmax[ABS_BRAKE] = 1;
    uinput_dev.absmin[ABS_HAT0X] = -1;
    uinput_dev.absmax[ABS_HAT0X] = 1;
    uinput_dev.absmin[ABS_HAT0Y] = -1;
    uinput_dev.absmax[ABS_HAT0Y] = 1;

    //ioctl函数用来声明设备所具有的一些按键和轴
    ioctl(uinput_fd, UI_SET_EVBIT, EV_SYN);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_MSC);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_ABS);

    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_THUMBL);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_THUMBR);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_TL);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_TR);
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
    ioctl(uinput_fd, UI_SET_MSCBIT, MSC_SCAN);
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
    memset(&inputEventX, 0, sizeof(struct input_event));
    inputEventX.type = EV_ABS;
    inputEventX.code = ABS_RZ;
    memset(&inputEventY, 0, sizeof(struct input_event));
    inputEventY.type = EV_ABS;
    inputEventY.code = ABS_Z;
    memset(&inputEventSYN, 0, sizeof(struct input_event));
    inputEventSYN.type = EV_SYN;
    inputEventSYN.code = SYN_REPORT;
    inputEventSYN.value = 0;
    memset(&inputEventTL, 0, sizeof(struct input_event));
    inputEventTL.type = EV_KEY;
    inputEventTL.code = BTN_TL;
    memset(&inputEventTR, 0, sizeof(struct input_event));
    inputEventTR.type = EV_KEY;
    inputEventTR.code = BTN_TR;
    memset(&inputEventThumbL, 0, sizeof(struct input_event));
    inputEventThumbL.type = EV_KEY;
    inputEventThumbL.code = BTN_THUMBL;
    memset(&inputEventHat0Y, 0, sizeof(struct input_event));
    inputEventHat0Y.type = EV_ABS;
    inputEventHat0Y.code = ABS_HAT0Y;

    return true;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativePressTL(JNIEnv *env, jclass clazz, jboolean pressed) {
    inputEventTL.value = pressed ? 1 : 0;
    write(uinput_fd, &inputEventTL, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativePressTR(JNIEnv *env, jclass clazz, jboolean pressed) {
    inputEventTR.value = pressed ? 1 : 0;
    write(uinput_fd, &inputEventTR, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativePressThumbL(JNIEnv *env, jclass clazz,
                                                       jboolean pressed) {
    inputEventThumbL.value = pressed ? 1 : 0;
    write(uinput_fd, &inputEventThumbL, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_GamePadNative_nativePressHat0Y(JNIEnv *env, jclass clazz, jboolean pressed) {
    inputEventHat0Y.value = pressed ? -1 : 0;
    write(uinput_fd, &inputEventHat0Y, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));
}