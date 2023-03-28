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
static struct input_event inputEventX, inputEventY, inputEventSYN;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_UserService_createUInput(JNIEnv *env, jobject) {

    uinput_fd = open("/dev/uinput", O_RDWR | O_NDELAY);
    if (uinput_fd < 0) {
        return false;//error process.
    }


    memset(&uinput_dev, 0, sizeof(struct uinput_user_dev));
    strcpy(uinput_dev.name, "Xbox Wireless Controller");

    uinput_dev.id.version = 1;
    //BUS_VIRTUAL 这个设备是虚拟的。对于UInput的使用场景来说，其实这个填什么都行，无所谓
    uinput_dev.id.bustype = BUS_VIRTUAL;
    uinput_dev.id.vendor = 0x1;
    uinput_dev.id.product = 0x1;
    //轴事件的最小值为-32768，最大值为32767。但是为了让正负数值关于0对称，我决定不使用-32768而是用-32767
    //这样设定之后，轴事件就有了最大最小范围，我们再传入轴事件值(必须为int型)时，系统会根据这个最大最小范围将轴事件归一化为-1到1之间。比如传入-16384,系统就会将这个数据处理为-0.5
    uinput_dev.absmin[ABS_X] = -32767;
    uinput_dev.absmax[ABS_X] = 32767;
    uinput_dev.absmin[ABS_Y] = -32767;
    uinput_dev.absmax[ABS_Y] = 32767;
    uinput_dev.absmin[ABS_Z] = -32767;
    uinput_dev.absmax[ABS_Z] = 32767;
    uinput_dev.absmin[ABS_RZ] = -32767;
    uinput_dev.absmax[ABS_RZ] = 32767;
    uinput_dev.absmin[ABS_THROTTLE] = -32767;
    uinput_dev.absmax[ABS_THROTTLE] = 32767;
    uinput_dev.absmin[ABS_BRAKE] = -32767;
    uinput_dev.absmax[ABS_BRAKE] = 32767;
    uinput_dev.absmin[ABS_HAT0X] = -32767;
    uinput_dev.absmax[ABS_HAT0X] = 32767;
    uinput_dev.absmin[ABS_HAT0Y] = -32767;
    uinput_dev.absmax[ABS_HAT0Y] = 32767;


    //声明该设备支持一些按键和一些轴
    ioctl(uinput_fd, UI_SET_EVBIT, EV_SYN);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY);
    ioctl(uinput_fd, UI_SET_EVBIT, EV_MSC);

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
    return true;

}





extern "C"
JNIEXPORT jboolean JNICALL
Java_com_tile_tuoluoyi_UserService_closeUInput(JNIEnv *env, jobject thiz) {
    if (ioctl(uinput_fd, UI_DEV_DESTROY) < 0) {
        return false;//error process.
    }
    if (close(uinput_fd) < 0) {
        return false;//error process.
    }
    return true;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_tile_tuoluoyi_UserService_InputControl(JNIEnv *env, jobject thiz, jint x_value,
                                                jint y_value) {


    inputEventX.value = x_value;
    write(uinput_fd, &inputEventX, sizeof(struct input_event));

    inputEventY.value = y_value;
    write(uinput_fd, &inputEventY, sizeof(struct input_event));

    write(uinput_fd, &inputEventSYN, sizeof(struct input_event));

}