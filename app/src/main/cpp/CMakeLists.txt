cmake_minimum_required(VERSION 3.18)
project(openphone_keyboard_rime LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(ENABLE_LOGGING OFF CACHE BOOL "" FORCE)
set(ENABLE_EXTERNAL_PLUGINS OFF CACHE BOOL "" FORCE)

set(OPENKEYBOARD_BOOST_VERSION "1.89.0")
set(OPENKEYBOARD_BOOST_ARCHIVE "${CMAKE_BINARY_DIR}/boost-${OPENKEYBOARD_BOOST_VERSION}.tar.xz")
set(OPENKEYBOARD_BOOST_ROOT "${CMAKE_BINARY_DIR}/boost-${OPENKEYBOARD_BOOST_VERSION}")
if(NOT EXISTS "${OPENKEYBOARD_BOOST_ROOT}")
    if(NOT EXISTS "${OPENKEYBOARD_BOOST_ARCHIVE}")
        file(
            DOWNLOAD
            "https://github.com/boostorg/boost/releases/download/boost-${OPENKEYBOARD_BOOST_VERSION}/boost-${OPENKEYBOARD_BOOST_VERSION}-cmake.tar.xz"
            "${OPENKEYBOARD_BOOST_ARCHIVE}"
            EXPECTED_HASH SHA256=67acec02d0d118b5de9eb441f5fb707b3a1cdd884be00ca24b9a73c995511f74
            SHOW_PROGRESS)
    endif()
    file(ARCHIVE_EXTRACT INPUT "${OPENKEYBOARD_BOOST_ARCHIVE}" DESTINATION "${CMAKE_BINARY_DIR}")
endif()

set(BOOST_INCLUDE_LIBRARIES algorithm crc dll interprocess range regex scope_exit signals2 utility uuid)
add_subdirectory("${OPENKEYBOARD_BOOST_ROOT}" boost EXCLUDE_FROM_ALL)

set(WITH_GFLAGS OFF CACHE BOOL "" FORCE)
set(WITH_GTEST OFF CACHE BOOL "" FORCE)
set(WITH_UNWIND OFF CACHE BOOL "" FORCE)
add_subdirectory(librime/deps/glog glog)

set(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(YAML_CPP_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(librime/deps/yaml-cpp yaml-cpp)

set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(LEVELDB_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(librime/deps/leveldb leveldb)

set(BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(ENABLE_NATIVE_CODE OFF CACHE BOOL "" FORCE)
add_subdirectory(librime/deps/marisa-trie marisa-trie)

set(USE_SYSTEM_MARISA ON CACHE BOOL "" FORCE)
set(LIBMARISA marisa)
set(ENABLE_GTEST OFF CACHE BOOL "" FORCE)
set(BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
add_subdirectory(librime/deps/opencc opencc)
file(GLOB opencc_headers
    librime/deps/opencc/src/*.hpp
    "${CMAKE_BINARY_DIR}/opencc/src/opencc_config.h")
file(COPY ${opencc_headers} DESTINATION "${CMAKE_BINARY_DIR}/include/opencc")

set(X11Keysym "${CMAKE_CURRENT_SOURCE_DIR}/librime/include")
add_subdirectory(librime)
target_compile_options(rime-static PRIVATE -Wno-error=deprecated-declarations)

add_library(openphone_rime SHARED rime_bridge.cpp)
target_include_directories(openphone_rime PRIVATE
    "${CMAKE_BINARY_DIR}/librime/src"
    "${CMAKE_CURRENT_SOURCE_DIR}/librime/src"
    "${CMAKE_CURRENT_SOURCE_DIR}/librime/include"
    "${Opencc_INCLUDE_PATH}")
target_link_libraries(openphone_rime rime-static ${Opencc_LIBRARY})
