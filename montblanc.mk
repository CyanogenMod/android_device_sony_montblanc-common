$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_full.mk)

$(call inherit-product, device/sony/montblanc-common/recovery/recovery.mk)

DEVICE_PACKAGE_OVERLAYS += device/sony/montblanc-common/overlay

# Permissions
PRODUCT_COPY_FILES += \
    frameworks/base/data/etc/android.hardware.camera.flash-autofocus.xml:system/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/base/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/base/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
    frameworks/base/data/etc/android.hardware.nfc.xml:system/etc/permissions/android.hardware.nfc.xml \
    frameworks/base/data/etc/android.hardware.sensor.accelerometer.xml:system/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/base/data/etc/android.hardware.sensor.compass.xml:system/etc/permissions/android.hardware.sensor.compass.xml \
    frameworks/base/data/etc/android.hardware.sensor.gyroscope.xml:system/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/base/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/base/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/base/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.distinct.xml \
    frameworks/base/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/base/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/base/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
    frameworks/base/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/base/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/base/data/etc/android.software.sip.voip.xml:system/etc/permissions/android.software.sip.voip.xml \
    frameworks/base/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
    packages/wallpapers/LivePicker/android.software.live_wallpaper.xml:system/etc/permissions/android.software.live_wallpaper.xml

# Configs
PRODUCT_COPY_FILES += \
    device/sony/montblanc-common/config/egl.cfg:system/lib/egl/egl.cfg \
    device/sony/montblanc-common/config/asound.conf:system/etc/asound.conf \
    device/sony/montblanc-common/config/dbus.conf:system/etc/dbus.conf \
    device/sony/montblanc-common/config/sysmon.cfg:system/etc/sysmon.cfg \
    device/sony/montblanc-common/config/01stesetup:system/etc/init.d/01stesetup \
    device/sony/montblanc-common/config/wpa_supplicant.conf:system/etc/wifi/wpa_supplicant.conf

# Filesystem management tools
PRODUCT_PACKAGES += \
    make_ext4fs \
    setup_fs

# light package
PRODUCT_PACKAGES += \
   lights.montblanc

# Bluetooth
PRODUCT_PACKAGES += \
   STEBluetooth

# Misc
PRODUCT_PACKAGES += \
   com.android.future.usb.accessory

# NFC
#PRODUCT_PACKAGES += \
#   libnfc \
#   libnfc_jni \
#   Nfc \
#   Tag

# We have enough storage space to hold precise GC data
PRODUCT_TAGS += dalvik.gc.type-precise

PRODUCT_BUILD_PROP_OVERRIDES += BUILD_UTC_DATE=0

# Custom init / uevent
PRODUCT_COPY_FILES += \
    device/sony/montblanc-common/config/init.rc:root/init.rc \
    device/sony/montblanc-common/config/init.st-ericsson.rc:root/init.st-ericsson.rc \
    device/sony/montblanc-common/config/ueventd.st-ericsson.rc:root/ueventd.st-ericsson.rc

# Recovery bootstrap script
PRODUCT_COPY_FILES += \
    device/sony/montblanc-common/recovery/bootrec:root/sbin/bootrec \
    device/sony/montblanc-common/recovery/usbid_init.sh:root/sbin/usbid_init.sh \
    device/sony/montblanc-common/recovery/postrecoveryboot.sh:root/sbin/postrecoveryboot.sh


# HW Configs
PRODUCT_COPY_FILES += \
    device/sony/montblanc-common/config/omxloaders:system/etc/omxloaders \
    device/sony/montblanc-common/config/ril_config:system/etc/ril_config \
    device/sony/montblanc-common/config/install_wlan:system/bin/install_wlan \
    device/sony/montblanc-common/config/ste_modem.sh:system/etc/ste_modem.sh

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mass_storage,adb \
    wifi.interface=wlan0
