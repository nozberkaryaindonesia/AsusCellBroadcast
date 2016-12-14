package com.android.cellbroadcastreceiver;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.android.cellbroadcastreceiver.GsmBroadcastConfigurator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.gsm.SmsCbConstants;

import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.Toast;


abstract class GsmUmtsCellBroadcastSmsFragmentBase extends PreferenceFragment {
    private final String TAG = getInstanceTag();
    protected static final boolean DEBUG = true;

    //For Progress Dialog display text by status
    private final static int TEXT_ENABLE = 0;
    private final static int TEXT_UPDATING = 1;

    protected boolean mConfigCMAS = false;

    //For checking the call back result when enable or disable a channel 
    //protected static final String FAIL_KEY = "fail";
    //We only reset back when user enable or disable, we dont change status we onResume or Rotation 
    protected static final String CLICKED_KEY = "clicked";

    private static HashMap<String, Boolean> sSlot1IDs = new HashMap<String, Boolean>();
    private static HashMap<String, Boolean> sSlot2IDs = new HashMap<String, Boolean>();
    private static SharedPreferences sKeyValues = null;
    private static SharedPreferences.Editor sKeyValuesEditor = null;
    private static Preference sContextMenuSelectKey;

    private Activity mActivity;
    private PreferenceCategory mPCategory;
    private GsmUmtsCellBroadcastSmsFragmentBase mPrefFragment;

    public static final String PREFIX_SIM2 = "SIM2_"; // dion_yang@20130912 : Add DSDS get from SIM
    private MyHandler mHandler;
    private ProgressDialog mProgressDialog, mLoadingProgressDialog;

    private static final int DIALOG_ADD_CHANNEL = 0;
    private static final int DIALOG_EDIT_CHANNEL = 1;
    private static final int DIALOG_DEL_CHANNEL = 2;
    private static final int DIALOG_PROGRESS_CHANNEL = 3;
    
    // The Custom Cell Broadcast ID range
    public static final int MIN_CBS_UI_ID = 0;
    public static final int MAX_CBS_UI_ID = 999;

    // For OTA using
    public static final String GSM_CBS_DATABASE_VERSION_KEY = "cbs_ver";
    public static final int GSM_CBS_DATABASE_VERSION = 2;

    protected String mPrefCategoryAlertSettings = "";
    protected String mPrefCategoryCustomSettings = "";

    protected static String sPrefKeyAlertSoundPreview = "preference_key_alert_sound_preview";
    protected static String sPrefKeyAlertVibrationPreview = "preference_key_alert_vibration_preview";

    protected String mPrefKeyPresidentialAlerts = "";
    protected String mPrefKeyAllAlerts = "";
    protected String mPrefKeyExtremeAlerts = "";
    protected String mPrefKeySevereAlerts = "";
    protected String mPrefKeyAmberAlerts = "";
    protected String mPrefKeyEnableEmergencyAlerts = "";
    protected String mPrefKeyEnableAlertVibration = "";
    protected String mPrefKeyEnableETWSTestAlerts = "";
    protected String mPrefKeyEnableCMASTestAlerts = "";

    // General SMS-CB preference key
    protected String mPrefKeySMSCBLanguage = "";

    protected int mSlotId;
    public static int SLOTID_SLOT1 = PhoneConstants.SUB1;
    public static int SLOTID_SLOT2 = PhoneConstants.SUB2;

    protected GsmUmtsCellBroadcastSmsFragmentBase() {
        mPrefFragment = this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onActivityCreated:");
        //ASUS BEGIN, dion_yang:
        mActivity = getActivity();
        mHandler = new MyHandler();

        Intent intent = mActivity.getIntent();
        mConfigCMAS = intent.getBooleanExtra("config_cmas", false);
        Log.d(TAG, "onActivityCreated: config_cmas=" + mConfigCMAS);

        mPCategory = (PreferenceCategory) findPreference(mPrefCategoryCustomSettings);
        getPreferenceFragment().registerForContextMenu(getListView());
        if (DEBUG) Log.d(TAG, mPCategory.getPreferenceManager().getSharedPreferencesName());
        sKeyValues = mActivity.getSharedPreferences(CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);

        // +++ dion_yang@20131026:For ATT requirement
        setEmergencyPreference();
        // --- dion_yang@20131026:For ATT requirement

        if (mConfigCMAS) {
            Log.d(TAG, "config CMAS: hide SMS-CB preference");
            getPreferenceScreen().removePreference(mPCategory);
            getActivity().setTitle(R.string.app_label);//OliverOu@20151113, TT-689136
        } else {
            Log.d(TAG, "config SMS-CB: hide CMAS preference");
            PreferenceCategory prefCMAS = (PreferenceCategory)findPreference(mPrefCategoryAlertSettings);
            if (prefCMAS != null) getPreferenceScreen().removePreference(prefCMAS);
            PreferenceCategory prefCMASpreview = (PreferenceCategory)findPreference("category_att_emergency_alerts_preview");
            if (prefCMASpreview != null) getPreferenceScreen().removePreference(prefCMASpreview);
            PreferenceCategory prefEWTS = (PreferenceCategory)findPreference("category_etws_settings");
            if (prefEWTS != null) getPreferenceScreen().removePreference(prefEWTS);
            PreferenceCategory prefCMASdev = (PreferenceCategory)findPreference("category_dev_settings");
            if (prefCMASdev != null) getPreferenceScreen().removePreference(prefCMASdev);
            // change title

            getActivity().setTitle(R.string.general_sms_cb_label);
        }
        //ASUS END, dion_yang:

        initialChannel();
    }

    // +++ dion_yang@20131026:For ATT requirement
    private void setEmergencyPreference() {
        PreferenceCategory emergencyAlertCategory = (PreferenceCategory) findPreference(mPrefCategoryAlertSettings);
        if (!CellBroadcastUtils.isATTSku() && !CellBroadcastUtils.isVZWSku()) {
            if (DEBUG) Log.d(TAG, "remove ATT's and VZW settings");//OliverOu@20151119:Add support Verison sku
            PreferenceCategory emergencyAlertPreviewCategory = (PreferenceCategory) findPreference("category_att_emergency_alerts_preview");
            Preference presidentialAlert = (Preference) findPreference(mPrefKeyPresidentialAlerts);
            Preference extremeAlert = (Preference) findPreference(mPrefKeyExtremeAlerts);
            Preference severeAlert = (Preference) findPreference(mPrefKeySevereAlerts);
            Preference amberAlert = (Preference) findPreference(mPrefKeyAmberAlerts);
            if (emergencyAlertCategory != null) {
                if (presidentialAlert != null) {
                    emergencyAlertCategory.removePreference(presidentialAlert);
                }
                if (extremeAlert != null) {
                    emergencyAlertCategory.removePreference(extremeAlert);
                }
                if (severeAlert != null) {
                    emergencyAlertCategory.removePreference(severeAlert);
                }
                if (amberAlert != null) {
                    emergencyAlertCategory.removePreference(amberAlert);
                }
            }
            if (emergencyAlertPreviewCategory != null) {
                getPreferenceScreen().removePreference(emergencyAlertPreviewCategory);
            }
        }
        if(!getResources().getBoolean(R.bool.show_etws_test_settings) && !(CellBroadcastUtils.isGcfMode() && CellBroadcastUtils.isATTSku())) {
            if (DEBUG) Log.d(TAG, "remove ETWS test settings");
            PreferenceCategory etwsTestAlertCategory = (PreferenceCategory)findPreference("category_etws_settings");
            if (etwsTestAlertCategory != null) {
                getPreferenceScreen().removePreference(etwsTestAlertCategory);
            }
        }
        if(!getResources().getBoolean(R.bool.show_dev_settings) && !(CellBroadcastUtils.isGcfMode() && CellBroadcastUtils.isATTSku())) {
            if (DEBUG) Log.d(TAG, "remove CMAS dev settings");
            PreferenceCategory cmasTestAlertCategory = (PreferenceCategory)findPreference("category_dev_settings");
            if (cmasTestAlertCategory != null) {
                getPreferenceScreen().removePreference(cmasTestAlertCategory);
            }
        }

        // +++ andrew_tu@20140430 TT-399013 : Hide vibrate alert if DUT has no vibrator.
        if (!CellBroadcastUtils.hasVibrator(getActivity().getBaseContext())) {
            Preference vibrateAlert = (Preference)getPreferenceFragment().findPreference(mPrefKeyEnableAlertVibration);
            if (vibrateAlert != null) {
                emergencyAlertCategory.removePreference(vibrateAlert);
            }
            // +++ gary_hsu@20160309, remove alert vibration preview
            PreferenceCategory prefCMASpreview = (PreferenceCategory)findPreference("category_att_emergency_alerts_preview");
            if (prefCMASpreview != null) {
                Preference vibrateAlertPreview = (Preference)getPreferenceFragment().findPreference("preference_key_alert_vibration_preview");
                if(vibrateAlertPreview != null){
                    prefCMASpreview.removePreference(vibrateAlertPreview);
                }
            }
            // --- gary_hsu@20160309, remove alert vibration preview
        }
        // --- andrew_tu@20140430 TT-399013 : Hide vibrate alert if DUT has no vibrator.

        // +++ andrew_tu@20140916 Hide language setting for user mode but ATT sku.
        // ME372CL About CBS
        if (CellBroadcastUtils.isUserMode() && !CellBroadcastUtils.isATTSku()) {
            PreferenceCategory prefcustom = (PreferenceCategory)findPreference(mPrefCategoryCustomSettings);
            if (prefcustom != null) {
                ListPreference prefLanguage = (ListPreference) prefcustom.findPreference(mPrefKeySMSCBLanguage);
                if (prefLanguage != null) {
                    prefcustom.removePreference(prefLanguage);
                }
            }
        }
        // --- andrew_tu@20140916 Hide language setting for user mode but ATT sku.
    }
    // --- dion_yang@20131026:For ATT requirement

    //ASUS BEGIN, dion_yang:
    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume:");
        
        if (mLoadingProgressDialog != null) mLoadingProgressDialog.dismiss();
        if (CellBroadcastReceiverApp.DEBUG_MEM) CellBroadcastReceiverApp.getApplication().dumpMemoryInfo(TAG);
    }

    private void initialChannel() {
        if (mConfigCMAS) {
            Log.w(TAG, "initialChannel: mConfigCMAS=" + mConfigCMAS + ", return!");
            return;
        }
        mHandler.post(mLoadingprogressDailogRunnable);

        Log.d(TAG, "initialChannel:");
        Set<String> keySet = sKeyValues.getAll().keySet();
        for (String key : keySet) {
            HashMap<String, Boolean> map = key.startsWith(PREFIX_SIM2) ? sSlot2IDs : sSlot1IDs;
            if (!key.isEmpty()) {
                map.put(key, sKeyValues.getBoolean(key, false));
            }
        }
        sKeyValuesEditor = sKeyValues.edit();

        Log.d(TAG, " sSlot1IDs= " + sSlot1IDs);
        Log.d(TAG, " sSlot2IDs= " + sSlot2IDs);

        if (mPCategory != null) {
            HashMap<String, Boolean> map = getIDs();
            int slotId = getSlotId();
            if (map.isEmpty()) {
                // For EMT and Tim, Europe operator request turn off the default value in Cell Broadcast
                if (DEBUG) Log.d(TAG, "onCreate: ID map is empty, we initial channel 50");
                TelephonyManager tm = (TelephonyManager)getActivity()
                        .getSystemService(Context.TELEPHONY_SERVICE);

                boolean isDefaultEnableChannel50Support = "br".equals(tm.getSimCountryIso());
                Log.d(TAG, "isDefaultEnableChannel50Support=" + isDefaultEnableChannel50Support);
                if (isDefaultEnableChannel50Support) {
                    CheckBoxPreference checkBoxPreference = createAddMessageID(slotId, 50, true,
                            false);
                    mPCategory.addPreference(checkBoxPreference);
                    checkBoxPreference.setChecked(true);
                } else {
                    CheckBoxPreference checkBoxPreference = createAddMessageID(slotId, 50, false,
                            true);
                    mPCategory.addPreference(checkBoxPreference);
                }
            } else {
                if (DEBUG) Log.d(TAG, "onCreate: ID map existed, we load existed channel list");
                loadAlreadyExistedAddedID(slotId);
            }
        }
        mHandler.removeCallbacks(mLoadingprogressDailogRunnable);
    }

    private Runnable mLoadingprogressDailogRunnable = new Runnable() {
        @Override
        public void run() {
            mLoadingProgressDialog = new ProgressDialog(getActivity());
            mLoadingProgressDialog.setIndeterminate(true);
            mLoadingProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mLoadingProgressDialog.setCanceledOnTouchOutside(false);
            mLoadingProgressDialog.setCancelable(false);
            mLoadingProgressDialog.setMessage(getActivity().getText(R.string.refreshing));
            mLoadingProgressDialog.show();
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause:");
        if (CellBroadcastReceiverApp.DEBUG_MEM) CellBroadcastReceiverApp.getApplication().dumpMemoryInfo(TAG);
        if (mLoadingProgressDialog != null) {
            mLoadingProgressDialog.dismiss();
        }
        // +++ andrew_tu@20140911 TT-463099 : Dismiss DialogFragment to avoid FC for changing language.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        DialogFragment dialog = (DialogFragment)getFragmentManager().findFragmentByTag("dialog");
        if (dialog != null) {
            dialog.dismiss();
        }
        // --- andrew_tu@20140911 TT-463099 : Dismiss DialogFragment to avoid FC for changing language.
    }

    @Override
    public void onStop() {
        super.onStop();
        if(DEBUG) Log.d(TAG, "onStop:");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(DEBUG) Log.d(TAG, "onDestroyView:");
        unregisterForContextMenu(getListView());
        getListView().removeAllViewsInLayout();

        mActivity = null;
        mPrefFragment = null;
        HashMap<String, Boolean> map = getIDs();;
        if (map != null) {
            map.clear();
            map = new HashMap<String, Boolean>();
        }

        sKeyValues = null;
        sKeyValuesEditor = null;
        if (mPCategory != null) {
            mPCategory.removeAll();
            mPCategory = null;
        }
        sContextMenuSelectKey = null;
        mProgressDialog = null;
        mHandler.removeCallbacks(mLoadingprogressDailogRunnable);
        if (mLoadingProgressDialog != null) mLoadingProgressDialog.dismiss();
        mHandler = null;
        mLoadingProgressDialog = null;
    }

    // +++ lide_yang@20131023: AT&T CDR-ECB-660
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        String preferenceKey = preference.getKey();
        if (DEBUG) {
            Log.d(TAG, "onPreferenceTreeClick:" + preferenceKey);
        }
        if (preferenceKey.toString().equals("add")) {
            createAddDialog();
            return true;
        } else if (preferenceKey.contentEquals(mPrefKeySMSCBLanguage)) {
            // Language checking for SAS devices.
        //ASUS BEGIN, dion_yang:support ETWS channel config
        } else if (preferenceKey.contentEquals(mPrefKeyEnableEmergencyAlerts)) {
            // +++ dion_yang@20131026:For ATT requirement
            if(CellBroadcastUtils.isATTSku()) {
                boolean isEmergencyAlertsEnabled = sharedPreferences.getBoolean(preferenceKey, false);
                CheckBoxPreference prefExtremeAlerts = (CheckBoxPreference) preferenceScreen
                        .findPreference(mPrefKeyExtremeAlerts);
                if(prefExtremeAlerts != null) {
                    prefExtremeAlerts.setChecked(isEmergencyAlertsEnabled);
                    setExtremeAlertsEnabled(preferenceScreen, isEmergencyAlertsEnabled);
                }

                CheckBoxPreference prefAmberAlerts = (CheckBoxPreference) preferenceScreen
                        .findPreference(mPrefKeyAmberAlerts);
                if(prefAmberAlerts != null) {
                    prefAmberAlerts.setChecked(isEmergencyAlertsEnabled);
                    setAmberAlertsEnabled(preferenceScreen, isEmergencyAlertsEnabled);
                }
            }
            // --- dion_yang@20131026:For ATT requirement

            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);
        //ASUS END, dion_yang:support ETWS channel config
        } else if (preferenceKey.contentEquals(mPrefKeyExtremeAlerts)) {
            boolean isExtremeAlertsEnabled = sharedPreferences.getBoolean(preferenceKey, false);
            setExtremeAlertsEnabled(preferenceScreen, isExtremeAlertsEnabled);
            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);  // dion_yang@20131026:For ATT requirement
        } else if (preferenceKey.contentEquals(mPrefKeySevereAlerts)) {
            boolean isSevereAlertsEnabled = sharedPreferences.getBoolean(preferenceKey, false);
            setSevereAlertsEnabled(preferenceScreen, isSevereAlertsEnabled);
            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);  // dion_yang@20131026:For ATT requirement
        } else if (preferenceKey.contentEquals(mPrefKeyAmberAlerts)) {
            boolean isAmberAlertsEnabled = sharedPreferences.getBoolean(preferenceKey, false);
            setAmberAlertsEnabled(preferenceScreen, isAmberAlertsEnabled);
            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);  // dion_yang@20131026:For ATT requirement
        } else if (preferenceKey.contentEquals(sPrefKeyAlertSoundPreview)) {
            // +++ dion_yang@20131026:For ATT requirement
            if(getActivity() != null) {
                CellBroadcastUtils.startAlertPreview(getActivity(), true);
            } else {
                Log.w(TAG, "getActivity was null");
            }
            // --- dion_yang@20131026:For ATT requirement
        } else if (preferenceKey.contentEquals(sPrefKeyAlertVibrationPreview)) {
        // +++ dion_yang@20131026:For ATT requirement
            if(getActivity() != null) {
                CellBroadcastUtils.startAlertPreview(getActivity(), false);
            } else {
                Log.w(TAG, "getActivity was null");
            }
        } else if (preferenceKey.contentEquals(mPrefKeyEnableETWSTestAlerts)) {
            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);
        } else if (preferenceKey.contentEquals(mPrefKeyEnableCMASTestAlerts)) {
            CellBroadcastUtils.initEmergencyAlertDualMode(mSlotId);
        } else if (preferenceKey.contentEquals("show_cmas_opt_out_dialog")) {
        // --- dion_yang@20131026:For ATT requirement
        // +++ dion_yang@20140113:Add CellBroadcast Vibration settings
        } else if(preferenceKey.contentEquals(mPrefKeyEnableAlertVibration)) {
        // --- dion_yang@20140113:Add CellBroadcast Vibration settings
        } else {
            if (DEBUG) {
                Log.d(TAG, "click added ID " + preferenceKey + " check box ="
                        + ((CheckBoxPreference) preference).isChecked());
            }
            if (mPCategory.findPreference(preferenceKey) != null) {
                // disable preference first
                preference.setEnabled(false);

                // check channel status
                HashMap<String, Boolean> map = getIDs();
                map.put(preferenceKey, ((CheckBoxPreference) preference).isChecked());
                int channelId = Integer.valueOf(preferenceKey.replace(PREFIX_SIM2, ""));
                boolean enable = map.get(preferenceKey);
                if (DEBUG) {
                    Log.d(TAG, "key=" + preferenceKey + ", ID map: " + map.toString());
                }

                // Asus Jenpang begin: show progress dialog or not when activate/deactivate channel
                boolean show = preference.getContext().getResources()
                        .getBoolean(R.bool.show_progress_dialog);
                if (show) {
                    showProgressDialog(enable, preference, TEXT_ENABLE);
                }
                // Asus Jenpang end: show progress dialog or not when activate/deactivate channel
                enableSingleChannel(getSlotId(), channelId, enable, false);
            }
        }
        return false;
    }

    private void setExtremeAlertsEnabled(PreferenceScreen preferenceScreen, boolean isEnabled) {
        // +++ dion_yang@20131026:For ATT requirement
        if(preferenceScreen != null) {
            CheckBoxPreference prefSevereAlerts = (CheckBoxPreference) preferenceScreen
                    .findPreference(mPrefKeySevereAlerts);
            if(prefSevereAlerts != null) {
                prefSevereAlerts.setChecked(isEnabled);
                setSevereAlertsEnabled(preferenceScreen, isEnabled);
            }
        }
        // --- dion_yang@20131026:For ATT requirement
    }

    private void setSevereAlertsEnabled(PreferenceScreen preferenceScreen, boolean isEnabled) {
    }

    private void setAmberAlertsEnabled(PreferenceScreen preferenceScreen, boolean isEnabled) {
    }
    // --- lide_yang@20131023: AT&T CDR-ECB-660

    protected void displayErrorToast(boolean state, String id) {
        if (mActivity != null) {
            String msg = mActivity.getString(R.string.err_set_failed_toast,
                    (state ? mActivity.getString(R.string.enable_text) :
                            mActivity.getString(R.string.disable_text)), id);
            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
        }
    }

   public class EditChannelDialogFragment extends DialogFragment {
        private final String TYPE_KEY = "type";
        private final String ENABLE_KEY = "enable";
        private final String ID_KEY = "id_text";
        private final String TEXT_KEY = "text";

        public EditChannelDialogFragment(int dialogType) {
            this(dialogType, "");
        }

        public EditChannelDialogFragment(int dialogType, String progressID) {
            this(dialogType, false, progressID, 0);
        }

        public EditChannelDialogFragment(int dialogType, boolean enable, String progressID, int text) {
            Bundle args = new Bundle();
            args.putInt(TYPE_KEY, dialogType);
            args.putBoolean(ENABLE_KEY, enable);
            args.putString(ID_KEY, progressID);
            args.putInt(TEXT_KEY, text);
            setArguments(args);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt(TYPE_KEY);
            boolean enable = getArguments().getBoolean(ENABLE_KEY);
            final String preferenceId = getArguments().getString(ID_KEY);
            int titleText = getArguments().getInt(TEXT_KEY);
            Log.d(TAG, "onCreateDialog type=" + id + ", enable=" + enable + ", progressId=" + preferenceId + ", titleText=" + titleText);
            AlertDialog addNewIdDialog;
            final Activity activity = getActivity();
            if(activity == null) {
                Log.d(TAG, "onCreateDialog off hook, activity was null");
                return super.onCreateDialog(savedInstanceState);
            }
            ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(activity, R.style.MmsColorfulTheme);
            LayoutInflater inflater = activity.getLayoutInflater().cloneInContext(contextThemeWrapper);
            final View newCustomAccountDialog = inflater.inflate(R.layout.dialog_for_add_new_id, null);
            EditText addID = (EditText)newCustomAccountDialog.findViewById(R.id.add_id);
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(contextThemeWrapper);
            switch (id) {
                case DIALOG_ADD_CHANNEL:
                    addID.setText("");
                    dialogBuilder.setView(newCustomAccountDialog);
                    dialogBuilder.setTitle(activity.getString(R.string.add_channel_id_dialog_title));
                    dialogBuilder.setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                                EditText addID = (EditText)newCustomAccountDialog.findViewById(R.id.add_id);
                                int idForAdding = isVaild(addID.getText().toString(), mActivity);
                                if(idForAdding != -1) {
                                    if(activity != null) {
                                        doAddDialogPositiveClick(idForAdding);
                                    }
                                } 
                            }
                        });
                    dialogBuilder.setNegativeButton(activity.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           }
                       });
                    addNewIdDialog = dialogBuilder.create();
                    return addNewIdDialog;
                    
                case DIALOG_EDIT_CHANNEL:
                    addID.setText(preferenceId.replace(PREFIX_SIM2, ""));
                    addID.setSelection(addID.getText().length());
                    dialogBuilder.setView(newCustomAccountDialog);
                    dialogBuilder.setTitle(activity.getString(R.string.edit_channel_id_dialog_title));
                    dialogBuilder.setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            EditText addID = (EditText) newCustomAccountDialog.findViewById(R.id.add_id);
                            int idForAdding = isVaild(addID.getText().toString(), mActivity);
                            if (idForAdding != -1) {
                                mPrefFragment.doDelDialogPositiveClick(preferenceId);
                                mPrefFragment.doEditDialogPositiveClick(getSlotId(), idForAdding);
                            }
                        }
                    });
                    dialogBuilder.setNegativeButton(activity.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                    addNewIdDialog = dialogBuilder.create();
                    return addNewIdDialog;
                    
                case DIALOG_DEL_CHANNEL:
                    String message = activity.getString(R.string.channel_id_dialog_message) + preferenceId + " ?";
                    if (mPrefFragment instanceof GsmUmtsCellBroadcastSmsFragment2) {
                        message = activity.getString(R.string.channel_id_dialog_message) + preferenceId.replace(PREFIX_SIM2, "") + " ?";
                    }
                    dialogBuilder.setMessage(message);
                    dialogBuilder.setTitle(activity.getString(R.string.del_channel_id_dialog_title));
                    dialogBuilder.setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mPrefFragment.doDelDialogPositiveClick(preferenceId);
                            }
                        });
                    dialogBuilder.setNegativeButton(activity.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                    addNewIdDialog = dialogBuilder.create();
                    return addNewIdDialog;
                    
                case DIALOG_PROGRESS_CHANNEL:
                    mProgressDialog = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                    mProgressDialog.setCancelable(false);
                    String state = enable ? activity.getString(R.string.enable_text):getString(R.string.disable_text);
                    switch(titleText) {
                        case TEXT_ENABLE:
                            mProgressDialog.setTitle(state+" "+preferenceId+" ...");
                            break;
                            
                        case TEXT_UPDATING:
                            mProgressDialog.setTitle(getString(R.string.progress_dialog_updating));
                            break;
                    }
                    return mProgressDialog;
             }
            return super.onCreateDialog(savedInstanceState);
        }

        private int isVaild(String sid, Activity activity) {
            int idForAdd = -1;
            if (sid.equals("")) {
                displayErrorToast(activity.getString(R.string.err_input_was_null), Toast.LENGTH_SHORT, activity);
                return -1;
            }
            try {
                idForAdd = Integer.valueOf(sid);
            } catch (NumberFormatException ex) {
                displayErrorToast(activity.getString(R.string.err_input_was_not_num), Toast.LENGTH_SHORT, activity);
                ex.printStackTrace();
                return -1;
            }
            if (idForAdd == -1) {
                Log.e(TAG, "Error for covert id");
                return -1;
            }
            if (idForAdd < MIN_CBS_UI_ID || idForAdd > MAX_CBS_UI_ID) {
                displayErrorToast(activity.getString(R.string.err_input_was_null), Toast.LENGTH_SHORT, activity);
                return -1;
            }

            if ((mSlotId == SLOTID_SLOT1 && sSlot1IDs.containsKey(String.valueOf(idForAdd))) ||
                    (mSlotId == SLOTID_SLOT2 && sSlot2IDs.containsKey(PREFIX_SIM2 + String.valueOf(idForAdd)))) {
                displayErrorToast(activity.getString(R.string.err_input_was_existed), Toast.LENGTH_SHORT, activity);
                return -1;
            }

            return idForAdd;
        }

        private void displayErrorToast(String msg, int showLength, Activity activity) {
            if (activity != null) {
                Toast.makeText(activity, msg, showLength).show();
            }
        }
    }

    private void createAddDialog() {
        //showDialog(DIALOG_ADD_CHANNEL);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment newFragment = new EditChannelDialogFragment(DIALOG_ADD_CHANNEL);
        newFragment.show(getFragmentManager(), "dialog");
    }
    
    public void doAddDialogPositiveClick(int idForAdding) {
        mPCategory.addPreference(createAddMessageID(getSlotId(), idForAdding, true, false));
    }

    public void createEditDialog() {
        //showDialog(DIALOG_EDIT_CHANNEL);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment newFragment = new EditChannelDialogFragment(DIALOG_EDIT_CHANNEL, sContextMenuSelectKey.getKey());
        newFragment.show(getFragmentManager(), "dialog");
    }

    public void doEditDialogPositiveClick(int simId, int idForAdding) {
        mPCategory.addPreference(createAddMessageID(simId, idForAdding, true, false));
    }

    public void createDeleteDialog() {
        //showDialog(DIALOG_DEL_CHANNEL);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment newFragment = new EditChannelDialogFragment(DIALOG_DEL_CHANNEL, sContextMenuSelectKey.getKey());
        newFragment.show(getFragmentManager(), "dialog");
    }

    public void doDelDialogPositiveClick(String removeTarget) {
       Preference preference = mPCategory.findPreference(removeTarget);
       // +++ theresa_chen@20140707: TT-387800 TT-389112: Fix NPE
       if (preference != null) {
           removeAddedMessageID(preference);
       } else {
           Log.v(TAG, "doDelDialogPositiveClick: preference not found: " + removeTarget);
       }
       // --- theresa_chen@20140707: TT-387800 TT-389112: Fix NPE
    }

    private void showProgressDialog(boolean enable , Preference preference, int titleText) {
        if(getPreferenceFragment() == null) {
            Log.w(TAG, "mPActivity was null! we may in rotation or DDS processing, skip show ProgressDialog()");
            return;
        }
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment newFragment = new EditChannelDialogFragment(DIALOG_PROGRESS_CHANNEL, enable, preference.getKey(), titleText);
        newFragment.show(getFragmentManager(), "dialog");
    }
    
    private void cancelProgressDialog() {
        if(mProgressDialog != null) {
            if(mProgressDialog.isShowing() && (mProgressDialog.getWindow()!=null)) {
                mProgressDialog.cancel();
            }
        }
    }

    private void loadAlreadyExistedAddedID(int simId) {
        if(DEBUG) Log.d(TAG, "loadAlreadyExistedAddedID: simId=" + simId);
        Map<String, Boolean> map = getIDs();
        if (map.containsKey(mPrefKeySMSCBLanguage)) {
            map.remove(mPrefKeySMSCBLanguage);
        }
        // +++ joey_lee, add channels for Civil Emergency Message, hide these channels
        String prefKey = (simId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                + String.valueOf(CellBroadcastUtils.MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE);
        String prefKey2 = (simId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                + String.valueOf(CellBroadcastUtils.MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES);
        if (map.containsKey(prefKey)) {
            map.remove(prefKey);
        }
        if (map.containsKey(prefKey2)) {
            map.remove(prefKey2);
        }
        // --- joey_lee, add channels for Civil Emergency Message, hide these channels
        ArrayList<String> sortedKeys = new ArrayList<String>(map.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            int channelId = Integer.valueOf(key.replace(PREFIX_SIM2, ""));
            if (channelId >= MIN_CBS_UI_ID && channelId <= MAX_CBS_UI_ID) {
                if (mPCategory != null) {
                    boolean enabled = map.get(key);
                    CheckBoxPreference preference = createAddMessageID(simId, channelId, enabled, true);
                    mPCategory.addPreference(preference);
                    preference.setChecked(enabled);
                }
            }
        }
    }

    private void enableSingleChannel(int slotId, int channelId, boolean enable, boolean removeMode) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(getActivity());
        gbc.setMsgId(channelId);
        if (DEBUG) Log.d(TAG, "enableSingleChannel slotId=" + slotId + ", channelId=" + channelId + ", enable=" + enable + ", removeMode=" + removeMode);
        GsmUmtsCellBroadcastSmsSwitchChannel startSwitch = new GsmUmtsCellBroadcastSmsSwitchChannel(gbc, mHandler, slotId, enable, removeMode, channelId, sKeyValuesEditor);
        startSwitch.start();
    }

    private CheckBoxPreference createAddMessageID(int slotId, int channelId, boolean enabled, boolean isLoadingUI) {
        CheckBoxPreference prf = new CheckBoxPreference(getActivity());
        String key = ((slotId == SLOTID_SLOT1) ? "" : PREFIX_SIM2) + String.valueOf(channelId);
        if (DEBUG) Log.d(TAG, "createAddMessageID key=" + key + ", channelId=" + channelId + ", enabled=" + enabled);

        prf.setKey(key);
        prf.setTitle(getString(R.string.area_info_msgs));
        prf.setSummaryOn(" (ID" + channelId + ") " + getString(R.string.area_info_msgs_enabled));
        prf.setSummaryOff(" (ID"+ channelId + ") " + getString(R.string.area_info_msgs_disabled));
        //prf.setChecked(enabled);

        sKeyValuesEditor.putBoolean(prf.getKey(), enabled);
        sKeyValuesEditor.commit();

        if(!isLoadingUI) {
            enableSingleChannel(slotId, channelId, enabled, false);
        }
        return prf;
    }

    private void removeAddedMessageID(Preference target) {
        // we may open this channel before
        if (target == null) return; // +++ andrew_tu@20140520 TT406969 : Fix NPE.
        if (DEBUG) Log.d(TAG, "removeAddedMessageID key=" + target.getKey() + ", status: " + ((CheckBoxPreference)target).isChecked());
        String key = target.getKey();

        if(((CheckBoxPreference)target).isChecked()) {
            int simId = key.startsWith(PREFIX_SIM2) ? SLOTID_SLOT2 : SLOTID_SLOT1;
            int channelId = Integer.valueOf(key.replace(PREFIX_SIM2, ""));
            enableSingleChannel(simId, channelId, false, true);
        } else {
            mPCategory.removePreference(target);

            sSlot1IDs.remove(key);
            sSlot2IDs.remove(key);
            sKeyValuesEditor.remove(target.getKey());
            sKeyValuesEditor.commit();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (DEBUG) Log.d(TAG, "((AdapterContextMenuInfo)menuInfo).id="+((AdapterContextMenuInfo)menuInfo).id);
        if (DEBUG) Log.d(TAG, "((AdapterContextMenuInfo)menuInfo).position="+((AdapterContextMenuInfo)menuInfo).position);
        int position = ((AdapterContextMenuInfo)menuInfo).position;

        Preference prf = (Preference) getPreferenceScreen().getRootAdapter().getItem(position);
        if (DEBUG) Log.d(TAG, prf.getKey());
        if (mPCategory.findPreference(prf.getKey()) != null) {
                if(!prf.getKey().toString().equals("add") && !prf.getKey().toString().equals(mPrefKeySMSCBLanguage)) {
                sContextMenuSelectKey = prf;
                menu.add(getString(R.string.edit));
                menu.add(getString(R.string.delete));
            }
        }
    }

    protected PreferenceFragment getPreferenceFragment() {
        return this;
    }
    //ASUS END, dion_yang

    private int getSlotId() {
        return mSlotId;
    }


    /*
     * For handle call back UI updating when enable/disable the CBS
     */
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Bundle channel_data = msg.getData();
            int slotId = channel_data.getInt(GsmUmtsCellBroadcastSmsSwitchChannel.KEY_SLOT);
            boolean activated = channel_data.getBoolean(GsmUmtsCellBroadcastSmsSwitchChannel.KEY_ACTIVATED);
            boolean isRemoveMode = channel_data.getBoolean(GsmUmtsCellBroadcastSmsSwitchChannel.KEY_REMOVE_MODE);
            int channel_id = channel_data.getInt(GsmUmtsCellBroadcastSmsSwitchChannel.KEY_CHANNEL_ID);
            String prefKey = (slotId == SLOTID_SLOT1 ? "" : PREFIX_SIM2) + String.valueOf(channel_id);
            Preference preference = mPCategory.findPreference(prefKey);
            if (DEBUG) {
                Log.d(TAG, "CB SETTING: handleMessage slotId= " + slotId + ", what= " + msg.what + ", activated: " + activated + ", prefKey: " + prefKey);
            }
            // Asus Jenpang: show progress dialog or not when activate/deactivate channel
            boolean show = CellBroadcastReceiverApp.getApplication().getResources()
                    .getBoolean(R.bool.show_progress_dialog);
            if (preference == null) {
                Log.w(TAG, "Preference wasn't existed!");
                // Asus Jenpang begin: show progress dialog or not when activate/deactivate channel
                if (show) {
                    cancelProgressDialog();
                }
                else {
                    Toast.makeText(CellBroadcastReceiverApp.getApplication(),
                            R.string.channel_failed, Toast.LENGTH_SHORT).show();
                }
                // Asus Jenpang end: show progress dialog or not when activate/deactivate channel
                return;
            }
            switch (msg.what) {
                case GsmUmtsCellBroadcastSmsSwitchChannel.CB_SET_SUCCESSED:
                    ((CheckBoxPreference) preference).setChecked(activated);
                    // Asus Jenpang begin: show progress dialog or not when activate/deactivate channel
                    if (show) {
                        cancelProgressDialog();
                    }
                    else {
                        Toast.makeText(CellBroadcastReceiverApp.getApplication(),
                                R.string.channel_succeeded, Toast.LENGTH_SHORT).show();
                    }
                    // Asus Jenpang end: show progress dialog or not when activate/deactivate channel
                    // if only if disable the channel success, we would starting remove operation
                    if (isRemoveMode) {
                        if (preference.getKey().startsWith(PREFIX_SIM2)) {
                            sSlot2IDs.remove(prefKey);
                        } else {
                            sSlot1IDs.remove(prefKey);
                        }
                        mPCategory.removePreference(preference);
                        sKeyValuesEditor.remove(prefKey);
                        sKeyValuesEditor.commit();
                    }
                    break;

                case GsmUmtsCellBroadcastSmsSwitchChannel.CB_EDIT_SET_SUCCESSED:
                    ((CheckBoxPreference) preference).setChecked(activated);
                    if (mPCategory != null) {
                        Preference prefs = mPCategory.findPreference(prefKey);
                        if (prefs != null) {
                            removeAddedMessageID(prefs);
                        }
                    }
                    break;

                case GsmUmtsCellBroadcastSmsSwitchChannel.CB_SET_FAILED:
                    displayErrorToast(activated, preference.getKey());

                    ((CheckBoxPreference) preference).setChecked(activated);
                    // Asus Jenpang begin: show progress dialog or not when activate/deactivate channel
                    if (show) {
                        cancelProgressDialog();
                    }
                    else {
                        Toast.makeText(CellBroadcastReceiverApp.getApplication(),
                                R.string.channel_failed, Toast.LENGTH_SHORT).show();
                    }
                    // Asus Jenpang end: show progress dialog or not when activate/deactivate channel
                    break;

                case GsmUmtsCellBroadcastSmsSwitchChannel.CB_UPDATING_SUCCESSED:
                    ((CheckBoxPreference) preference).setChecked(activated);
                    break;

                case GsmUmtsCellBroadcastSmsSwitchChannel.CB_UPDATING_FAILED:
                    ((CheckBoxPreference) preference).setChecked(!activated);
                    break;
            }
            // save preference
            CheckBoxPreference updatePref = (CheckBoxPreference) mPCategory.findPreference(prefKey);
            if (updatePref != null) {
                sKeyValuesEditor.putBoolean(prefKey, updatePref.isChecked());
                sKeyValuesEditor.commit();
            }
            preference.setEnabled(true);
            preference = null;
        }
    }

    private HashMap<String, Boolean> getIDs() {
        return (getSlotId() == SLOTID_SLOT1) ? sSlot1IDs : sSlot2IDs;
    }
    private String getInstanceTag() {
        String name = getClass().getSimpleName();
        return name;
    }
}
