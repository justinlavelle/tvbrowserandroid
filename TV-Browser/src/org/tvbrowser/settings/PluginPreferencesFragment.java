/*
 * TV-Browser for Android
 * Copyright (C) 2014 René Mach (rene@tvbrowser.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to use, copy, modify or merge the Software,
 * furthermore to publish and distribute the Software free of charge without modifications and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.tvbrowser.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import org.tvbrowser.devplugin.Plugin;
import org.tvbrowser.devplugin.PluginManager;
import org.tvbrowser.devplugin.PluginServiceConnection;
import org.tvbrowser.tvbrowser.R;
import org.tvbrowser.utils.CompatUtils;
import org.tvbrowser.utils.IOUtils;
import org.tvbrowser.view.InfoPreferenceCompat;

import java.util.concurrent.atomic.AtomicReference;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * The preferences fragment for the plugins.
 * 
 * @author René Mach
 */
public class PluginPreferencesFragment extends PreferenceFragmentCompat {

  @Override
  public void onDetach() {
    super.onDetach();
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final String mPluginId;
    if (savedInstanceState == null) {
      mPluginId = getArguments().getString("pluginId");
    } else {
      // Orientation Change
      mPluginId = savedInstanceState.getString("pluginId");
    }
    Log.d("info9",""+mPluginId);
    // Load the preferences from an XML resource

    androidx.preference.PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
    // add preferences using preferenceScreen.addPreference()
    this.setPreferenceScreen(preferenceScreen);
    preferenceScreen.setTitle("ccccc");

    final PluginPreferencesActivity activity = PluginPreferencesActivity.getInstance();

    if (activity != null) {
      final PluginManager pluginManager = activity.getPluginManager();
      final PluginServiceConnection pluginConnection = PluginPreferencesActivity.getInstance().getServiceConnectionWithId(mPluginId);

      //  pluginConnection.unbindPlugin(getActivity().getApplicationContext());

      SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

      if (pluginConnection != null) {
        final CheckBoxPreference activated = new CheckBoxPreference(getActivity());
        activated.setTitle(R.string.pref_activated);
        activated.setKey(mPluginId + "_ACTIVATED");
        activated.setChecked(pref.getBoolean(pluginConnection.getId() + "_ACTIVATED", true));

        preferenceScreen.addPreference(activated);

        String description = pluginConnection.getPluginDescription();

        InfoPreferenceCompat uninstall = new InfoPreferenceCompat(getActivity());
        uninstall.setTitle(R.string.uninstall);
        uninstall.setOnPreferenceClickListener(preference -> {
          final Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
          intent.setData(Uri.parse("package:"+pluginConnection.getPackageId()));
          intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
          startActivityForResult(intent, PluginPreferencesActivity.REQUEST_CODE_UNINSTALL);

          return false;
        });
        preferenceScreen.addPreference(uninstall);

        if (description != null) {
          InfoPreferenceCompat descriptionPref = new InfoPreferenceCompat(getActivity());
          descriptionPref.setTitle(R.string.pref_plugins_description);
          descriptionPref.setSummary(description);
          preferenceScreen.addPreference(descriptionPref);
        }

        final AtomicReference<Preference> startSetupRef = new AtomicReference<>(null);
        if (pluginConnection != null && pluginConnection.hasPreferences()) {
          final Preference startSetup = new Preference(getActivity());
          startSetup.setTitle(R.string.pref_open);
          startSetup.setKey(mPluginId);
          startSetup.setOnPreferenceClickListener(preference -> {
            try {
              if (pluginConnection != null) {
                Plugin plugin = pluginConnection.getPlugin();

                if (plugin != null && pluginConnection.isBound(getActivity().getApplicationContext())) {
                  plugin.openPreferences(IOUtils.getChannelList(getActivity()));
                } else {
                  final Context bindContext = getActivity();

                  if (pluginConnection.bindPlugin(bindContext, null)) {
                    pluginConnection.getPlugin().onActivation(pluginManager);
                    pluginConnection.getPlugin().openPreferences(IOUtils.getChannelList(bindContext));
                    pluginConnection.callOnDeactivation();

                    pluginConnection.unbindPlugin(bindContext);
                  }
                }


              }
            } catch (Throwable ignored) {
            }

            return true;
          });

          preferenceScreen.addPreference(startSetup);

          startSetup.setEnabled(activated.isChecked());
          startSetupRef.set(startSetup);
        }

        activated.setOnPreferenceChangeListener((preference, newValue) -> {
          if (pluginConnection != null) {
            final AtomicReference<Context> mBindContextRef = new AtomicReference<>(null);

            Runnable runnable = () -> {
              if (startSetupRef.get() != null) {
                startSetupRef.get().setEnabled((Boolean) newValue);
              }

              if ((Boolean) newValue) {
                try {
                  pluginConnection.getPlugin().onActivation(pluginManager);
                } catch (RemoteException e) {
                  e.printStackTrace();
                }
              } else {
                pluginConnection.callOnDeactivation();
              }

              if (mBindContextRef.get() != null) {
                pluginConnection.callOnDeactivation();
                pluginConnection.unbindPlugin(mBindContextRef.get());
              }
            };

            Plugin plugin = pluginConnection.getPlugin();

            if (plugin == null) {
              mBindContextRef.set(getActivity());
              if (pluginConnection.bindPlugin(mBindContextRef.get(), null)) {
                runnable.run();
              }
            } else {
              runnable.run();
            }
          }

          return true;
        });

        String license = pluginConnection.getPluginLicense();

        if (license != null) {
          InfoPreferenceCompat licensePref = new InfoPreferenceCompat(getActivity());
          licensePref.setTitle(R.string.pref_plugins_license);
          licensePref.setSummary(CompatUtils.fromHtml(license));

          preferenceScreen.addPreference(licensePref);
        }
      }
    }
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if(requestCode == PluginPreferencesActivity.REQUEST_CODE_UNINSTALL) {
      if(getActivity() instanceof  ActivityPluginFragment) {
        getActivity().finish();
      }

      PluginPreferencesActivity.getInstance().onActivityResult(requestCode, resultCode, data);
      Log.d("info9",""+getActivity());
   //   ((PluginPreferencesActivity) getActivity()).onActivityResult(requestCode, resultCode, data);
    }
    else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}
