/*
 * Copyright (C) 2010-2011 The Android Open Source Project
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

package com.android.musicfx;

import com.android.audiofx.OpenSLESConstants;
import com.android.musicfx.widget.Gallery;
import com.android.musicfx.widget.InterceptableLinearLayout;
import com.android.musicfx.widget.Knob;
import com.android.musicfx.widget.Knob.OnKnobChangeListener;
import com.android.musicfx.widget.Visualizer;
import com.android.musicfx.widget.Visualizer.OnSeekBarChangeListener;

import android.Manifest;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.support.v4.content.ContextCompat;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.content.pm.PackageManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.NavigationView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import android.util.DisplayMetrics;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;
import java.util.UUID;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 *
 */
public class ActivityMusic extends AppCompatActivity {
    private final static String TAG = "MusicFXActivityMusic";

    /**
     * Max number of EQ bands supported
     */
    private final static int EQUALIZER_MAX_BANDS = 32;

    /**
     * Max levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MAX_LEVEL = 1000;

    /**
     * Min levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MIN_LEVEL = -1000;

    /**
     * Indicates if Virtualizer effect is supported.
     */
    private boolean mVirtualizerSupported;
    private boolean mVirtualizerIsHeadphoneOnly;
    /**
     * Indicates if BassBoost effect is supported.
     */
    private boolean mBassBoostSupported;
    /**
     * Indicates if Equalizer effect is supported.
     */
    private boolean mEqualizerSupported;
    /**
     * Indicates if Preset Reverb effect is supported.
     */
    private boolean mPresetReverbSupported;
    private boolean mStereoWideSupported;

    // Equalizer fields
    private final Visualizer[] mEqualizerVisualizer = new Visualizer[EQUALIZER_MAX_BANDS];
    private int mNumberEqualizerBands;
    private int mEqualizerMinBandLevel;
    private int mEQPresetUserPos = 1;
    private int mEQPreset;
    private int mEQPresetPrevious;
    private int[] mEQPresetUserBandLevelsPrev;
    private String[] mEQPresetNames;
    private String[] mReverbPresetNames;
    private String[] mSWStrengthNames;

    private int mPRPreset;
    private int mPRPresetPrevious;
    private Spinner mPRPresetSpinner;
    private int mSWStrength;
    private int mSWStrengthPrevious;
    private Spinner mSWStrengthSpinner;

    private boolean mPRPresetSpinnerInit;
    private boolean mSWStrengthSpinnerInit;
    private ArrayAdapter<String> mSWStrengthAdapter;
    private ArrayAdapter<String> mPRPresetAdapter;

    private boolean mIsHeadsetOn = false;
    private boolean mIsSpeakerOn = false;
    private boolean mIsBluetoothOn = false;

    private TextView mCurrentLevelText;
    private StringBuilder mFormatBuilder = new StringBuilder();
    private Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

    private String mCurrentLevel = ControlPanelEffect.SPEAKER_PREF_SCOPE;
    private NavigationView mDrawerList;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Switch mToolbarSwitch;
    private ViewGroup mViewGroup;
    private Gallery mGallery;
    private int mHighlightColor;
    private int mTextColor;
    private int mSpinoffColor;

    private Runnable mDoAfterPermCheck;

    private static final int PERMISSION_REQUEST_STORAGE = 0;

    /**
     * Array containing RSid of preset reverb names.
     */
    private static final int[] mReverbPresetRSids = {
            R.string.none, R.string.smallroom, R.string.mediumroom, R.string.largeroom,
            R.string.mediumhall, R.string.largehall, R.string.plate
    };

    /**
     * Presets
     */
    private static final String PRESETS_FOLDER = "MusicFXPresets";

    /**
     * Context field
     */
    private Context mContext;

    private final BroadcastReceiver mPrefLevelChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ControlPanelEffect.PREF_SCOPE_CHANGED)) {
                Log.i(TAG, "onReceive " + action);
                if (mVirtualizerSupported || mBassBoostSupported || mEqualizerSupported
                        || mPresetReverbSupported || mStereoWideSupported) {
                    String currentLevel = ControlPanelEffect.getCurrentPrevLevel(ActivityMusic.this);
                    updateCurrentLevelInfo(currentLevel);
                }
            }
        }
    };

    /*
     * Declares and initializes all objects and widgets in the layouts and the CheckBox and SeekBar
     * onchange methods on creation.
     *
     * (non-Javadoc)
     *
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init context to be used in listeners
        mContext = this;
        mCurrentLevel = ControlPanelEffect.getCurrentPrevLevel(this);
        Log.d(TAG, "onCreate " + mCurrentLevel);

        ControlPanelEffect.initEffectsPreferences(mContext);

        // query available effects
        final Descriptor[] effects = AudioEffect.queryEffects();

        // Determine available/supported effects
        Log.d(TAG, "Available effects:");
        for (final Descriptor effect : effects) {
            Log.d(TAG, effect.name.toString() + ", type: " + effect.type.toString());

            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
                mVirtualizerIsHeadphoneOnly = !isVirtualizerTransauralSupported();
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                mEqualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_PRESET_REVERB)) {
                mPresetReverbSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_STEREOWIDE)) {
                mStereoWideSupported = true;
            }
        }

        setContentView(R.layout.music_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mViewGroup = (ViewGroup) findViewById(R.id.contentSoundEffects);
        mHighlightColor = getResources().getColor(R.color.highlight_gallery_text);
        mTextColor = getResources().getColor(R.color.textColor);
        mSpinoffColor = getResources().getColor(R.color.spinner_disabled);

        // Fill array with presets from AudioEffects call.
        // allocate a space for 1 extra strings (User)
        final int numPresets = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_num_presets);
        mEQPresetNames = new String[numPresets + 1];
        for (short i = 0; i < numPresets; i++) {
            final String eqPresetName = ControlPanelEffect.getParameterString(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.eq_preset_name, i);
            mEQPresetNames[i] = localizePresetName(eqPresetName);
        }
        mEQPresetNames[numPresets] = getString(R.string.user);
        mEQPresetUserPos = numPresets;

        // Load string resource of reverb presets
        mReverbPresetNames = new String[mReverbPresetRSids.length];
        for (short i = 0; i < mReverbPresetRSids.length; ++i) {
            mReverbPresetNames[i] = getString(mReverbPresetRSids[i]);
        }
        mSWStrengthNames = getResources().getStringArray(R.array.stereowide_modes);

        mCurrentLevelText = (TextView)findViewById(R.id.switchstatus);
        mCurrentLevelText.setCompoundDrawableTintList(new ColorStateList(new int[][] { new int[0] },
                new int[] { getResources().getColor(R.color.current_out_source_color) }));

        // Watch for button clicks and initialization.
        if (mVirtualizerSupported || mBassBoostSupported || mEqualizerSupported
                || mPresetReverbSupported || mStereoWideSupported) {
            // Initialize the Virtualizer elements.
            // Set the SeekBar listener.
            if (mVirtualizerSupported) {
                final Knob knob = (Knob) findViewById(R.id.vIStrengthKnob);
                knob.setMax(OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH -
                        OpenSLESConstants.VIRTUALIZER_MIN_STRENGTH);
                knob.setOnKnobChangeListener(new OnKnobChangeListener() {
                    // Update the parameters while Knob changes and set the
                    // effect parameter.
                    @Override
                    public void onValueChanged(final Knob knob, final int value,
                                               final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.virt_strength, value);
                    }

                    @Override
                    public boolean onSwitchChanged(final Knob knob, boolean on) {
                        if (on && (!mIsHeadsetOn && !mIsBluetoothOn && mVirtualizerIsHeadphoneOnly)) {
                            showHeadsetMsg(getString(R.string.headset_plug));
                            return false;
                        }
                        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.virt_enabled, on);
                        return true;
                    }
                });
            }

            // Initialize the Bass Boost elements.
            // Set the SeekBar listener.
            if (mBassBoostSupported) {
                final Knob knob = (Knob) findViewById(R.id.bBStrengthKnob);
                knob.setMax(OpenSLESConstants.BASSBOOST_MAX_STRENGTH
                        - OpenSLESConstants.BASSBOOST_MIN_STRENGTH);
                knob.setOnKnobChangeListener(new OnKnobChangeListener() {
                    // Update the parameters while SeekBar changes and set the
                    // effect parameter.

                    @Override
                    public void onValueChanged(final Knob knob, final int value,
                                               final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.bb_strength, value);
                    }

                    @Override
                    public boolean onSwitchChanged(final Knob knob, boolean on) {
                        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                                ControlPanelEffect.Key.bb_enabled, on);
                        return true;
                    }
                });
            }

            mGallery = (Gallery) findViewById(R.id.eqPresets);
            // Initialize the Equalizer elements.
            if (mEqualizerSupported) {
                mEQPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.eq_current_preset);
                if (mEQPreset >= mEQPresetNames.length) {
                    mEQPreset = 0;
                }
                mEQPresetPrevious = mEQPreset;
                equalizerBandsInit((LinearLayout) findViewById(R.id.eqcontainer));
                equalizerPresetsInit();
            }

            // Initialize the Preset Reverb elements.
            // Set Spinner listeners.
            mPRPresetSpinner = (Spinner) findViewById(R.id.prSpinner);
            if (mPresetReverbSupported) {
                mPRPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.pr_current_preset);
                mPRPresetPrevious = mPRPreset;
                reverbSpinnerInit();
            }

            View spSpinnerContainer = findViewById(R.id.swSpinnerContainer);
            mSWStrengthSpinner = (Spinner) findViewById(R.id.swSpinner);
            if (mStereoWideSupported) {
                mSWStrength = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.sw_strength);
                mSWStrengthPrevious = mSWStrength;
                stereoWideSpinnerInit();
            } else {
                spSpinnerContainer.setVisibility(View.GONE);
            }
        } else {
            mViewGroup.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.noEffectsTextView)).setVisibility(View.VISIBLE);
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (NavigationView) findViewById(R.id.left_drawer);

        // Set the list's click listener
        mDrawerList.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                mDrawerLayout.closeDrawer(mDrawerList);
                if (id == R.id.menu_speaker) {
                    updateForLevel(ControlPanelEffect.SPEAKER_PREF_SCOPE);
                } else if (id == R.id.menu_headset) {
                    updateForLevel(ControlPanelEffect.HEADSET_PREF_SCOPE);
                } else if (id == R.id.menu_bluetooth) {
                    updateForLevel(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);
                } else if (id == R.id.save_preset) {
                    needRequestStoragePermission(new Runnable() {
                        @Override
                        public void run() {
                            savePresetDialog();
                        }
                    });
                } else if (id == R.id.load_preset) {
                    needRequestStoragePermission(new Runnable() {
                        @Override
                        public void run() {
                            loadPresetDialog();
                        }
                    });
                }

                return true;
            }
        });
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();
    }

    @Override
    public void setTitle(CharSequence title) {
        getSupportActionBar().setTitle(title);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void updateForLevel(String level) {
        if (!level.equals(mCurrentLevel)) {
            mCurrentLevel = level;
            mIsHeadsetOn = mCurrentLevel.equals(ControlPanelEffect.HEADSET_PREF_SCOPE);
            mIsSpeakerOn = mCurrentLevel.equals(ControlPanelEffect.SPEAKER_PREF_SCOPE);
            mIsBluetoothOn = mCurrentLevel.equals(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);
            updateUI();
            updateTitle();
        }
    }

    private void updateTitle() {
        if (mCurrentLevel == ControlPanelEffect.SPEAKER_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_speaker));
            mDrawerList.getMenu().findItem(R.id.menu_speaker).setChecked(true);
        } else if (mCurrentLevel == ControlPanelEffect.HEADSET_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_headset));
            mDrawerList.getMenu().findItem(R.id.menu_headset).setChecked(true);
        } else if (mCurrentLevel == ControlPanelEffect.BLUETOOTH_PREF_SCOPE) {
            setTitle(getResources().getString(R.string.drawer_item_bluetooth));
            mDrawerList.getMenu().findItem(R.id.menu_bluetooth).setChecked(true);
        }
    }

    private void updateCurrentLevelInfo(String level) {
        if (level == ControlPanelEffect.SPEAKER_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_speaker) + " " + getResources().getString(R.string.inuse_string));
        } else if (level == ControlPanelEffect.HEADSET_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_headset) + " " + getResources().getString(R.string.inuse_string));
        } else if (level == ControlPanelEffect.BLUETOOTH_PREF_SCOPE) {
            mCurrentLevelText.setText(getResources().getString(R.string.drawer_item_bluetooth) + " " + getResources().getString(R.string.inuse_string));
        }
    }

    private final String localizePresetName(final String name) {
        final String[] names = {
                "Normal", "Classical", "Dance", "Flat", "Folk",
                "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"
        };
        final int[] ids = {
                R.string.normal, R.string.classical, R.string.dance, R.string.flat, R.string.folk,
                R.string.heavy_metal, R.string.hip_hop, R.string.jazz, R.string.pop, R.string.rock
        };

        for (int i = names.length - 1; i >= 0; --i) {
            if (names[i].equals(name)) {
                return getString(ids[i]);
            }
        }
        return name;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        mToolbarSwitch = (Switch) menu.findItem(R.id.toolbar_switch).getActionView().findViewById(R.id.toolbar_switch_button);
        mToolbarSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // set parameter and state
                ControlPanelEffect.setEnabled(mContext, mCurrentLevel, isChecked);
                // Enable Linear layout (in scroll layout) view with all
                // effect contents depending on checked state
                setEnabledAllChildren(mViewGroup, isChecked);
                // update UI according to headset state
                updateUIHeadset(false);
                setInterception(isChecked);
            }
        });
        updateUI();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (id == R.id.toolbar_switch) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * (non-Javadoc)
     *            // Update UI

     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (mVirtualizerSupported || mBassBoostSupported || mEqualizerSupported
                || mPresetReverbSupported || mStereoWideSupported) {

            if (!isServiceRunning()) {
                if (!SystemProperties.getBoolean("ro.musicfx.disabled", false)) {
                    Log.d(TAG, "starting SystemService from onResume");
                    startService(new Intent(this, SystemService.class));
                }
            }
            mCurrentLevel = ControlPanelEffect.getCurrentPrevLevel(this);
            mIsHeadsetOn = mCurrentLevel.equals(ControlPanelEffect.HEADSET_PREF_SCOPE);
            mIsSpeakerOn = mCurrentLevel.equals(ControlPanelEffect.SPEAKER_PREF_SCOPE);
            mIsBluetoothOn = mCurrentLevel.equals(ControlPanelEffect.BLUETOOTH_PREF_SCOPE);

            updateUI();
            updateTitle();
            updateCurrentLevelInfo(mCurrentLevel);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ControlPanelEffect.PREF_SCOPE_CHANGED);
            registerReceiver(mPrefLevelChanged, intentFilter);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mPrefLevelChanged);
        } catch (Exception e) {
        }
    }

    private void reverbSpinnerInit() {
        mPRPresetAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, mReverbPresetNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View item = super.getView(position, convertView, parent);
                if (position == mPRPreset && position != 0 && mPRPresetSpinner.isEnabled()) {
                    ((TextView) item.findViewById(android.R.id.text1)).setTextColor(mHighlightColor);
                } else {
                    ((TextView) item.findViewById(android.R.id.text1)).setTextColor(mSpinoffColor);
                }
                return item;
            }
        };
        mPRPresetSpinner.setAdapter(mPRPresetAdapter);
        mPRPresetSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mPRPresetSpinnerInit) {
                    mPRPresetSpinnerInit = true;
                    return;
                }
                if (position != mPRPresetPrevious) {
                    mPRPreset = position;
                    presetReverbSetPreset(position);
                    TextView tv = (TextView) parent.getChildAt(0);
                    if (tv != null) {
                        if (position != 0 && mPRPresetSpinner.isEnabled()) {
                            tv.setTextColor(mHighlightColor);
                        } else {
                            tv.setTextColor(mSpinoffColor);
                        }
                    }
                }
                mPRPresetPrevious = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mPRPresetSpinner.setSelection(mPRPreset);
        mPRPresetSpinner.setBackgroundResource(R.drawable.rev_spinner);
    }

    private void stereoWideSpinnerInit() {
        mSWStrengthAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, mSWStrengthNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View item = super.getView(position, convertView, parent);
                if (position == mSWStrength && position != 0 && mSWStrengthSpinner.isEnabled()) {
                    ((TextView) item.findViewById(android.R.id.text1)).setTextColor(mHighlightColor);
                } else {
                    ((TextView) item.findViewById(android.R.id.text1)).setTextColor(mSpinoffColor);
                }
                return item;
            }
        };
        mSWStrengthSpinner.setAdapter(mSWStrengthAdapter);
        mSWStrengthSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mSWStrengthSpinnerInit) {
                    mSWStrengthSpinnerInit = true;
                    return;
                }
                if (position != mSWStrengthPrevious) {
                    mSWStrength = position;
                    stereoWideSetStrength(position);
                    TextView tv = (TextView) parent.getChildAt(0);
                    if (tv != null) {
                        if (position != 0 && mSWStrengthSpinner.isEnabled()) {
                            tv.setTextColor(mHighlightColor);
                        } else {
                            tv.setTextColor(mSpinoffColor);
                        }
                    }
                }
                mSWStrengthPrevious = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mSWStrengthSpinner.setSelection(mSWStrength);
        mSWStrengthSpinner.setBackgroundResource(R.drawable.rev_spinner);
    }

    private void equalizerPresetsInit() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.equalizer_presets,
                mEQPresetNames);

        mGallery.setAdapter(adapter);
        mGallery.setOnItemSelectedListener(new Gallery.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                if (position != mEQPresetPrevious) {
                    mEQPreset = position;
                    showSeekBar(position == mEQPresetUserPos);
                    equalizerSetPreset(position);
                    equalizerUpdateDisplay();
                }
                mEQPresetPrevious = position;
            }
        });
        mGallery.setSelection(mEQPreset);
        showSeekBar(mEQPreset == mEQPresetUserPos);
        equalizerUpdateDisplay();
    }


    /**
     * En/disables all children for a given view. For linear and relative layout children do this
     * recursively
     *
     * @param viewGroup
     * @param enabled
     */
    private void setEnabledAllChildren(final ViewGroup viewGroup, final boolean enabled) {
        final int count = viewGroup.getChildCount();
        final View bb = findViewById(R.id.bBStrengthKnob);
        final View virt = findViewById(R.id.vIStrengthKnob);
        final View eq = findViewById(R.id.eqcontainer);
        boolean on = true;

        for (int i = 0; i < count; i++) {
            final View view = viewGroup.getChildAt(i);
            if ((view instanceof LinearLayout) || (view instanceof RelativeLayout)) {
                final ViewGroup vg = (ViewGroup) view;
                setEnabledAllChildren(vg, enabled);
            }

            if (enabled && view == virt) {
                on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.virt_enabled);
                view.setEnabled(on);
            } else if (enabled && view == bb) {
                on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                        ControlPanelEffect.Key.bb_enabled);
                view.setEnabled(on);
            } else if (enabled && view == eq) {
                showSeekBar(mEQPreset == mEQPresetUserPos);
                view.setEnabled(true);
            } else {
                view.setEnabled(enabled);
            }
        }
        if (mPRPresetAdapter != null) {
            mPRPresetAdapter.notifyDataSetChanged();
        }
        if (mSWStrengthAdapter != null) {
            mSWStrengthAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Updates UI (checkbox, seekbars, enabled states) according to the current stored preferences.
     */
    private void updateUI() {
        if (mToolbarSwitch == null) {
            return;
        }
        final boolean isEnabled = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                ControlPanelEffect.Key.global_enabled);
        mToolbarSwitch.setChecked(isEnabled);
        setEnabledAllChildren(mViewGroup, isEnabled);
        updateUIHeadset(false);

        if (mVirtualizerSupported) {
            Knob knob = (Knob) findViewById(R.id.vIStrengthKnob);
            int strength = ControlPanelEffect
                    .getParameterInt(mContext, mCurrentLevel,
                            ControlPanelEffect.Key.virt_strength);
            knob.setValue(strength);
            boolean hasStrength = ControlPanelEffect.getParameterBoolean(mContext,
                    mCurrentLevel, ControlPanelEffect.Key.virt_strength_supported);
            if (!hasStrength) {
                knob.setVisibility(View.GONE);
            }
        }
        if (mBassBoostSupported) {
            ((Knob) findViewById(R.id.bBStrengthKnob)).setValue(ControlPanelEffect
                    .getParameterInt(mContext, mCurrentLevel,
                            ControlPanelEffect.Key.bb_strength));
        }
        if (mEqualizerSupported) {
            mEQPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.eq_current_preset);
            if (mEQPreset >= mEQPresetNames.length) {
                mEQPreset = 0;
            }
            mGallery.setSelection(mEQPreset);
        }
        if (mPresetReverbSupported) {
            mPRPreset = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.pr_current_preset);
            mPRPresetSpinner.setSelection(mPRPreset);
        }
        if (mStereoWideSupported) {
            mSWStrength = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.sw_strength);
            mSWStrengthSpinner.setSelection(mSWStrength);
        }
        setInterception(isEnabled);
    }

    private void setInterception(boolean isEnabled) {
        final InterceptableLinearLayout ill = (InterceptableLinearLayout) mViewGroup;
        ill.setInterception(!isEnabled);
        if (isEnabled) {
            ill.setOnClickListener(null);
        } else {
            ill.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Toast toast = Toast.makeText(mContext,
                            getString(R.string.power_on_prompt), Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            });
        }
    }

    /**
     * Updates UI for headset mode. En/disable VI and BB controls depending on
     * headset state (on/off) if effects are on. Do the inverse for their
     * layouts so they can take over control/events.
     */
    private void updateUIHeadset(boolean force) {
        final Knob bBKnob = (Knob) findViewById(R.id.bBStrengthKnob);
        //bBKnob.setBinary(mIsSpeakerOn);
        bBKnob.setEnabled(mToolbarSwitch.isChecked());
        final Knob vIKnob = (Knob) findViewById(R.id.vIStrengthKnob);
        vIKnob.setEnabled(mToolbarSwitch.isChecked());

        if (!force) {
            boolean on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.bb_enabled);
            bBKnob.setOn(mToolbarSwitch.isChecked() && on);
            on = ControlPanelEffect.getParameterBoolean(mContext, mCurrentLevel,
                    ControlPanelEffect.Key.virt_enabled);
            vIKnob.setOn(mToolbarSwitch.isChecked() && on);
        }
    }

    /**
     * Initializes the equalizer elements. Set the SeekBars and Spinner listeners.
     */
    private void equalizerBandsInit(LinearLayout eqcontainer) {
        // Initialize the N-Band Equalizer elements.
        mNumberEqualizerBands = ControlPanelEffect.getParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_num_bands);
        mEQPresetUserBandLevelsPrev = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_preset_user_band_level);
        final int[] centerFreqs = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_center_freq);
        final int[] bandLevelRange = ControlPanelEffect.getParameterIntArray(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_level_range);
        mEqualizerMinBandLevel = (int) Math.min(EQUALIZER_MIN_LEVEL, bandLevelRange[0]);
        final int mEqualizerMaxBandLevel = (int) Math.max(EQUALIZER_MAX_LEVEL, bandLevelRange[1]);
        final OnSeekBarChangeListener listener = new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final Visualizer v, final int progress,
                                          final boolean fromUser) {
                for (short band = 0; band < mNumberEqualizerBands; ++band) {
                    if (mEqualizerVisualizer[band] == v) {
                        final short level = (short) (progress + mEqualizerMinBandLevel);
                        if (fromUser) {
                            equalizerBandUpdate(band, level);
                        }
                        break;
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(final Visualizer v) {
            }

            @Override
            public void onStopTrackingTouch(final Visualizer v) {
            }
        };

        final OnTouchListener tl = new OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mEQPreset != mEQPresetUserPos) {
                            final Toast toast = Toast.makeText(mContext,
                                    getString(R.string.eq_custom), Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.TOP | Gravity.CENTER, 0,
                                    toast.getYOffset() * 2);
                            toast.show();
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        };

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int pixels = getResources().getDimensionPixelOffset(R.dimen.each_visualizer_width);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                pixels, ViewGroup.LayoutParams.MATCH_PARENT);
        for (int band = 0; band < mNumberEqualizerBands; band++) {
            // Unit conversion from mHz to Hz and use k prefix if necessary to display
            final int centerFreq = centerFreqs[band] / 1000;
            float centerFreqHz = centerFreq;
            String unitPrefix = "";
            if (centerFreqHz >= 1000) {
                centerFreqHz = centerFreqHz / 1000;
                unitPrefix = "k";
            }

            final Visualizer v = new Visualizer(mContext);
            v.setText(format("%.0f", centerFreqHz) + unitPrefix);
            v.setMax(mEqualizerMaxBandLevel - mEqualizerMinBandLevel);
            v.setOnSeekBarChangeListener(listener);
            v.setOnTouchListener(tl);
            eqcontainer.addView(v, lp);
            mEqualizerVisualizer[band] = v;
        }

        TextView tv = (TextView) findViewById(R.id.maxLevelText);
        tv.setText(String.format("+%d dB", (int) Math.ceil(mEqualizerMaxBandLevel / 100)));
        tv = (TextView) findViewById(R.id.centerLevelText);
        tv.setText("0 dB");
        tv = (TextView) findViewById(R.id.minLevelText);
        tv.setText(String.format("%d dB", (int) Math.floor(mEqualizerMinBandLevel / 100)));
    }

    private String format(String format, Object... args) {
        mFormatBuilder.setLength(0);
        mFormatter.format(format, args);
        return mFormatBuilder.toString();
    }

    private void showSeekBar(boolean show) {
        for (int i = 0; i < mNumberEqualizerBands; ++i) {
            mEqualizerVisualizer[i].setShowSeekBar(show);
        }
    }

    /**
     * Updates the EQ by getting the parameters.
     */
    private void equalizerUpdateDisplay() {
        // Update and show the active N-Band Equalizer bands.
        final int[] bandLevels = ControlPanelEffect.getParameterIntArray(mContext,
                mCurrentLevel, ControlPanelEffect.Key.eq_band_level);
        for (short band = 0; band < mNumberEqualizerBands; band++) {
            final int level = bandLevels[band];
            final int progress = level - mEqualizerMinBandLevel;
            mEqualizerVisualizer[band].setProgress(progress);
        }
    }

    /**
     * Updates/sets a given EQ band level.
     *
     * @param band  Band id
     * @param level EQ band level
     */
    private void equalizerBandUpdate(final int band, final int level) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_band_level, level, band);
    }

    /**
     * Sets the given EQ preset.
     *
     * @param preset EQ preset id.
     */
    private void equalizerSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.eq_current_preset, preset);
    }

    /**
     * Sets the given PR preset.
     *
     * @param preset PR preset id.
     */
    private void presetReverbSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.pr_current_preset, preset);
        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                ControlPanelEffect.Key.pr_enabled, preset != 0);
    }

    private void stereoWideSetStrength(final int strength) {
        ControlPanelEffect.setParameterInt(mContext, mCurrentLevel,
                ControlPanelEffect.Key.sw_strength, strength);
        ControlPanelEffect.setParameterBoolean(mContext, mCurrentLevel,
                ControlPanelEffect.Key.sw_enabled, strength != 0);
    }

    /**
     * Show msg that headset needs to be plugged.
     */
    private void showHeadsetMsg(String message) {
        final Context context = getApplicationContext();
        final int duration = Toast.LENGTH_SHORT;

        final Toast toast = Toast.makeText(context, message, duration);
        toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
        toast.show();
    }

    private boolean isServiceRunning() {
        final ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(getPackageName() + ".SystemService")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVirtualizerTransauralSupported() {
        Virtualizer virt = null;
        boolean transauralSupported = false;
        try {
            virt = new Virtualizer(0, android.media.AudioSystem.newAudioSessionId());
            transauralSupported = virt.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO,
                    Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL);
        } catch (Exception e) {
        } finally {
            if (virt != null) {
                virt.release();
            }
        }
        return transauralSupported;
    }

    public void savePresetDialog() {
        // We first list existing presets
        File presetsDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PRESETS_FOLDER);
        presetsDir.mkdirs();

        Log.e(TAG, "Saving preset to " + presetsDir.getAbsolutePath());

        // The first entry is "New preset", so we offset
        File[] presets = presetsDir.listFiles((FileFilter) null);
        final String[] names = new String[presets != null ? presets.length + 1 : 1];
        names[0] = getString(R.string.new_preset);
        if (presets != null) {
            for (int i = 0; i < presets.length; i++) {
                names[i + 1] = presets[i].getName();
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ActivityMusic.this);
        builder.setTitle(R.string.save_preset)
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // New preset, we ask for the name
                            AlertDialog.Builder inputBuilder = new AlertDialog.Builder(ActivityMusic.this);
                            inputBuilder.setTitle(R.string.new_preset);

                            // Set an EditText view to get user input
                            final EditText input = new EditText(ActivityMusic.this);
                            inputBuilder.setView(input);

                            inputBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    String value = input.getText().toString();
                                    savePreset(value);
                                }
                            });
                            inputBuilder.setNegativeButton(android.R.string.cancel, null);

                            inputBuilder.show();
                        } else {
                            savePreset(names[which]);
                        }
                    }
                });
        Dialog dlg = builder.create();
        dlg.show();
    }

    public void loadPresetDialog() {
        File presetsDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PRESETS_FOLDER);
        presetsDir.mkdirs();

        File[] presets = presetsDir.listFiles((FileFilter) null);
        final String[] names = new String[presets != null ? presets.length : 0];
        if (presets != null) {
            for (int i = 0; i < presets.length; i++) {
                names[i] = presets[i].getName();
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ActivityMusic.this);
        builder.setTitle(R.string.load_preset)
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        loadPreset(names[which]);
                    }
                });
        builder.create().show();
    }

    public void savePreset(String name) {
        final String spDir = getApplicationInfo().dataDir + "/shared_prefs/";

        // Copy the SharedPreference to our output directory
        File presetDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PRESETS_FOLDER + "/" + name);
        presetDir.mkdirs();

        try {
            for (String prefLevel : ControlPanelEffect.ALL_PREF_SCOPES) {
                copy(new File(spDir, prefLevel + ".xml"), new File(presetDir, prefLevel + ".xml"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot save preset", e);
        }

        Toast.makeText(getApplicationContext(), mContext.getString(R.string.preset_save_toast), Toast.LENGTH_SHORT).show();
    }

    public void loadPreset(String name) {
        File presetDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + PRESETS_FOLDER + "/" + name);
        if (!presetDir.exists()) {
            return;
        }

        try {
            loadAllXMLFile(presetDir.getAbsolutePath());
            updateUI();
        } catch (Exception e) {
            Log.e(TAG, "LoadPreset Exception : " + e);
        }

        Toast.makeText(getApplicationContext(), mContext.getString(R.string.preset_load_toast), Toast.LENGTH_SHORT).show();
    }


    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private boolean needRequestStoragePermission(Runnable runWithPerms) {
        boolean needRequest = false;
        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            int count = permissionList.size();
            if (count > 0) {
                String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }
                mDoAfterPermCheck = runWithPerms;
                requestPermissions(permissionArray, PERMISSION_REQUEST_STORAGE);
            }
        } else {
            runWithPerms.run();
        }

        return needRequest;
    }

    private boolean checkPermissionGrantResults(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_STORAGE: {
                if (checkPermissionGrantResults(grantResults)) {
                    if (mDoAfterPermCheck != null) {
                        mDoAfterPermCheck.run();
                        mDoAfterPermCheck = null;
                    }
                }
            }
        }
    }

    private void loadRessourcesFromXmlParser(XmlPullParser parser, String prefLevel) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();

        final SharedPreferences sharedPreferences = getSharedPreferences(prefLevel, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        do {
            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }
            switch (parser.getName()) {
                case "boolean":
                    editor.putBoolean(parser.getAttributeValue(null, "name"), Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                    break;
                case "int":
                    editor.putInt(parser.getAttributeValue(null, "name"), Integer.parseInt(parser.getAttributeValue(null, "value")));
                    break;
                case "string":
                    editor.putString(parser.getAttributeValue(null, "name"), parser.getAttributeValue(null, "value"));
                default:
                    break;
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);

        editor.commit();
    }

    private void loadXMLFile(String xmlFileName, String prefLevel) throws XmlPullParserException, IOException {

        InputStream in = null;
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;

        try {
            factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            in = new FileInputStream(xmlFileName);
            parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            loadRessourcesFromXmlParser(parser, prefLevel);
        } catch (Exception e) {
            Log.e(TAG, "loadXMLFile Exception :" + e);
        }
    }

    private void loadAllXMLFile(String fromDir) throws XmlPullParserException, IOException {
        for (String prefLevel : ControlPanelEffect.ALL_PREF_SCOPES) {
            loadXMLFile(new File(fromDir, prefLevel + ".xml").getAbsolutePath(), prefLevel);
        }
    }
}

