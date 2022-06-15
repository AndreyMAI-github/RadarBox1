package org.rdr.radarbox.DSP;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.rdr.radarbox.R;
import org.rdr.radarbox.RadarBox;

/**
 * Класс для оценки отношения сигнал-шум. Внутри него можно получать актульную информацию об
 * актульаных средних значениях ОСШ {@link #getAvgSNR()},
 * о максимальном ОСШ {@link #getMaxSNR()}
 * @author Козлов Роман Юрьевич
 * @version 0.1
 */

public class SNR extends PreferenceFragmentCompat {
    static int nAccumulated = 10;
    static int nSnrAccumulated = 10;
    static double[][] accumulatedData;
    static double[] arrayMaxSNR;
    static double[] arrayAvgSNR;
    static double maxSNR = 0;
    static double avgSNR = 0;
    static double[] arraySNR;
    static int iFrame = 0;
    static int iSNR = 0;

    EditTextPreference pref;
    Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String stringValue = newValue.toString();
            if (preference instanceof EditTextPreference) {
                preference.setSummary(stringValue);
                ((EditTextPreference) preference).setText(stringValue);
            }
            return false;
        }
    };

    void bindSummaryValue(Preference preference){
        preference.setOnPreferenceChangeListener(listener);
        listener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(),""));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_dsp_snr,rootKey);

            pref = findPreference("nAccumulated");
            assert pref != null;
            bindSummaryValue(pref);
            pref.setSummary(pref.getText());
//        pref.setText(Integer.toString(nAccumulated));
            pref.setDefaultValue(Integer.toString(nAccumulated));

        pref = findPreference("nSnrAccumulated");
        assert pref != null;
        bindSummaryValue(pref);
        pref.setSummary(pref.getText());
//        pref.setText(Integer.toString(nSnrAccumulated));
        pref.setDefaultValue(Integer.toString(nSnrAccumulated));

    }

    public static void calculateSNR(double[] rawFreqFrame){
        accumulatedData = new double[nAccumulated][rawFreqFrame.length];
        // Raw Data Accumulation
        for (int f=0; f<rawFreqFrame.length; f++)
            accumulatedData[iFrame][f] = rawFreqFrame[f];
        iFrame++;
        if (iFrame >= nAccumulated)
            iFrame = 0;

        arrayMaxSNR = new double[nSnrAccumulated];
        arrayAvgSNR = new double[nSnrAccumulated];
        // Null SNRs
        for (int i=0; i<nSnrAccumulated; i++) {
            arrayAvgSNR[i] =0;
            arrayMaxSNR[i] = 0;
        }

        arraySNR = new double[rawFreqFrame.length];
        // Variance Calculation
        float mu ;
        for(int f=0; f<rawFreqFrame.length; f++){
            // Mu
            mu = 0;
            for (int n=0; n<nAccumulated; n++)
                mu += accumulatedData[n][f];
            mu /= nAccumulated;
            // Variance
            for (int n=0; n<nAccumulated; n++)
                arraySNR[f] = (float)Math.pow((accumulatedData[n][f]-mu),2);
            arraySNR[f] /= nAccumulated-1;
            // Maximum SNR
            if (arraySNR[f]>arrayMaxSNR[iSNR])
                arrayMaxSNR[iSNR] = arraySNR[f];
            // Average SNR
            arrayAvgSNR[iSNR] += arraySNR[f];
        }
        arrayAvgSNR[iSNR] /= rawFreqFrame.length;

        iSNR++;
        if (iSNR >= nSnrAccumulated) {
            // Average estimation
            avgSNR = 0;
            maxSNR = 0;
            for (int i=0; i<nSnrAccumulated; i++){
                avgSNR += arrayAvgSNR[i];
                maxSNR += arrayMaxSNR[i];
            }
            avgSNR /= nSnrAccumulated;
            maxSNR /= nSnrAccumulated;
            iSNR = 0;
        }
    }

    public static double getAvgSNR() {
        return avgSNR;
    }
    public static double getMaxSNR() {
        return maxSNR;
    }
    public static double[] getArrayAvgSNR() {
        return arrayAvgSNR;
    }
    public static double[] getArrayMaxSNR() {
        return arrayMaxSNR;
    }
}
