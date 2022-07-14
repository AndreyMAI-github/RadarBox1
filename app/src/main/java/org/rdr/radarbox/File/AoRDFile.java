package org.rdr.radarbox.File;

import android.content.Context;

import org.rdr.radarbox.RadarBox;
import org.rdr.radarbox.Device.DeviceConfiguration;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.NotDirectoryException;
import java.nio.InvalidMarkException;
import java.nio.BufferUnderflowException;

import java.util.Arrays;
import java.util.Scanner;
import java.util.ArrayList;

import androidx.annotation.NonNull;

public class AoRDFile extends File {
    private Context aordFileContext = RadarBox.getAppContext();

    private File unzipFolder = null;
    private ZipManager zipManager = null;

    public DataFileManager data = null;
    public ConfigurationFileManager config = null;
    public StatusFileManager status = null;
    public DescriptionFileManager description = null;
    public AdditionalFileManager additional = null;

    private boolean enabled = true;

    // Initialize methods
    public AoRDFile(@NonNull File file) {
        super(file.getAbsolutePath());
        try {
            Helpers.checkFileExistence(file);

            zipManager = new ZipManager(this);
            zipManager.unzipFile();

            unzipFolder = zipManager.getUnzipFolder();
            checkAoRDFileFolder(unzipFolder);

            read();
        } catch (IOException e) {
            RadarBox.logger.add(this, "ERROR: " + e.getLocalizedMessage());
            e.printStackTrace();
            close();
        }
    }

    public AoRDFile(@NonNull File file, @NonNull File folder) {
        super(file.getAbsolutePath());
        try {
            Helpers.checkFileExistence(file);
            Helpers.checkFileExistence(folder);
            checkAoRDFileFolder(folder);

            unzipFolder = folder;
            zipManager = new ZipManager(this);

            read();
        } catch (IOException e) {
            RadarBox.logger.add(this, "ERROR: " + e.getLocalizedMessage());
            e.printStackTrace();
            close();
        }
    }

    public AoRDFile(String filePath) {
        super(filePath);
        try {
            Helpers.checkFileExistence(this);

            zipManager = new ZipManager(this);
            zipManager.unzipFile();

            unzipFolder = zipManager.getUnzipFolder();
            Helpers.checkFileExistence(unzipFolder);
            checkAoRDFileFolder(unzipFolder);

            read();
        } catch (IOException e) {
            RadarBox.logger.add(this, "ERROR: " + e.getLocalizedMessage());
            e.printStackTrace();
            close();
        }
    }

    public AoRDFile(String filePath, String folderPath) {
        super(filePath);
        try {
            Helpers.checkFileExistence(this);

            unzipFolder = new File(folderPath);
            Helpers.checkFileExistence(unzipFolder);
            checkAoRDFileFolder(unzipFolder);

            zipManager = new ZipManager(this);

            read();
        } catch (IOException e) {
            RadarBox.logger.add(this, "ERROR: " + e.getLocalizedMessage());
            e.printStackTrace();
            close();
        }
    }

    /**
     * Проверка папки, в которую распакован AoRD-файл, на корректность содержимого.
     * @param aordFileUnzipFolder - объект {@link File} директории.
     * @throws NotDirectoryException - если передана не директория.
     * @throws WrongFileFormatException - если в формате AoRD допущена ошибка.
     */
    public static void checkAoRDFileFolder(File aordFileUnzipFolder)
            throws NotDirectoryException, WrongFileFormatException {
        if (!aordFileUnzipFolder.isDirectory()) {
            throw new NotDirectoryException("Файл " + aordFileUnzipFolder.getAbsolutePath() +
                    " не является директорией");
        }
        for (String fileName : new String[] {Const.CONFIG_FILE_NAME, Const.DATA_FILE_NAME}) {
            if (!new File(aordFileUnzipFolder.getAbsolutePath() + "/" +
                    fileName).exists()) {
                throw new WrongFileFormatException(
                        "Некорректный формат AoRD-файла: не хватает файла (папки) " + fileName);
            }
        }
    }

    // Get methods
    public boolean isEnabled() {
        return enabled;
    }

    public Context getContext() {
        return aordFileContext;
    }

    public File getUnzipFolder() {
        return unzipFolder;
    }

    // Main methods
    private void read() throws IOException {
        data = new DataFileManager();
        config = new ConfigurationFileManager();
        status = new StatusFileManager();
        description = new DescriptionFileManager();
        additional = new AdditionalFileManager();
    }

    public static AoRDFile createNewAoRDFile(String path) {
        File folderWrite = Helpers.createUniqueFile(path);
        try {
            if (!folderWrite.mkdir()) {
                throw new IOException("Can`t create main write folder");
            }
            for (String name : new String[] {Const.DATA_FILE_NAME, Const.CONFIG_FILE_NAME,
                    Const.STATUS_FILE_NAME, Const.DESC_FILE_NAME}) {
                File file = new File(folderWrite.getAbsolutePath() + "/" + name);
                if (!file.createNewFile()) {
                    throw new IOException("Can`t create file " + name);
                }
            }
            File file = new File(folderWrite.getAbsolutePath() + "/" +
                    Const.ADDITIONAL_FOLDER_NAME);
            if (!file.mkdir()) {
                throw new IOException("Can`t create folder " + Const.ADDITIONAL_FOLDER_NAME);
            }
            try {
                File zipFile = ZipManager.archiveFolder(folderWrite);
                AoRDFile result = new AoRDFile(zipFile, folderWrite);
                if (result.isEnabled()) {
                    result.config.write(RadarBox.device.configuration);
                    RadarBox.logger.add("Successful creation of file " + result.getAbsolutePath());
                    return result;
                }
            } catch (IOException ie) {
                RadarBox.logger.add("ERROR on creation zip");
            }
        } catch (Exception e) {
            RadarBox.logger.add("ERROR: AoRDFile.createNewAoRDFile error: " +
                    e.getLocalizedMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void commit() {
        data.endWriting();
        if (!delete()) {
            RadarBox.logger.add(this, "ERROR: Can`t commit file " + getAbsolutePath());
            return;
        }
        try {
            additional.prepareToCommit();
            File newSelf = ZipManager.archiveFolder(unzipFolder);
            if (!newSelf.getAbsolutePath().equals(getAbsolutePath())) {
                close();
                throw new IOException("Error on commit file: incorrect archive name");
            }
            additional.commit();
        } catch (IOException e) {
            RadarBox.logger.add(this, e.toString());
            e.printStackTrace();
            close();
        }
        RadarBox.logger.add(this, "INFO: Commit on file " + getName() + " is successful");
    }

    public void close() {
        Helpers.removeTreeIfExists(unzipFolder);
        enabled = false;
    }

    // Classes for inner files
    protected class BaseInnerFileManager {
        protected File selfFile = null;

        protected BaseInnerFileManager(String name, boolean required) throws IOException {
            selfFile = new File(unzipFolder.getAbsolutePath() + "/" + name);
            if (!required && !selfFile.exists()) {
                if (!selfFile.createNewFile()) {
                    throw new IOException("Can`t create file " + selfFile.getAbsolutePath());
                }
            } else if (required && !selfFile.exists()) {
                throw new FileNotFoundException("Required file in " +
                        AoRDFile.this.getAbsolutePath() + " not found");
            }
        }

        public File getFile() {
            return selfFile;
        }

        public String getFileName() {
            return selfFile.getName();
        }

        public String getFilePath() {
            return selfFile.getAbsolutePath();
        }

        public void setFile(File file) throws IOException {
            if (!selfFile.delete() || !Helpers.copyFile(file, selfFile)) {
                throw new IOException("Can`t set file " + file.getAbsolutePath());
            }
        }
    }

    public class DataFileManager extends BaseInnerFileManager {
        private MappedByteBuffer fileReadBuffer;
        private ShortBuffer fileReadShortBuffer;
        private int curReadFrame;
        private FileOutputStream dataWriteStream = null;

        DataFileManager() throws IOException {
            super(Const.DATA_FILE_NAME, true);
            readSelf();
        }

        protected void readSelf() throws IOException {
            FileInputStream dataReadStream = new FileInputStream(selfFile);
            fileReadBuffer = dataReadStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,0, selfFile.length());
            fileReadBuffer.mark(); //отметка, с которой можно начать читать данные типа short

            // создание буфера типа short для удобного считывания данных
            fileReadShortBuffer = fileReadBuffer.order(ByteOrder.BIG_ENDIAN).asShortBuffer();

            // установка отметки в нулевую позицию для возможности в будущем перечитывать данные
            fileReadShortBuffer.mark();
            curReadFrame = 0;
            dataReadStream.close();
        }

        public void getNextFrame(short[] dest) {
            try {
                fileReadShortBuffer.get(dest,0, dest.length);
                curReadFrame++;
            }
            catch (BufferUnderflowException e) {
                RadarBox.logger.add(this,e.toString() + "\n\tCouldn't read " + curReadFrame
                        + "-th frame. \n\tFileBuffer position: " + fileReadBuffer.position()
                        + "\n\tReading elements count: " + dest.length + "(int16)"
                        + "\n\tElements remaining in buffer: " + fileReadShortBuffer.remaining()
                        + "(int16)");
                try {
                    fileReadShortBuffer.reset();
                    curReadFrame=0;

                } catch (InvalidMarkException e2) {
                    RadarBox.logger.add(this,e2.getLocalizedMessage() +
                            "\n\tCouldn't reload file");
                    // RadarBox.mainDataThread.extraStop();
                }
            }
        }

        public void startWriting() {
            try {
                dataWriteStream = new FileOutputStream(selfFile);
            } catch (IOException e) {
                RadarBox.logger.add(this, e.toString());
                e.printStackTrace();
                endWriting();
            }

        }

        public void write(short[] data) {
            if (dataWriteStream != null && AoRDSettingsManager.needSaveData) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(2 * data.length);
                byteBuffer.asShortBuffer().put(data);
                try {
                    dataWriteStream.write(byteBuffer.array());
                    dataWriteStream.flush();
                } catch (IOException e) {
                    RadarBox.logger.add(this, e.toString());
                    e.printStackTrace();
                    endWriting();
                }
            }
        }

        public void endWriting() {
            if (dataWriteStream == null) {
                return;
            }
            try {
                dataWriteStream.close();
                readSelf();
            } catch (IOException e) {
                RadarBox.logger.add(this, e.toString());
                e.printStackTrace();
                close();
            }
            dataWriteStream = null;
        }
    }

    public class ConfigurationFileManager extends BaseInnerFileManager {
        private VirtualDeviceConfiguration virtualDeviceConfiguration = null;

        ConfigurationFileManager() throws IOException {
            super(Const.CONFIG_FILE_NAME, true);
            readSelf();
        }

        protected void readSelf() throws IOException {
            try {
                FileInputStream configReadStream = new FileInputStream(selfFile);
                virtualDeviceConfiguration = new VirtualDeviceConfiguration(aordFileContext,
                        "virtual", configReadStream);
                configReadStream.close();
            } catch (IllegalStateException e) {
                RadarBox.logger.add(this,
                        "WARNING: Reading null configuration " +
                                "(if it`s on creation of AoRD-file, it is normal)");
            }
        }

        /** Возвращает конфигурацию устройства, считанную из файла, либо null.
         * @return null, если файл с конфигурацией не открыт.
         */
        public DeviceConfiguration getVirtual() {
            return virtualDeviceConfiguration;
        }

        public void write(DeviceConfiguration configuration) {
            try {
                if(RadarBox.device == null)
                    throw new IOException("No device");
                if (!selfFile.delete()) {
                    throw new IOException("Can`t write configuration to file");
                }
                XmlSerializer serializer = Xml.newSerializer();
                FileOutputStream fileWriteStream = new FileOutputStream(selfFile);
                serializer.setOutput(fileWriteStream, "UTF-8");
                serializer.startDocument(null, Boolean.TRUE);
                serializer.setFeature(
                        "http://xmlpull.org/v1/doc/features.html#indent-output", true);
                serializer.startTag(null,"config");
                DeviceConfiguration.writeDeviceConfiguration(serializer, configuration);
                ArrayList<DeviceConfiguration.Parameter> parameters = configuration.getParameters();
                for (DeviceConfiguration.Parameter param: parameters) {
                    if(param.getValue().getClass().equals(Boolean.class))
                        DeviceConfiguration.writeBooleanParameter(serializer,
                                (DeviceConfiguration.BooleanParameter) param);
                    else if(param.getValue().getClass().equals(Integer.class))
                        DeviceConfiguration.writeIntegerParameter(serializer,
                                (DeviceConfiguration.IntegerParameter) param);
                }
                serializer.endTag(null,"config");
                serializer.endDocument();
                serializer.flush();
                fileWriteStream.close();
                readSelf();
            } catch (IOException e) {
                RadarBox.logger.add(this, e.toString());
                e.printStackTrace();
                close();
            }
        }

        /** Класс-наследник {@link #DeviceConfiguration} для считывания конфигурации устройства
         * из конфигурационного файла */
        private class VirtualDeviceConfiguration extends DeviceConfiguration {

            public VirtualDeviceConfiguration(Context context, String devicePrefix,
                                              InputStream configFileStream) {
                super(context, devicePrefix);

                try {
                    parseConfigurationHeader(configFileStream);
                } catch (XmlPullParserException | IOException e) {
                    RadarBox.logger.add(deviceName + " CONFIG",
                            "ERROR on parseConfiguration: " + e.getLocalizedMessage());
                }
            }

            private void parseConfigurationHeader(InputStream inputStream)
                    throws IOException, XmlPullParserException {
                super.parseConfiguration(inputStream);
            }

            protected void readDeviceConfiguration(XmlPullParser parser) {
                super.readDeviceConfiguration(parser);
            }

            protected BooleanParameter readBooleanParameter(XmlPullParser parser) {
                BooleanParameter parameter = super.readBooleanParameter(parser);
                parameter.setValue(Boolean.parseBoolean(parser.getAttributeValue(
                        null,"value")));
                return parameter;
            }

            protected IntegerParameter readIntegerParameter(XmlPullParser parser) {
                IntegerParameter parameter = super.readIntegerParameter(parser);
                parameter.setValue(Integer.parseInt(
                        (parser.getAttributeValue(null,"value")),
                        parameter.getRadix()));
                return parameter;
            }
        }
    }

    public class StatusFileManager extends BaseInnerFileManager {
        StatusFileManager() throws IOException {
            super(Const.STATUS_FILE_NAME, false);
        }
    }

    public class DescriptionFileManager extends BaseInnerFileManager {
        private String description = "";

        DescriptionFileManager() throws IOException {
            super(Const.DESC_FILE_NAME, false);
            readSelf();
        }

        /**
         * Чтение текстового файла.
         * @throws IOException - при ошибке системы ввода/вывода.
         */
        protected void readSelf() throws IOException {
            FileReader fileReader = new FileReader(selfFile);
            Scanner reader = new Scanner(fileReader);
            String result = "";
            while (reader.hasNextLine()) {
                result += reader.nextLine() + "\n";
            }
            reader.close();
            fileReader.close();
            if (result.length() >= 1) {
                result = result.substring(0, result.length() - 1);
            }
            description = result;
        }

        /**
         * Возвращает текстовое описание AoRD-файла.
         * @return null, если файл не открыт.
         */
        public String getText() {
            return description;
        }

        /**
         * Запись в текстовый файл.
         * @param text - строка, которую нужно записать.
         */
        public void write(String text) {
            try {
                FileWriter writer = new FileWriter(selfFile, false);
                writer.write(text);
                writer.flush();
                description = text;
                RadarBox.logger.add(this, "DEBUG: Description written: " + description);
            } catch (IOException e) {
                RadarBox.logger.add(this, e.toString());
                e.printStackTrace();
            }
        }
    }

    public class AdditionalFileManager extends BaseInnerFileManager {
        private File selfFolder = null;
        
        AdditionalFileManager() throws IOException {
            super(Const.ADDITIONAL_ARCH_NAME, false);
            selfFolder = new File(unzipFolder.getAbsolutePath() + "/" +
                    Const.ADDITIONAL_FOLDER_NAME);
            if (!selfFolder.exists() && !selfFolder.mkdir()) {
                throw new IOException("Can`t create dir " + selfFolder.getAbsolutePath());
            }
        }

        public String[] getNamesList() {
            return selfFolder.list();
        }

        public File[] getFilesList() {
            return selfFolder.listFiles();
        }

        public void addFile(File newFile) {
            RadarBox.logger.add(this, "DEBUG: Adding file " + newFile.getAbsolutePath() +
                    newFile.exists());
            if (!Helpers.copyFile(newFile, Helpers.createUniqueFile(
                    selfFolder.getAbsolutePath() + "/" + newFile.getName()))) {
                RadarBox.logger.add(this, "ERROR: Can`t add file " +
                        newFile.getAbsolutePath() + " to additional folder of AoRD-file " +
                        selfFolder.getAbsolutePath());
            }
        }

        public void deleteFile(String name) {
            if (Arrays.asList(getNamesList()).contains(name)) {
                File fileToDelete = new File(selfFolder.getAbsolutePath() + "/" + name);
                if (!fileToDelete.delete()) {
                    RadarBox.logger.add(this, "ERROR: Can`t delete file " +
                            fileToDelete.getAbsolutePath() +
                            " from additional folder of AoRD-file " +
                            AoRDFile.this.getAbsolutePath());
                }
            }
        }
        
        protected void prepareToCommit() throws IOException {
            if (selfFile.exists()) {
                if (!selfFile.delete()) {
                    throw new IOException("Can`t delete file " + selfFile.getAbsolutePath());
                }
            }
        }
        
        public void commit() {
            try {
                prepareToCommit();
                selfFile = ZipManager.archiveFolder(selfFolder);
            } catch (IOException e) {
                RadarBox.logger.add(this, e.toString());
                e.printStackTrace();
                close();
            }
        }
    }

    // Help classes
    private static class Const {
        public static final String CONFIG_FILE_NAME = "config.xml";
        public static final String DATA_FILE_NAME = "radar_data.data";
        public static final String STATUS_FILE_NAME = "status.csv";
        public static final String DESC_FILE_NAME = "description.txt";
        public static final String ADDITIONAL_FOLDER_NAME = "additional";
        public static final String ADDITIONAL_ARCH_NAME = ADDITIONAL_FOLDER_NAME + ".zip";

    }
}
