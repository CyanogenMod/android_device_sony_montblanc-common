LOCAL_PATH := $(call my-dir)

ifeq ($(TARGET_BOOTLOADER_BOARD_NAME),montblanc)
    include $(call all-subdir-makefiles,$(LOCAL_PATH))
endif
