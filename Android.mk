# Copyright 2011 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES := telephony-common
#LOCAL_JAVA_LIBRARIES += telephony-asus

LOCAL_STATIC_JAVA_LIBRARIES += android_support_v13

LOCAL_PACKAGE_NAME := AsusCellBroadcast
LOCAL_OVERRIDES_PACKAGES := CellBroadcastReceiver
ifeq ($(strip $(MTK_CMAS_SUPPORT)),yes)
LOCAL_OVERRIDES_PACKAGES := CMASReceiver \
                            CmasEM
endif
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

# This finds and builds the test apk as well, so a single make does both.
include $(call all-makefiles-under,$(LOCAL_PATH))

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := android_support_v13:libs/android-support-v13.jar

include $(BUILD_MULTI_PREBUILT)

# +++ andrew_tu
ifeq ($(TARGET_BUILD_VARIANT), user)
    DEBUG := 0
else
    DEBUG := 1
endif
$(warning DEBUG Value: $(DEBUG))

ADDITIONAL_BUILD_PROPERTIES += \
    persist.asus.cb.debug=$(DEBUG)
# --- andrew_tu
