LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp-permissions-com.dumbermini.apps.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := initial-package-stopped-states-com.dumbermini.apps.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/sysconfig
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := more_apps
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_PREBUILT_MODULE_FILE := $(call intermediates-dir-for,APPS,more_apps)/native-release.apk
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRODUCT_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_MULTILIB := first
LOCAL_OPTIONAL_USES_LIBRARIES := androidx.window.extensions androidx.window.sidecar
LOCAL_PREBUILT_JNI_LIBS_arm64 := @lib/arm64-v8a/libandroidx.graphics.path.so @lib/arm64-v8a/libdatastore_shared_counter.so
LOCAL_PREBUILT_JNI_LIBS_arm := @lib/armeabi-v7a/libandroidx.graphics.path.so @lib/armeabi-v7a/libdatastore_shared_counter.so
LOCAL_PREBUILT_JNI_LIBS_x86 := @lib/x86/libandroidx.graphics.path.so @lib/x86/libdatastore_shared_counter.so
LOCAL_PREBUILT_JNI_LIBS_x86_64 := @lib/x86_64/libandroidx.graphics.path.so @lib/x86_64/libdatastore_shared_counter.so
LOCAL_REQUIRED_MODULES := \
    privapp-permissions-com.dumbermini.apps.xml \
    initial-package-stopped-states-com.dumbermini.apps.xml
include $(BUILD_PREBUILT)

# All application sources and pinned tools participate in the ROM dependency graph.
more_apps_native_apk := $(call intermediates-dir-for,APPS,more_apps)/native-release.apk
more_apps_native_inputs := $(shell find $(LOCAL_PATH)/app/src/main $(LOCAL_PATH)/native-rom -type f -o -type d) \
    $(LOCAL_PATH)/app/proguard.pro $(LOCAL_PATH)/Android.mk
$(more_apps_native_apk): PRIVATE_MORE_APPS_PATH := $(LOCAL_PATH)
$(more_apps_native_apk): $(more_apps_native_inputs)
	@echo "Compiling More Apps from source (standalone Kotlin/KSP/Android tools)"
	$(hide) /usr/bin/python3 $(PRIVATE_MORE_APPS_PATH)/native-rom/build.py --source $(PRIVATE_MORE_APPS_PATH) --out $(@D)/native --apk $@
.KATI_RESTAT: $(more_apps_native_apk)
