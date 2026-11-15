cmake_minimum_required(VERSION 3.22.1)
project(accelinject CXX)

add_library(accelinject SHARED library_inject.cpp inject_jni.cpp)
target_compile_features(accelinject PRIVATE cxx_std_17)
target_link_libraries(accelinject log dl)