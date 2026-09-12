# 旧版 CMake 对 < 3.5 的兼容策略已被新工具链移除，提升到 3.10.2 保证与新 NDK/CMake 兼容
cmake_minimum_required(VERSION 3.10.2)

# 16KB 页面大小适配：强制共享库 LOAD 段按 16KB（16384）对齐。
# 该变量会被下方 add_subdirectory 的 aes / argon2 继承，
# 使 libaes.so 与 libargon2.so 在 16KB 页面设备上可被正常加载。
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_subdirectory(aes)
add_subdirectory(argon2)
