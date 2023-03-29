# Android-Gyroscope-MC
  用陀螺仪玩安卓MC！
# 原理
  利用Linux的UInput机制，使用adb权限或者root权限注册一个虚拟硬件手柄到系统。然后将陀螺仪数据实时转化为虚拟手柄的右摇杆的移动。
# 为什么使用虚拟手柄，而不使用虚拟触控或者虚拟鼠标
  虚拟触控会和真实手指的触控冲突(虚拟触控无法模拟多点触控，问题核心就是无法实时得知10个触控槽位中哪些是有触控的，然后将虚拟触控安排在空闲的第一个槽位)，且虚拟触控划到屏幕边缘时就没办法继续划了。另外，在没有新触控方案的MC版本上，玩家划屏时是不能跳跃、放置、使用物品、攻击的；虚拟触控的划屏自然就会导致玩家坐牢，什么都干不了。
  虚拟鼠标也会和真实手指的触控冲突。鼠标存在时，安卓系统会直接自动屏蔽全部硬件触控，这是一个无解的问题，并且很少有人注意到这个问题。大家一般都是直接接键鼠，然后纯键鼠操作；很少有人试过鼠标和触控同时操作。不过现在看到这里您就可以去试一试了，您将发现鼠标移动时安卓系统会直接屏蔽掉手指触控。
  而虚拟手柄是唯一的不和触控存在冲突的方式。
  
