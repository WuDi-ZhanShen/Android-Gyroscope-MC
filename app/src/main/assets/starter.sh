pm grant com.tile.tuoluoyi android.permission.WRITE_SECURE_SETTINGS

file_name="GyroNative.dex"
#file_name="Gyro.apk"
file_name1="libtuoluoyi.so"
origin_path="$(dirname "$0")/$file_name"
origin_path1="$(dirname "$0")/$file_name1"

cache_dir="/data/local/tmp"
target_path1="$cache_dir/libtuoluoyi.so"
if [[ -e $origin_path1 ]]; then
  cp -rf "$origin_path1" $target_path1
  chmod +x $target_path1
  export CLASSPATH="$origin_path"
  nohup app_process -Djava.library.path="$cache_dir" / com.tile.tuoluoyi.GamePadNative >/dev/null 2>&1 &
fi
