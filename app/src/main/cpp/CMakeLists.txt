# Upscaling (Real-ESRGAN et Real-CUGAN, ncnn Vulkan) : bibliothèque JNI libbullarr_upscale.so.
cmake_minimum_required(VERSION 3.22)
project(bullarr_upscale CXX)
set(CMAKE_CXX_STANDARD 17)

set(NCNN_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/ncnn)
if(NOT EXISTS ${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
  message(FATAL_ERROR "ncnn absent : lancez scripts/fetch-ncnn.sh")
endif()
set(ncnn_DIR ${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# Shaders de pré/post-traitement compilés en SPIR-V avec le glslc du NDK.
file(GLOB GLSLC_FOUND ${ANDROID_NDK}/shader-tools/*/glslc)
list(GET GLSLC_FOUND 0 GLSLC)
set(SPV_HEADERS)
foreach(SH realesrgan_preproc realesrgan_postproc realesrgan_preproc_tta realesrgan_postproc_tta)
  foreach(V "" "_fp16s" "_int8s")
    if(V STREQUAL "_fp16s")
      set(DEFS -DNCNN_fp16_storage=1)
    elseif(V STREQUAL "_int8s")
      set(DEFS -DNCNN_fp16_storage=1 -DNCNN_int8_storage=1)
    else()
      set(DEFS)
    endif()
    set(OUT ${CMAKE_CURRENT_BINARY_DIR}/${SH}${V}.spv.hex.h)
    add_custom_command(OUTPUT ${OUT}
      COMMAND ${GLSLC} ${DEFS} -mfmt=num -o ${OUT} ${CMAKE_CURRENT_SOURCE_DIR}/${SH}.comp
      DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/${SH}.comp VERBATIM)
    list(APPEND SPV_HEADERS ${OUT})
  endforeach()
endforeach()

# Real-CUGAN : shaders embarqués en texte GLSL, compilés à l'exécution par ncnn.
foreach(SH realcugan_preproc realcugan_postproc realcugan_4x_postproc realcugan_preproc_tta realcugan_postproc_tta realcugan_4x_postproc_tta)
  set(OUT ${CMAKE_CURRENT_BINARY_DIR}/${SH}.comp.hex.h)
  add_custom_command(OUTPUT ${OUT}
    COMMAND ${CMAKE_COMMAND} -DSHADER_SRC=${CMAKE_CURRENT_SOURCE_DIR}/${SH}.comp -DSHADER_COMP_HEADER=${OUT}
      -P ${CMAKE_CURRENT_SOURCE_DIR}/generate_shader_comp_header.cmake
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/${SH}.comp VERBATIM)
  list(APPEND SPV_HEADERS ${OUT})
endforeach()

add_library(bullarr_upscale SHARED upscaler_jni.cpp realesrgan.cpp realcugan.cpp ${SPV_HEADERS})
target_include_directories(bullarr_upscale PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(bullarr_upscale ncnn jnigraphics log)
target_link_options(bullarr_upscale PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
