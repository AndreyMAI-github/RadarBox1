package org.rdr.radarbox.DSP;

import org.rdr.radarbox.Device.DeviceConfiguration;
import org.rdr.radarbox.R;

import java.util.Arrays;
import java.util.Objects;

import static org.rdr.radarbox.RadarBox.logger;

import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * Класс для работы с частотными сигналами. Внутри него можно получать актульную информацию об
 * актульаных сырых частотных данных {@link #getRawFreqFrame()},
 * о векторе частот в мегагерцах {@link #getFrequenciesMHz()} и т.д.
 * @author Сапронов Данил Игоревич
 * @version 0.1
 */
public class FreqSignals extends PreferenceFragmentCompat {
    private int FN, rxN, txN, chN, frameSize, freqInitMHz, freqStepMHz;
    private short[] rawFreqFrame;
    private short[] rawFreqFrameReshuffled;
    private int[] frequenciesMHz;
    private int[][] rxtxOrder;

    public FreqSignals() {

    }

    EditTextPreference pref;
    Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String stringValue = newValue.toString();
            if (preference instanceof EditTextPreference) {
                preference.setSummary(stringValue);
                ((EditTextPreference) preference).setText(stringValue);
                setNewParameters();
            }
            return false;
        }
    };

    void setNewParameters(){
        pref = findPreference("freqInitMHz");
        assert pref != null;
        if (Objects.equals(pref.getText(), "Default value"))
            pref.setText(Integer.toString(freqInitMHz));
        freqInitMHz = Integer.parseInt(pref.getText());

        pref = findPreference("freqStepMHz");
        assert pref != null;
        if (Objects.equals(pref.getText(), "Default value"))
            pref.setText(Integer.toString(freqStepMHz));
        freqStepMHz = Integer.parseInt(pref.getText());

        pref = findPreference("FN");
        assert pref != null;
        if (Objects.equals(pref.getText(), "Default value"))
            pref.setText(Integer.toString(FN));
        FN = Integer.parseInt(pref.getText());
    }

    void bindSummaryValue(Preference preference){
        preference.setOnPreferenceChangeListener(listener);
        listener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(),""));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_dsp_freqsignals,rootKey);

        pref = findPreference("freqInitMHz");
        assert pref != null;
        pref.setSummary(pref.getText());
        pref.setDefaultValue(Integer.toString(freqInitMHz));
        bindSummaryValue(pref);

        pref = findPreference("freqStepMHz");
        assert pref != null;
        pref.setSummary(pref.getText());
        pref.setDefaultValue(Integer.toString(freqStepMHz));
        bindSummaryValue(pref);

        pref = findPreference("FN");
        assert pref != null;
        pref.setSummary(pref.getText());
        pref.setDefaultValue(Integer.toString(FN));
        bindSummaryValue(pref);
    }

    /** Функция, изменяющая параметры частотных сигналов. Вызывается при смене устройства
     * либо при переходе между работой с устройством и работой с файлом.
     * @param deviceConfig новая конфигурация устройства /
     *                     виртуального устройства (из заголовока файла)
     * @return true, если параметры частотных сигналов удачно считаны из переданной конфигурации
     * и установлены
     */
    public boolean updateSignalParameters(DeviceConfiguration deviceConfig) {

        rxN=deviceConfig.getRxN();
        txN=deviceConfig.getTxN();
        rxtxOrder = new int[rxN][txN];
        deviceConfig.getLiveRxtxOrder().observeForever(rxtxOrder1D->{
            chN = 0;
            for (int r = 0; r < rxN; r++) {
                for (int t = 0; t < txN; t++) {
                    rxtxOrder[r][t] = rxtxOrder1D[t*rxN+r];
                    if (rxtxOrder[r][t] != 0) chN++;
                }
            }
            frameSize=2*FN*chN;
            rawFreqFrame= new short[2*FN*chN];
            rawFreqFrameReshuffled = new short[2*FN*chN];
        });
        DeviceConfiguration.Parameter param = deviceConfig.getParameters().stream().filter(
                parameter -> parameter.getID().equals("FN")
        ).findAny().orElse(null);
        if(param!=null) ((DeviceConfiguration.IntegerParameter) param)
                .getLiveValue().observeForever(value-> {
                    FN=value;
                    frameSize=2*FN*chN;
                    rawFreqFrame= new short[2*FN*chN];
                    rawFreqFrameReshuffled = new short[2*FN*chN];
                    frequenciesMHz = new int[FN];
                    for(int i=0; i<FN; i++) frequenciesMHz[i] = freqInitMHz+i*freqStepMHz;
                });
        else {
            FN=0;
            logger.add(this,"No FN parameter in parameter list");
            return false;
        }
        param = deviceConfig.getParameters().stream().filter(
                parameter -> parameter.getID().equals("F0")
        ).findAny().orElse(null);
        if(param!=null) ((DeviceConfiguration.IntegerParameter) param)
                .getLiveValue().observeForever(value->{
                    freqInitMHz=value;
                    for(int i=0; i<FN; i++) frequenciesMHz[i] = freqInitMHz+i*freqStepMHz;
                });
        else {
            freqInitMHz=0;
            logger.add("Signals","No F0 parameter in parameter list");
            return false;
        }

        param = deviceConfig.getParameters().stream().filter(
                parameter -> parameter.getID().equals("dF")
        ).findAny().orElse(null);
        if(param!=null)((DeviceConfiguration.IntegerParameter) param)
                .getLiveValue().observeForever(value->{
                    freqStepMHz=value;
                    for(int i=0; i<FN; i++) frequenciesMHz[i] = freqInitMHz+i*freqStepMHz;
                });
        else {
            freqStepMHz=0;
            logger.add("Signals","No dF parameter in parameter list");
            return false;
        }
        return true;
    }

    /**
     * <p>Функция для обработки полученных "сырых" данных радара.</p>
     * Дальнейшее использование данных осуществляется через функции-геттеры данного класса.
     * Например {@link FreqSignals#getRawFreqOneChannelSignal(int,short[])}
     */
    public void doSignalProcessing() {
        //reshuffleRawFrame(); // больше не нужно, потому что выполняется в RawDataAdapter в классе Device
    }

    /** <p>Массив сырых частотных данных (кадр)</p>
     *  <p>Размер массива определяется количеством работающих каналов и количеством частотных точек</p>
     *  <p>2*{@link #getChN()}*{@link #getFN()}</p>
     * @return Количество отчётов типа short (размер массива {@link #getRawFreqFrame()}
     */
    public final short[] getRawFreqFrame() {
        return rawFreqFrame;
    }

    /** Метод перемешивает сырые данные, так, чтобы они были объединены по каналам
     * так как данные в сыром сигнале перемешаны (на одной частоте данные с нескольких
     * приёмников), то, чтобы правильно вычленить частотные данные для одного канала,
     * используется метод вычисляющий индексы отсчётов и происходит правильное перемешивание.
     */
    private void reshuffleRawFrame() {
        int notNullChannelCounter = 0;
        short[] oneChannelSignalReshuffled = new short[2*FN];
        for(int tx=0; tx<txN; tx++)
            for(int rx=0; rx<txN; rx++)
                if(rxtxOrder[rx][tx]!=0)
                    if(reshuffleOneChannelSignal(rx,tx,oneChannelSignalReshuffled)) {
                        System.arraycopy(oneChannelSignalReshuffled, 0,
                                rawFreqFrameReshuffled, notNullChannelCounter * 2 * FN, 2 * FN);
                        notNullChannelCounter++;
                    }
    }

    public boolean reshuffleOneChannelSignal(int rx, int tx, short[] oneChannelArray) {
        if (rx<0 || rx> rxN-1 || tx<0 || tx> txN-1 || oneChannelArray.length!=2*FN)
            return false;
        /* так как данные в сыром сигнале перемешаны (на одной частоте данные с нескольких
        приёмников), то, чтобы правильно вычленить частотные данные для одного канала,
        используется метод вычисляющий индексы отсчётов и происходит правильное перемешивание
        */
        // Вычисляется сумма всех ненулевых приёмопередающих каналов, предшествующих текущему
        int SumRt = 0;
        for(int t=0; t<tx; t++) {
            for (int r = 0; r < rxN; r++)
                if (rxtxOrder[r][t] != 0)
                    SumRt++;
        }
        // Вычисляется количество ненулевых приёмных каналов для данного передатчика
        int Rt = 0;
        for(int r=0; r<rxN; r++) if(rxtxOrder[r][tx]!=0) Rt++;
        // Вычислеяется каким по счёту ненулевым приёмным каналом является текущий канал для текущего передатчика
        int rt = 0;
        for(int r=0; r<rx; r++) if(rxtxOrder[r][tx]!=0) rt++;
        // С учётом полученных данных выбираются отсчёты соотвутствующие искомому каналу
        for(int f=0; f<FN; f++) {
            oneChannelArray[2*f] = rawFreqFrame[2*FN*SumRt+2*f*Rt+2*rt];
            oneChannelArray[2*f+1] = rawFreqFrame[2*FN*SumRt+2*f*Rt+2*rt+1];
        }
        return true;
    }

    /** <p>Размер кадра в отсчётах типа short.</p>
     *  <p>FrameSize=2*{@link #getChN()}*{@link #getFN()}</p>
     * @return Количество отчётов типа short (размер массива {@link #getRawFreqFrame()}
     */
    public int getFrameSize() {
        return frameSize;
    }

    /**
     * <p>Возвращает вектор частот в МГц</p>
     * @return массив размера {@link #FN}
     */
    public int[] getFrequenciesMHz() {
        return frequenciesMHz;
    }


    /** <p>Возвращает количество "ненулевых" (не выключенных) каналов приёма-передачи.</p>
     * @return число от 0 до {@link #rxN}*{@link #txN}
     */
    public int getChN() {
        return chN;
    }

    /** <p>Возвращает количество частотных отсчётов.</p>
     * @return количество частотных отсчётов
     */
    public int getFN() {
        return FN;
    }

    /** <p>Возвращает начальную частоту в МГц.</p>
     * @return начальная частота в МГц
     */
    public int getFreqInitMHz() {
        return freqInitMHz;
    }

    /** <p>Возвращает шаг частоты в МГц.</p>
     * @return шаг частоты в МГц
     */
    public int getFreqStepMHz() {
        return freqStepMHz;
    }

    /** <p>Возвращает общее количество приёмных каналов.</p>
     * @return количество приёмных каналов
     */
    public int getRxN() {
        return rxN;
    }

    /** <p>Возвращает общее количество передающих каналов.</p>
     * @return количество передающих каналов
     */
    public int getTxN() {
        return txN;
    }

    /**
     * <p>Возвращает сигнал сырых частотных данных (до калибровки)</p>
     * <p>Для заданного канала при нумерации от 0 до {@link #txN}*{@link #rxN}-1;</p>
     * @param channel номер канала
     * @param oneChannelArray массив размера {@link #FN}*2
     * @return -1, если номер канала задан неверно или передан массив, не соответствующий размеру.
     * 0, если запрашиваемый канал отключен. массив сигнала тогда заполняется нулями
     * 1, если массив сырых частотных данных успешно сформирован
     */
    public int getRawFreqOneChannelSignal(int channel, short[] oneChannelArray) {
        return getRawFreqOneChannelSignal(channel%rxN,channel/rxN, oneChannelArray);
    }

    /**
     * <p>Возвращает сигнал сырых частотных данных (до калибровки)</p>
     * <p>Для заданных передатчика и приёмника (нумерация с нуля);</p>
     * @param rx номер приёмника, начиная с нуля
     * @param tx номер передатчика, начиная с нуля
     * @return -1, если номер канала задан неверно или передан массив, не соответствующий размеру.
     * 0, если запрашиваемый канал отключен. Массив сигнала тогда заполняется нулями
     * 1, если массив сырых частотных данных успешно сформирован
     */
    public int getRawFreqOneChannelSignal(int rx, int tx, short[] oneChannelArray) {
        if (rx<0 || rx> rxN-1 || tx<0 || tx> txN-1 || oneChannelArray.length!=2*FN)
            return -1;

        if (txN==1 && rxN==1) {
            System.arraycopy(rawFreqFrame,0,
                    oneChannelArray,0,2*FN);
            return 1;
        }

        if(rxtxOrder[rx][tx]==0) {
            for(int i=0; i<2*FN; i++) oneChannelArray[i]=0;
            return 0;
        }

        int notNullRxTxChannels = 0;
        for(int t=0; t<tx; t++) for(int r=0; r<rxN; r++) if(rxtxOrder[r][t]!=0) notNullRxTxChannels++;
        for(int r=0; r<rx; r++) if(rxtxOrder[r][tx]!=0) notNullRxTxChannels++;
        System.arraycopy(rawFreqFrame,notNullRxTxChannels*2*FN,
                oneChannelArray,0,2*FN);
        return 1;
    }

}
