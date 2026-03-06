cmake_minimum_required(VERSION 3.22.1)

project("hyperplay")

message(STATUS "Configuring HyperPlay Native Engine...")
message(STATUS "Android ABI: ${ANDROID_ABI}")

# Set paths
set(CMAKE_CXX_STANDARD 17)

# Add Oboe
message(STATUS "Adding Oboe from: ${CMAKE_CURRENT_SOURCE_DIR}/external/oboe")
add_subdirectory(external/oboe)

# Add Resonance Audio
set(RA_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/resonance_audio)
set(PROJECT_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio)
set(EIGEN3_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/third_party/eigen)
set(PFFFT_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/third_party/pffft)
set(BUILD_RESONANCE_AUDIO_API ON CACHE BOOL "" FORCE)

include(${RA_SOURCE_DIR}/CMakeLists.txt)

add_library(hyperplay SHARED
        native-lib.cpp
        engine/AudioEngine.cpp
        engine/AudioProcessor.cpp
        engine/FFmpegDecoder.cpp
        engine/UsbNativeStreamer.cpp
        $<TARGET_OBJECTS:ResonanceAudioObj>
        $<TARGET_OBJECTS:SadieHrtfsObj>
        $<TARGET_OBJECTS:PffftObj>
)

# Find libraries
find_library(log-lib log)
find_library(android-lib android)

# Set the location of FFmpeg libraries
set(FFMPEG_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

message(STATUS "Checking for FFmpeg libraries in: ${FFMPEG_LIBS_DIR}")

if(EXISTS ${FFMPEG_LIBS_DIR}/libavcodec.so)
    message(STATUS "FFmpeg libraries found for ${ANDROID_ABI}")
    
    # Import FFmpeg libraries
    add_library(avcodec SHARED IMPORTED)
    set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIBS_DIR}/libavcodec.so)

    add_library(avformat SHARED IMPORTED)
    set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIBS_DIR}/libavformat.so)

    add_library(avutil SHARED IMPORTED)
    set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIBS_DIR}/libavutil.so)

    add_library(swresample SHARED IMPORTED)
    set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIBS_DIR}/libswresample.so)

    add_library(swscale SHARED IMPORTED)
    set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIBS_DIR}/libswscale.so)
    
    set(FFMPEG_FOUND TRUE)
else()
    message(WARNING "FFmpeg libraries NOT found for ${ANDROID_ABI}. Native engine will not work on this architecture.")
    set(FFMPEG_FOUND FALSE)
endif()

# Include paths
target_include_directories(hyperplay PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/engine
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/external/oboe/include
        ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/
        ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/resonance_audio/
        ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/third_party/eigen/
        ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/third_party/pffft/
)

# Force include paths for Resonance Audio components
target_include_directories(ResonanceAudioObj PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/)
target_include_directories(SadieHrtfsObj PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/)
target_include_directories(PffftObj PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/external/resonance-audio/)

# Link libraries
target_link_libraries(hyperplay
        oboe
        ${log-lib}
        ${android-lib}
)

if(FFMPEG_FOUND)
    target_link_libraries(hyperplay
            avcodec
            avformat
            avutil
            swresample
            swscale
    )
    target_compile_definitions(hyperplay PRIVATE HAVE_FFMPEG)
endif()
