/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Checks if an application wants to use permissions that can only be used by system applications.
 */
public class SystemPermissionsDetector extends Detector implements XmlScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "ProtectedPermissions",
                    "Using system app permission",
                    "Permissions with the protection level `signature`, `privileged` or `signatureOrSystem` "
                            + "are only granted to system apps. If an app is a regular non-system app, it will "
                            + "never be able to use these permissions.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(SystemPermissionsDetector.class, Scope.MANIFEST_SCOPE));

    // List of permissions have the protection levels signature, privileged or systemOrSignature.
    // This list must be sorted alphabetically.
    @SuppressWarnings("WrongTerminology") // external constants: can't rename
    @VisibleForTesting
    static final String[] SYSTEM_PERMISSIONS =
            new String[] {
                "android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE",
                "android.permission.ACCESS_ALL_EXTERNAL_STORAGE",
                "android.permission.ACCESS_AMBIENT_LIGHT_STATS",
                "android.permission.ACCESS_BLOBS_ACROSS_USERS",
                "android.permission.ACCESS_BROADCAST_RADIO",
                "android.permission.ACCESS_CACHE_FILESYSTEM",
                "android.permission.ACCESS_CHECKIN_PROPERTIES",
                "android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY",
                "android.permission.ACCESS_CONTEXT_HUB",
                "android.permission.ACCESS_DRM_CERTIFICATES",
                "android.permission.ACCESS_EPHEMERAL_APPS",
                "android.permission.ACCESS_FM_RADIO",
                "android.permission.ACCESS_IMS_CALL_SERVICE",
                "android.permission.ACCESS_INPUT_FLINGER",
                "android.permission.ACCESS_INSTANT_APPS",
                "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE",
                "android.permission.ACCESS_LOCUS_ID_USAGE_STATS",
                "android.permission.ACCESS_LOWPAN_STATE",
                "android.permission.ACCESS_MESSAGES_ON_ICC",
                "android.permission.ACCESS_MOCK_LOCATION",
                "android.permission.ACCESS_MTP",
                "android.permission.ACCESS_NETWORK_CONDITIONS",
                "android.permission.ACCESS_NOTIFICATIONS",
                "android.permission.ACCESS_PDB_STATE",
                "android.permission.ACCESS_SHARED_LIBRARIES",
                "android.permission.ACCESS_SHORTCUTS",
                "android.permission.ACCESS_SURFACE_FLINGER",
                "android.permission.ACCESS_TUNED_INFO",
                "android.permission.ACCESS_TV_DESCRAMBLER",
                "android.permission.ACCESS_TV_TUNER",
                "android.permission.ACCESS_UCE_OPTIONS_SERVICE",
                "android.permission.ACCESS_UCE_PRESENCE_SERVICE",
                "android.permission.ACCESS_USB",
                "android.permission.ACCESS_VIBRATOR_STATE",
                "android.permission.ACCESS_VOICE_INTERACTION_SERVICE",
                "android.permission.ACCESS_VR_MANAGER",
                "android.permission.ACCESS_VR_STATE",
                "android.permission.ACCOUNT_MANAGER",
                "android.permission.ACTIVITY_EMBEDDING",
                "android.permission.ACT_AS_PACKAGE_FOR_ACCESSIBILITY",
                "android.permission.ADD_SYSTEM_SERVICE",
                "android.permission.ADD_TRUSTED_DISPLAY",
                "android.permission.ADJUST_RUNTIME_PERMISSIONS_POLICY",
                "android.permission.ALLOCATE_AGGRESSIVE",
                "android.permission.ALLOW_ANY_CODEC_FOR_PLAYBACK",
                "android.permission.AMBIENT_WALLPAPER",
                "android.permission.APPROVE_INCIDENT_REPORTS",
                "android.permission.ASEC_ACCESS",
                "android.permission.ASEC_CREATE",
                "android.permission.ASEC_DESTROY",
                "android.permission.ASEC_MOUNT_UNMOUNT",
                "android.permission.ASEC_RENAME",
                "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY",
                "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY_BY_PORT",
                "android.permission.BACKUP",
                "android.permission.BACKUP_DATA",
                "android.permission.BATTERY_PREDICTION",
                "android.permission.BATTERY_STATS",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.BIND_APPWIDGET",
                "android.permission.BIND_ATTENTION_SERVICE",
                "android.permission.BIND_AUGMENTED_AUTOFILL_SERVICE",
                "android.permission.BIND_AUTOFILL",
                "android.permission.BIND_AUTOFILL_FIELD_CLASSIFICATION_SERVICE",
                "android.permission.BIND_AUTOFILL_SERVICE",
                "android.permission.BIND_CACHE_QUOTA_SERVICE",
                "android.permission.BIND_CALL_DIAGNOSTIC_SERVICE",
                "android.permission.BIND_CALL_REDIRECTION_SERVICE",
                "android.permission.BIND_CALL_SERVICE",
                "android.permission.BIND_CARRIER_MESSAGING_CLIENT_SERVICE",
                "android.permission.BIND_CARRIER_MESSAGING_SERVICE",
                "android.permission.BIND_CARRIER_SERVICES",
                "android.permission.BIND_CELL_BROADCAST_SERVICE",
                "android.permission.BIND_CHOOSER_TARGET_SERVICE",
                "android.permission.BIND_COMPANION_DEVICE_MANAGER_SERVICE",
                "android.permission.BIND_COMPANION_DEVICE_SERVICE",
                "android.permission.BIND_CONDITION_PROVIDER_SERVICE",
                "android.permission.BIND_CONNECTION_SERVICE",
                "android.permission.BIND_CONTENT_CAPTURE_SERVICE",
                "android.permission.BIND_CONTENT_SUGGESTIONS_SERVICE",
                "android.permission.BIND_CONTROLS",
                "android.permission.BIND_DEVICE_ADMIN",
                "android.permission.BIND_DIRECTORY_SEARCH",
                "android.permission.BIND_DISPLAY_HASHING_SERVICE",
                "android.permission.BIND_DOMAIN_VERIFICATION_AGENT",
                "android.permission.BIND_DREAM_SERVICE",
                "android.permission.BIND_EUICC_SERVICE",
                "android.permission.BIND_EXPLICIT_HEALTH_CHECK_SERVICE",
                "android.permission.BIND_EXTERNAL_STORAGE_SERVICE",
                "android.permission.BIND_FINANCIAL_SMS_SERVICE",
                "android.permission.BIND_GBA_SERVICE",
                "android.permission.BIND_HOTWORD_DETECTION_SERVICE",
                "android.permission.BIND_IMS_SERVICE",
                "android.permission.BIND_INCALL_SERVICE",
                "android.permission.BIND_INLINE_SUGGESTION_RENDER_SERVICE",
                "android.permission.BIND_INPUT_METHOD",
                "android.permission.BIND_INTENT_FILTER_VERIFIER",
                "android.permission.BIND_JOB_SERVICE",
                "android.permission.BIND_KEYGUARD_APPWIDGET",
                "android.permission.BIND_MIDI_DEVICE_SERVICE",
                "android.permission.BIND_MUSIC_RECOGNITION_SERVICE",
                "android.permission.BIND_NETWORK_RECOMMENDATION_SERVICE",
                "android.permission.BIND_NFC_SERVICE",
                "android.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE",
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                "android.permission.BIND_NOTIFICATION_RANKER_SERVICE",
                "android.permission.BIND_PACKAGE_VERIFIER",
                "android.permission.BIND_PHONE_ACCOUNT_SUGGESTION_SERVICE",
                "android.permission.BIND_PRINT_RECOMMENDATION_SERVICE",
                "android.permission.BIND_PRINT_SERVICE",
                "android.permission.BIND_PRINT_SPOOLER_SERVICE",
                "android.permission.BIND_QUICK_ACCESS_WALLET_SERVICE",
                "android.permission.BIND_QUICK_SETTINGS_TILE",
                "android.permission.BIND_REMOTEVIEWS",
                "android.permission.BIND_REMOTE_DISPLAY",
                "android.permission.BIND_RESOLVER_RANKER_SERVICE",
                "android.permission.BIND_RESUME_ON_REBOOT_SERVICE",
                "android.permission.BIND_ROTATION_RESOLVER_SERVICE",
                "android.permission.BIND_ROUTE_PROVIDER",
                "android.permission.BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE",
                "android.permission.BIND_SCREENING_SERVICE",
                "android.permission.BIND_SETTINGS_SUGGESTIONS_SERVICE",
                "android.permission.BIND_SOUND_TRIGGER_DETECTION_SERVICE",
                "android.permission.BIND_TELECOM_CONNECTION_SERVICE",
                "android.permission.BIND_TELEPHONY_DATA_SERVICE",
                "android.permission.BIND_TELEPHONY_NETWORK_SERVICE",
                "android.permission.BIND_TEXTCLASSIFIER_SERVICE",
                "android.permission.BIND_TEXT_SERVICE",
                "android.permission.BIND_TIME_ZONE_PROVIDER_SERVICE",
                "android.permission.BIND_TRANSLATION_SERVICE",
                "android.permission.BIND_TRUST_AGENT",
                "android.permission.BIND_TV_INPUT",
                "android.permission.BIND_TV_REMOTE_SERVICE",
                "android.permission.BIND_VISUAL_VOICEMAIL_SERVICE",
                "android.permission.BIND_VOICE_INTERACTION",
                "android.permission.BIND_VPN_SERVICE",
                "android.permission.BIND_VR_LISTENER_SERVICE",
                "android.permission.BIND_WALLPAPER",
                "android.permission.BLUETOOTH_MAP",
                "android.permission.BLUETOOTH_PRIVILEGED",
                "android.permission.BLUETOOTH_STACK",
                "android.permission.BRICK",
                "android.permission.BRIGHTNESS_SLIDER_USAGE",
                "android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS",
                "android.permission.BROADCAST_NETWORK_PRIVILEGED",
                "android.permission.BROADCAST_PACKAGE_REMOVED",
                "android.permission.BROADCAST_SCORE_NETWORKS",
                "android.permission.BROADCAST_SMS",
                "android.permission.BROADCAST_WAP_PUSH",
                "android.permission.CACHE_CONTENT",
                "android.permission.CALL_PRIVILEGED",
                "android.permission.CAMERA_DISABLE_TRANSMIT_LED",
                "android.permission.CAMERA_INJECT_EXTERNAL_CAMERA",
                "android.permission.CAMERA_OPEN_CLOSE_LISTENER",
                "android.permission.CAMERA_SEND_SYSTEM_EVENTS",
                "android.permission.CAPTURE_AUDIO_HOTWORD",
                "android.permission.CAPTURE_AUDIO_OUTPUT",
                "android.permission.CAPTURE_BLACKOUT_CONTENT",
                "android.permission.CAPTURE_MEDIA_OUTPUT",
                "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT",
                "android.permission.CAPTURE_TUNER_AUDIO_INPUT",
                "android.permission.CAPTURE_TV_INPUT",
                "android.permission.CAPTURE_VIDEO_OUTPUT",
                "android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT",
                "android.permission.CARRIER_FILTER_SMS",
                "android.permission.CHANGE_ACCESSIBILITY_VOLUME",
                "android.permission.CHANGE_APP_IDLE_STATE",
                "android.permission.CHANGE_BACKGROUND_DATA_SETTING",
                "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
                "android.permission.CHANGE_CONFIGURATION",
                "android.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST",
                "android.permission.CHANGE_HDMI_CEC_ACTIVE_SOURCE",
                "android.permission.CHANGE_LOWPAN_STATE",
                "android.permission.CHANGE_NETWORK_STATE",
                "android.permission.CHANGE_OVERLAY_PACKAGES",
                "android.permission.CLEAR_APP_CACHE",
                "android.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS",
                "android.permission.CLEAR_APP_USER_DATA",
                "android.permission.CLEAR_FREEZE_PERIOD",
                "android.permission.COMPANION_APPROVE_WIFI_CONNECTIONS",
                "android.permission.CONFIGURE_DISPLAY_BRIGHTNESS",
                "android.permission.CONFIGURE_DISPLAY_COLOR_MODE",
                "android.permission.CONFIGURE_DISPLAY_COLOR_TRANSFORM",
                "android.permission.CONFIGURE_INTERACT_ACROSS_PROFILES",
                "android.permission.CONFIGURE_WIFI_DISPLAY",
                "android.permission.CONFIRM_FULL_BACKUP",
                "android.permission.CONNECTIVITY_INTERNAL",
                "android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS",
                "android.permission.CONTROL_ALWAYS_ON_VPN",
                "android.permission.CONTROL_DEVICE_LIGHTS",
                "android.permission.CONTROL_DEVICE_STATE",
                "android.permission.CONTROL_DISPLAY_BRIGHTNESS",
                "android.permission.CONTROL_DISPLAY_COLOR_TRANSFORMS",
                "android.permission.CONTROL_DISPLAY_SATURATION",
                "android.permission.CONTROL_INCALL_EXPERIENCE",
                "android.permission.CONTROL_KEYGUARD",
                "android.permission.CONTROL_KEYGUARD_SECURE_NOTIFICATIONS",
                "android.permission.CONTROL_LOCATION_UPDATES",
                "android.permission.CONTROL_OEM_PAID_NETWORK_PREFERENCE",
                "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS",
                "android.permission.CONTROL_UI_TRACING",
                "android.permission.CONTROL_VPN",
                "android.permission.CONTROL_WIFI_DISPLAY",
                "android.permission.COPY_PROTECTED_DATA",
                "android.permission.CREATE_USERS",
                "android.permission.CRYPT_KEEPER",
                "android.permission.DELETE_CACHE_FILES",
                "android.permission.DELETE_PACKAGES",
                "android.permission.DEVICE_POWER",
                "android.permission.DIAGNOSTIC",
                "android.permission.DISABLE_HIDDEN_API_CHECKS",
                "android.permission.DISABLE_INPUT_DEVICE",
                "android.permission.DISABLE_SYSTEM_SOUND_EFFECTS",
                "android.permission.DISPATCH_NFC_MESSAGE",
                "android.permission.DISPATCH_PROVISIONING_MESSAGE",
                "android.permission.DOMAIN_VERIFICATION_AGENT",
                "android.permission.DUMP",
                "android.permission.DVB_DEVICE",
                "android.permission.ENABLE_TEST_HARNESS_MODE",
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.EXEMPT_FROM_AUDIO_RECORD_RESTRICTIONS",
                "android.permission.FACTORY_TEST",
                "android.permission.FILTER_EVENTS",
                "android.permission.FORCE_BACK",
                "android.permission.FORCE_DEVICE_POLICY_MANAGER_LOGS",
                "android.permission.FORCE_PERSISTABLE_URI_PERMISSIONS",
                "android.permission.FORCE_STOP_PACKAGES",
                "android.permission.FOTA_UPDATE",
                "android.permission.FRAME_STATS",
                "android.permission.FREEZE_SCREEN",
                "android.permission.GET_ACCOUNTS_PRIVILEGED",
                "android.permission.GET_APP_GRANTED_URI_PERMISSIONS",
                "android.permission.GET_APP_OPS_STATS",
                "android.permission.GET_DETAILED_TASKS",
                "android.permission.GET_INTENT_SENDER_INTENT",
                "android.permission.GET_PACKAGE_IMPORTANCE",
                "android.permission.GET_PASSWORD",
                "android.permission.GET_PEOPLE_TILE_PREVIEW",
                "android.permission.GET_PROCESS_STATE_AND_OOM_SCORE",
                "android.permission.GET_RUNTIME_PERMISSIONS",
                "android.permission.GET_TOP_ACTIVITY_INFO",
                "android.permission.GLOBAL_SEARCH",
                "android.permission.GLOBAL_SEARCH_CONTROL",
                "android.permission.GRANT_PROFILE_OWNER_DEVICE_IDS_ACCESS",
                "android.permission.GRANT_REVOKE_PERMISSIONS",
                "android.permission.GRANT_RUNTIME_PERMISSIONS",
                "android.permission.GRANT_RUNTIME_PERMISSIONS_TO_TELEPHONY_DEFAULTS",
                "android.permission.HANDLE_CAR_MODE_CHANGES",
                "android.permission.HARDWARE_TEST",
                "android.permission.HDMI_CEC",
                "android.permission.HIDE_NON_SYSTEM_OVERLAY_WINDOWS",
                "android.permission.INJECT_EVENTS",
                "android.permission.INPUT_CONSUMER",
                "android.permission.INSTALL_DYNAMIC_SYSTEM",
                "android.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS",
                "android.permission.INSTALL_LOCATION_PROVIDER",
                "android.permission.INSTALL_LOCATION_TIME_ZONE_PROVIDER_SERVICE",
                "android.permission.INSTALL_PACKAGES",
                "android.permission.INSTALL_PACKAGE_UPDATES",
                "android.permission.INSTALL_SELF_UPDATES",
                "android.permission.INSTALL_TEST_ONLY_PACKAGE",
                "android.permission.INSTANT_APP_FOREGROUND_SERVICE",
                "android.permission.INTENT_FILTER_VERIFICATION_AGENT",
                "android.permission.INTERACT_ACROSS_PROFILES",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERNAL_DELETE_CACHE_FILES",
                "android.permission.INTERNAL_SYSTEM_WINDOW",
                "android.permission.INVOKE_CARRIER_SETUP",
                "android.permission.KEEP_UNINSTALLED_PACKAGES",
                "android.permission.KEYPHRASE_ENROLLMENT_APPLICATION",
                "android.permission.KILL_UID",
                "android.permission.LAUNCH_TRUST_AGENT_SETTINGS",
                "android.permission.LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH",
                "android.permission.LOADER_USAGE_STATS",
                "android.permission.LOCAL_MAC_ADDRESS",
                "android.permission.LOCATION_HARDWARE",
                "android.permission.LOCK_DEVICE",
                "android.permission.LOG_COMPAT_CHANGE",
                "android.permission.LOOP_RADIO",
                "android.permission.MAGNIFY_DISPLAY",
                "android.permission.MANAGE_ACCESSIBILITY",
                "android.permission.MANAGE_ACTIVITY_STACKS",
                "android.permission.MANAGE_ACTIVITY_TASKS",
                "android.permission.MANAGE_APPOPS",
                "android.permission.MANAGE_APP_HIBERNATION",
                "android.permission.MANAGE_APP_OPS_MODES",
                "android.permission.MANAGE_APP_OPS_RESTRICTIONS",
                "android.permission.MANAGE_APP_PREDICTIONS",
                "android.permission.MANAGE_APP_TOKENS",
                "android.permission.MANAGE_AUDIO_POLICY",
                "android.permission.MANAGE_AUTO_FILL",
                "android.permission.MANAGE_BIND_INSTANT_SERVICE",
                "android.permission.MANAGE_BIOMETRIC",
                "android.permission.MANAGE_BIOMETRIC_DIALOG",
                "android.permission.MANAGE_BLUETOOTH_WHEN_PERMISSION_REVIEW_REQUIRED",
                "android.permission.MANAGE_BLUETOOTH_WHEN_WIRELESS_CONSENT_REQUIRED",
                "android.permission.MANAGE_CAMERA",
                "android.permission.MANAGE_CARRIER_OEM_UNLOCK_STATE",
                "android.permission.MANAGE_CA_CERTIFICATES",
                "android.permission.MANAGE_COMPANION_DEVICES",
                "android.permission.MANAGE_CONTENT_CAPTURE",
                "android.permission.MANAGE_CONTENT_SUGGESTIONS",
                "android.permission.MANAGE_CRATES",
                "android.permission.MANAGE_CREDENTIAL_MANAGEMENT_APP",
                "android.permission.MANAGE_DEBUGGING",
                "android.permission.MANAGE_DEVICE_ADMINS",
                "android.permission.MANAGE_DOCUMENTS",
                "android.permission.MANAGE_DYNAMIC_SYSTEM",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.MANAGE_FACTORY_RESET_PROTECTION",
                "android.permission.MANAGE_FINGERPRINT",
                "android.permission.MANAGE_GAME_MODE",
                "android.permission.MANAGE_IPSEC_TUNNELS",
                "android.permission.MANAGE_LOWPAN_INTERFACES",
                "android.permission.MANAGE_MEDIA",
                "android.permission.MANAGE_MEDIA_PROJECTION",
                "android.permission.MANAGE_MUSIC_RECOGNITION",
                "android.permission.MANAGE_NETWORK_POLICY",
                "android.permission.MANAGE_NOTIFICATIONS",
                "android.permission.MANAGE_NOTIFICATION_LISTENERS",
                "android.permission.MANAGE_ONE_TIME_PERMISSION_SESSIONS",
                "android.permission.MANAGE_ONGOING_CALLS",
                "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS",
                "android.permission.MANAGE_ROLE_HOLDERS",
                "android.permission.MANAGE_ROLLBACKS",
                "android.permission.MANAGE_ROTATION_RESOLVER",
                "android.permission.MANAGE_SCOPED_ACCESS_DIRECTORY_PERMISSIONS",
                "android.permission.MANAGE_SEARCH_UI",
                "android.permission.MANAGE_SENSORS",
                "android.permission.MANAGE_SENSOR_PRIVACY",
                "android.permission.MANAGE_SLICE_PERMISSIONS",
                "android.permission.MANAGE_SMARTSPACE",
                "android.permission.MANAGE_SOUND_TRIGGER",
                "android.permission.MANAGE_SPEECH_RECOGNITION",
                "android.permission.MANAGE_SUBSCRIPTION_PLANS",
                "android.permission.MANAGE_TEST_NETWORKS",
                "android.permission.MANAGE_TIME_AND_ZONE_DETECTION",
                "android.permission.MANAGE_TOAST_RATE_LIMITING",
                "android.permission.MANAGE_UI_TRANSLATION",
                "android.permission.MANAGE_USB",
                "android.permission.MANAGE_USERS",
                "android.permission.MANAGE_USER_OEM_UNLOCK_STATE",
                "android.permission.MANAGE_VOICE_KEYPHRASES",
                "android.permission.MANAGE_WIFI_COUNTRY_CODE",
                "android.permission.MANAGE_WIFI_WHEN_PERMISSION_REVIEW_REQUIRED",
                "android.permission.MANAGE_WIFI_WHEN_WIRELESS_CONSENT_REQUIRED",
                "android.permission.MARK_DEVICE_ORGANIZATION_OWNED",
                "android.permission.MARK_NETWORK_SOCKET",
                "android.permission.MASTER_CLEAR",
                "android.permission.MEDIA_CONTENT_CONTROL",
                "android.permission.MEDIA_RESOURCE_OVERRIDE_PID",
                "android.permission.MODIFY_ACCESSIBILITY_DATA",
                "android.permission.MODIFY_APPWIDGET_BIND_PERMISSIONS",
                "android.permission.MODIFY_AUDIO_ROUTING",
                "android.permission.MODIFY_CELL_BROADCASTS",
                "android.permission.MODIFY_DAY_NIGHT_MODE",
                "android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS",
                "android.permission.MODIFY_NETWORK_ACCOUNTING",
                "android.permission.MODIFY_PARENTAL_CONTROLS",
                "android.permission.MODIFY_PHONE_STATE",
                "android.permission.MODIFY_QUIET_MODE",
                "android.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE",
                "android.permission.MODIFY_SETTINGS_OVERRIDEABLE_BY_RESTORE",
                "android.permission.MODIFY_THEME_OVERLAY",
                "android.permission.MONITOR_DEFAULT_SMS_PACKAGE",
                "android.permission.MONITOR_DEVICE_CONFIG_ACCESS",
                "android.permission.MONITOR_INPUT",
                "android.permission.MOUNT_FORMAT_FILESYSTEMS",
                "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
                "android.permission.MOVE_PACKAGE",
                "android.permission.NETWORK_AIRPLANE_MODE",
                "android.permission.NETWORK_BYPASS_PRIVATE_DNS",
                "android.permission.NETWORK_CARRIER_PROVISIONING",
                "android.permission.NETWORK_FACTORY",
                "android.permission.NETWORK_MANAGED_PROVISIONING",
                "android.permission.NETWORK_SCAN",
                "android.permission.NETWORK_SETTINGS",
                "android.permission.NETWORK_SETUP_WIZARD",
                "android.permission.NETWORK_SIGNAL_STRENGTH_WAKEUP",
                "android.permission.NETWORK_STACK",
                "android.permission.NETWORK_STATS_PROVIDER",
                "android.permission.NET_ADMIN",
                "android.permission.NET_TUNNELING",
                "android.permission.NFC_HANDOVER_STATUS",
                "android.permission.NFC_SET_CONTROLLER_ALWAYS_ON",
                "android.permission.NOTIFICATION_DURING_SETUP",
                "android.permission.NOTIFY_PENDING_SYSTEM_UPDATE",
                "android.permission.NOTIFY_TV_INPUTS",
                "android.permission.OBSERVE_APP_USAGE",
                "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS",
                "android.permission.OBSERVE_NETWORK_POLICY",
                "android.permission.OBSERVE_ROLE_HOLDERS",
                "android.permission.OEM_UNLOCK_STATE",
                "android.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS",
                "android.permission.OPEN_APPLICATION_DETAILS_OPEN_BY_DEFAULT_PAGE",
                "android.permission.OPEN_APP_OPEN_BY_DEFAULT_SETTINGS",
                "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG",
                "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD",
                "android.permission.OVERRIDE_DISPLAY_MODE_REQUESTS",
                "android.permission.OVERRIDE_WIFI_CONFIG",
                "android.permission.PACKAGE_ROLLBACK_AGENT",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.PACKAGE_VERIFICATION_AGENT",
                "android.permission.PACKET_KEEPALIVE_OFFLOAD",
                "android.permission.PEEK_DROPBOX_DATA",
                "android.permission.PEERS_MAC_ADDRESS",
                "android.permission.PERFORM_CDMA_PROVISIONING",
                "android.permission.PERFORM_SIM_ACTIVATION",
                "android.permission.POWER_SAVER",
                "android.permission.PROVIDE_RESOLVER_RANKER_SERVICE",
                "android.permission.PROVIDE_TRUST_AGENT",
                "android.permission.QUERY_AUDIO_STATE",
                "android.permission.QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT",
                "android.permission.QUERY_TIME_ZONE_RULES",
                "android.permission.RADIO_SCAN_WITHOUT_LOCATION",
                "android.permission.READ_ACTIVE_EMERGENCY_SESSION",
                "android.permission.READ_BLOCKED_NUMBERS",
                "android.permission.READ_CARRIER_APP_INFO",
                "android.permission.READ_CLIPBOARD_IN_BACKGROUND",
                "android.permission.READ_COMPAT_CHANGE_CONFIG",
                "android.permission.READ_CONTENT_RATING_SYSTEMS",
                "android.permission.READ_DEVICE_CONFIG",
                "android.permission.READ_DREAM_STATE",
                "android.permission.READ_DREAM_SUPPRESSION",
                "android.permission.READ_FRAME_BUFFER",
                "android.permission.READ_INPUT_STATE",
                "android.permission.READ_LOGS",
                "android.permission.READ_LOWPAN_CREDENTIAL",
                "android.permission.READ_NEARBY_STREAMING_POLICY",
                "android.permission.READ_NETWORK_USAGE_HISTORY",
                "android.permission.READ_OEM_UNLOCK_STATE",
                "android.permission.READ_PEOPLE_DATA",
                "android.permission.READ_PRECISE_PHONE_STATE",
                "android.permission.READ_PRINT_SERVICES",
                "android.permission.READ_PRINT_SERVICE_RECOMMENDATIONS",
                "android.permission.READ_PRIVILEGED_PHONE_STATE",
                "android.permission.READ_PROJECTION_STATE",
                "android.permission.READ_RUNTIME_PROFILES",
                "android.permission.READ_SEARCH_INDEXABLES",
                "android.permission.READ_SYSTEM_UPDATE_INFO",
                "android.permission.READ_WALLPAPER_INTERNAL",
                "android.permission.READ_WIFI_CREDENTIAL",
                "android.permission.REAL_GET_TASKS",
                "android.permission.REBOOT",
                "android.permission.RECEIVE_BLUETOOTH_MAP",
                "android.permission.RECEIVE_DATA_ACTIVITY_CHANGE",
                "android.permission.RECEIVE_DEVICE_CUSTOMIZATION_READY",
                "android.permission.RECEIVE_EMERGENCY_BROADCAST",
                "android.permission.RECEIVE_MEDIA_RESOURCE_USAGE",
                "android.permission.RECEIVE_STK_COMMANDS",
                "android.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE",
                "android.permission.RECOVERY",
                "android.permission.RECOVER_KEYSTORE",
                "android.permission.REGISTER_CALL_PROVIDER",
                "android.permission.REGISTER_CONNECTION_MANAGER",
                "android.permission.REGISTER_MEDIA_RESOURCE_OBSERVER",
                "android.permission.REGISTER_SIM_SUBSCRIPTION",
                "android.permission.REGISTER_STATS_PULL_ATOM",
                "android.permission.REGISTER_WINDOW_MANAGER_LISTENERS",
                "android.permission.REMOTE_AUDIO_PLAYBACK",
                "android.permission.REMOTE_DISPLAY_PROVIDER",
                "android.permission.REMOVE_DRM_CERTIFICATES",
                "android.permission.REMOVE_TASKS",
                "android.permission.RENOUNCE_PERMISSIONS",
                "android.permission.REQUEST_INCIDENT_REPORT_APPROVAL",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.REQUEST_NETWORK_SCORES",
                "android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE",
                "android.permission.RESET_APP_ERRORS",
                "android.permission.RESET_FACE_LOCKOUT",
                "android.permission.RESET_FINGERPRINT_LOCKOUT",
                "android.permission.RESET_PASSWORD",
                "android.permission.RESET_SHORTCUT_MANAGER_THROTTLING",
                "android.permission.RESTART_WIFI_SUBSYSTEM",
                "android.permission.RESTORE_RUNTIME_PERMISSIONS",
                "android.permission.RESTRICTED_VR_ACCESS",
                "android.permission.RETRIEVE_WINDOW_CONTENT",
                "android.permission.RETRIEVE_WINDOW_INFO",
                "android.permission.RETRIEVE_WINDOW_TOKEN",
                "android.permission.REVIEW_ACCESSIBILITY_SERVICES",
                "android.permission.REVOKE_RUNTIME_PERMISSIONS",
                "android.permission.ROTATE_SURFACE_FLINGER",
                "android.permission.RUN_IN_BACKGROUND",
                "android.permission.SCHEDULE_PRIORITIZED_ALARM",
                "android.permission.SCORE_NETWORKS",
                "android.permission.SECURE_ELEMENT_PRIVILEGED_OPERATION",
                "android.permission.SEND_CATEGORY_CAR_NOTIFICATIONS",
                "android.permission.SEND_DEVICE_CUSTOMIZATION_READY",
                "android.permission.SEND_EMBMS_INTENTS",
                "android.permission.SEND_RESPOND_VIA_MESSAGE",
                "android.permission.SEND_SHOW_SUSPENDED_APP_DETAILS",
                "android.permission.SEND_SMS_NO_CONFIRMATION",
                "android.permission.SERIAL_PORT",
                "android.permission.SET_ACTIVITY_WATCHER",
                "android.permission.SET_ALWAYS_FINISH",
                "android.permission.SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS",
                "android.permission.SET_ANIMATION_SCALE",
                "android.permission.SET_CLIP_SOURCE",
                "android.permission.SET_DEBUG_APP",
                "android.permission.SET_DISPLAY_OFFSET",
                "android.permission.SET_HARMFUL_APP_WARNINGS",
                "android.permission.SET_INITIAL_LOCK",
                "android.permission.SET_INPUT_CALIBRATION",
                "android.permission.SET_KEYBOARD_LAYOUT",
                "android.permission.SET_MEDIA_KEY_LISTENER",
                "android.permission.SET_ORIENTATION",
                "android.permission.SET_POINTER_SPEED",
                "android.permission.SET_PREFERRED_APPLICATIONS",
                "android.permission.SET_PROCESS_FOREGROUND",
                "android.permission.SET_PROCESS_LIMIT",
                "android.permission.SET_SCREEN_COMPATIBILITY",
                "android.permission.SET_TIME",
                "android.permission.SET_TIME_ZONE",
                "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER",
                "android.permission.SET_WALLPAPER_COMPONENT",
                "android.permission.SHOW_KEYGUARD_MESSAGE",
                "android.permission.SHUTDOWN",
                "android.permission.SIGNAL_PERSISTENT_PROCESSES",
                "android.permission.SIGNAL_REBOOT_READINESS",
                "android.permission.SMS_FINANCIAL_TRANSACTIONS",
                "android.permission.SOUNDTRIGGER_DELEGATE_IDENTITY",
                "android.permission.SOUND_TRIGGER_RUN_IN_BATTERY_SAVER",
                "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
                "android.permission.START_ACTIVITY_AS_CALLER",
                "android.permission.START_ANY_ACTIVITY",
                "android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND",
                "android.permission.START_TASKS_FROM_RECENTS",
                "android.permission.START_VIEW_PERMISSION_USAGE",
                "android.permission.STATSCOMPANION",
                "android.permission.STATUS_BAR",
                "android.permission.STATUS_BAR_SERVICE",
                "android.permission.STOP_APP_SWITCHES",
                "android.permission.STORAGE_INTERNAL",
                "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME",
                "android.permission.SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON",
                "android.permission.SUGGEST_EXTERNAL_TIME",
                "android.permission.SUGGEST_MANUAL_TIME_AND_ZONE",
                "android.permission.SUGGEST_TELEPHONY_TIME_AND_ZONE",
                "android.permission.SUSPEND_APPS",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.SYSTEM_APPLICATION_OVERLAY",
                "android.permission.SYSTEM_CAMERA",
                "android.permission.TABLET_MODE",
                "android.permission.TEMPORARY_ENABLE_ACCESSIBILITY",
                "android.permission.TEST_BIOMETRIC",
                "android.permission.TEST_BLACKLISTED_PASSWORD",
                "android.permission.TEST_MANAGE_ROLLBACKS",
                "android.permission.TETHER_PRIVILEGED",
                "android.permission.TOGGLE_AUTOMOTIVE_PROJECTION",
                "android.permission.TRIGGER_SHELL_BUGREPORT",
                "android.permission.TRIGGER_TIME_ZONE_RULES_CHECK",
                "android.permission.TRUST_LISTENER",
                "android.permission.TUNER_RESOURCE_ACCESS",
                "android.permission.TV_INPUT_HARDWARE",
                "android.permission.TV_VIRTUAL_REMOTE_CONTROLLER",
                "android.permission.UNLIMITED_SHORTCUTS_API_CALLS",
                "android.permission.UNLIMITED_TOASTS",
                "android.permission.UPDATE_APP_OPS_STATS",
                "android.permission.UPDATE_CONFIG",
                "android.permission.UPDATE_DEVICE_STATS",
                "android.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION",
                "android.permission.UPDATE_FONTS",
                "android.permission.UPDATE_LOCK",
                "android.permission.UPDATE_LOCK_TASK_PACKAGES",
                "android.permission.UPDATE_TIME_ZONE_RULES",
                "android.permission.UPGRADE_RUNTIME_PERMISSIONS",
                "android.permission.USER_ACTIVITY",
                "android.permission.USE_BIOMETRIC_INTERNAL",
                "android.permission.USE_COLORIZED_NOTIFICATIONS",
                "android.permission.USE_DATA_IN_BACKGROUND",
                "android.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER",
                "android.permission.USE_RESERVED_DISK",
                "android.permission.UWB_PRIVILEGED",
                "android.permission.VIBRATE_ALWAYS_ON",
                "android.permission.VIEW_INSTANT_APPS",
                "android.permission.VIRTUAL_INPUT_DEVICE",
                "android.permission.WATCH_APPOPS",
                "android.permission.WHITELIST_AUTO_REVOKE_PERMISSIONS",
                "android.permission.WHITELIST_RESTRICTED_PERMISSIONS",
                "android.permission.WIFI_ACCESS_COEX_UNSAFE_CHANNELS",
                "android.permission.WIFI_SET_DEVICE_MOBILITY_STATE",
                "android.permission.WIFI_UPDATE_COEX_UNSAFE_CHANNELS",
                "android.permission.WIFI_UPDATE_USABILITY_STATS_SCORE",
                "android.permission.WRITE_APN_SETTINGS",
                "android.permission.WRITE_BLOCKED_NUMBERS",
                "android.permission.WRITE_DEVICE_CONFIG",
                "android.permission.WRITE_DREAM_STATE",
                "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS",
                "android.permission.WRITE_GSERVICES",
                "android.permission.WRITE_MEDIA_STORAGE",
                "android.permission.WRITE_OBB",
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.WRITE_SETTINGS",
                "android.permission.WRITE_SETTINGS_HOMEPAGE_DATA",
                "com.android.permission.BIND_EUICC_SERVICE",
                "com.android.permission.INSTALL_EXISTING_PACKAGES",
                "com.android.permission.USE_INSTALLER_V2",
                "com.android.permission.USE_SYSTEM_DATA_LOADERS",
                "com.android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS",
                "com.android.voicemail.permission.READ_VOICEMAIL",
                "com.android.voicemail.permission.WRITE_VOICEMAIL",
            };

    /** Constructs a new {@link SystemPermissionsDetector} check */
    public SystemPermissionsDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameNode != null) {
            String permissionName = nameNode.getValue();
            if (Arrays.binarySearch(SYSTEM_PERMISSIONS, permissionName) >= 0) {

                if (permissionName.equals("android.permission.CHANGE_NETWORK_STATE")) {
                    // This permission was briefly (*only* in API level 23) marked
                    // signature permission; it was fixed again in the next API level.
                    return;
                }

                if (permissionName.equals("android.permission.SYSTEM_ALERT_WINDOW")) {
                    // Even though it's a signature permission you *can* get it by
                    // sending an intent with action ACTION_MANAGE_OVERLAY_PERMISSION; see
                    // https://developer.android.com/reference/android/Manifest.permission.html#SYSTEM_ALERT_WINDOW
                    // Therefore, don't flag it here.
                    return;
                }

                if (permissionName.equals("android.permission.REQUEST_INSTALL_PACKAGES")) {
                    // Despite have protection level, it appears to be valid and required
                    // in some scenarios; see bug 73857733 and
                    // https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html
                    return;
                }

                // Special cases: some permissions were added as signature permissions later;
                // look for these and allow it.
                int max = getLastNonSignatureApiLevel(permissionName);
                if (max != -1) {
                    //  android:maxSdkVersion="22"
                    Attr maxAttribute = element.getAttributeNodeNS(ANDROID_URI, "maxSdkVersion");
                    if (maxAttribute != null) {
                        try {
                            int maxValue = Integer.parseInt(maxAttribute.getValue());
                            if (maxValue <= max) {
                                return;
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }

                context.report(
                        ISSUE,
                        element,
                        context.getLocation(nameNode),
                        "Permission is only granted to system apps");
            }
        }
    }

    private static int getLastNonSignatureApiLevel(@NonNull String name) {
        switch (name) {
            case "android.permission.READ_LOGS":
            case "android.permission.SET_ALWAYS_FINISH":
            case "android.permission.SET_ANIMATION_SCALE":
            case "android.permission.SET_DEBUG_APP":
            case "android.permission.SET_PROCESS_LIMIT":
            case "android.permission.SIGNAL_PERSISTENT_PROCESSES":
                return 15;
            case "android.permission.CHANGE_CONFIGURATION":
            case "android.permission.MOUNT_FORMAT_FILESYSTEMS":
            case "android.permission.MOUNT_UNMOUNT_FILESYSTEMS":
                return 16;
            case "com.android.voicemail.permission.READ_VOICEMAIL":
            case "com.android.voicemail.permission.WRITE_VOICEMAIL":
                return 21;
            case "android.permission.ACCESS_MOCK_LOCATION":
            case "android.permission.CHANGE_NETWORK_STATE":
            case "android.permission.CLEAR_APP_CACHE":
            case "android.permission.SYSTEM_ALERT_WINDOW":
            case "android.permission.WRITE_SETTINGS":
                return 22;
            case "android.permission.REQUEST_INSTALL_PACKAGES":
            case "android.permission.SET_TIME_ZONE":
                return 25;
            case "android.permission.ACCESS_BLOBS_ACROSS_USERS":
            case "android.permission.BIND_COMPANION_DEVICE_SERVICE":
            case "android.permission.MANAGE_MEDIA":
            case "android.permission.MANAGE_ONGOING_CALLS":
            case "android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND":
            case "android.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER":
                return 30;
            default:
                return -1;
        }
    }
}
