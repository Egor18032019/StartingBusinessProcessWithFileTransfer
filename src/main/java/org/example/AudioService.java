package org.example;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioService {
    private static final int DEFAULT_MAX_RECORDING_TIME_MS = 5_000; // 30 секунд
    private static final int BUFFER_SIZE = 4096;

    private String AUDIO_FOLDER;
    private SimpleDateFormat DATE_FORMAT;
    private int MAX_RECORDING_TIME; // в миллисекундах

    public AudioService() {
        this("recordings", new SimpleDateFormat("yyyyMMdd_HHmmss"), DEFAULT_MAX_RECORDING_TIME_MS);
    }

    public AudioService(String audioFolder, SimpleDateFormat dateFormat, int maxRecordingTimeMs) {
        this.AUDIO_FOLDER = audioFolder;
        this.DATE_FORMAT = dateFormat;
        this.MAX_RECORDING_TIME = maxRecordingTimeMs;
        createAudioFolder();
    }

    /**
     * Записывает аудио с микрофона до нажатия Enter или до достижения лимита времени.
     *
     * @return файл записи (WAV) или null, если запись пустая
     * @throws LineUnavailableException если микрофон недоступен
     * @throws IOException              при ошибках ввода-вывода
     */
    public File recordAudioWithStop() throws LineUnavailableException, IOException {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new RuntimeException("Микрофон не поддерживается");
        }

        TargetDataLine line = null;
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            long recordingStart = System.currentTimeMillis();
            long lastProgressUpdate = System.currentTimeMillis();

            System.out.println("🔴 ЗАПИСЬ... Нажмите Enter для остановки");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            AtomicBoolean isRecording = new AtomicBoolean(true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            Thread stopThread = new Thread(() -> {
                try {
                    while (isRecording.get()) {
                        if (reader.ready()) {
                            reader.read(); // Enter
                            isRecording.set(false);
                            System.out.println("\n⏹️  Остановка записи...");
                            break;
                        }
                        Thread.sleep(100); //   CPU ?
                    }
                } catch (Exception  ignored) {
                    ignored.printStackTrace();
                }
            });
            stopThread.setDaemon(true);
            stopThread.start();

            int totalBytes = 0;

            try {
                while (isRecording.get()) {
                    long now = System.currentTimeMillis();
                    long totalElapsed = now - recordingStart;

                    // Проверка на превышение максимального времени
                    if (totalElapsed >= MAX_RECORDING_TIME) {
                        isRecording.set(false);

                    }

                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        out.write(buffer, 0, count);
                        totalBytes += count;
                    }

                    // Обновление прогресса раз в ~1 секунду
                    if (now - lastProgressUpdate > 1000) {
                        int currentSeconds = (int) (totalElapsed / 1000);
                        int maxSeconds = MAX_RECORDING_TIME / 1000;
                        int progressPercent = Math.min(100, (currentSeconds * 100) / Math.max(1, maxSeconds));
                        updateProgress(progressPercent, currentSeconds, totalBytes);
                        lastProgressUpdate = now;
                    }

                }

                // Финальное обновление прогресса
                int finalSeconds = (int) ((System.currentTimeMillis() - recordingStart) / 1000);
                int maxSeconds = MAX_RECORDING_TIME / 1000;
                int finalProgress = Math.min(100, (finalSeconds * 100) / Math.max(1, maxSeconds));
                updateProgress(finalProgress, finalSeconds, totalBytes);

                System.out.println(); // новая строка после прогресс-бара

            } finally {
                if (line != null) {
                    line.stop();
                    line.close();
                }
                clearSystemInBuffer();
            }

            if (totalBytes == 0) {
                System.out.println("Запись пуста");
                return null;
            }

            // Сохранение файла
            String timestamp = DATE_FORMAT.format(new Date());
            String fileName = String.format("audio_%s.wav", timestamp);
            Path filePath = Paths.get(AUDIO_FOLDER, fileName);

            try (AudioInputStream audioStream = new AudioInputStream(
                    new ByteArrayInputStream(out.toByteArray()), format, out.size() / format.getFrameSize())) {
                AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, filePath.toFile());
            }

            File audioFile = filePath.toFile();

            // Расчёт длительности через frame rate (более надёжно)
            long frameLength = out.size() / format.getFrameSize();
            double duration = (double) frameLength / format.getFrameRate(); // format.getFrameRate() == sampleRate для PCM

            System.out.println("✅ Запись сохранена: " + fileName);
            System.out.printf("📊 Длительность: %.1f секунд\n", duration);
            System.out.printf("💾 Размер файла: %.1f KB\n", audioFile.length() / 1024.0);
            System.out.println("📁 Путь: " + audioFile.getAbsolutePath());
            return audioFile;

        } catch (Exception e) {
            if (line != null) {
                line.close();
            }
            throw e;
        }
    }

    /**
     * Поиск последнего аудиофайла в папке (только .wav)
     *
     * @return последний .wav файл или null
     */
    public File findLatestAudioFile() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }

        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".wav")); // MP3 не создаются — убрано
        if (files == null || files.length == 0) {
            return null;
        }

        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        return latest;
    }

    private void updateProgress(int progressPercent, long seconds, int bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            sb.append(i < progressPercent ? "█" : "░");
        }
        sb.append("] ")
                .append(progressPercent).append("% | ")
                .append(seconds).append("с | ")
                .append(bytes / 1024).append("KB");
        System.out.print("\r" + sb);
    }

    private void createAudioFolder() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("❌ Не удалось создать папку для записей: " + AUDIO_FOLDER);
        }
    }

    public String getFolder() {
        return AUDIO_FOLDER;
    }

    /**
     * Очищает буфер System.in от оставшихся символов
     */
    private void clearSystemInBuffer() {
        try {
            while (System.in.available() > 0) {
                System.in.read();
            }
        } catch (IOException e) {
            // Игнорируем ошибки при очистке буфера
        }
    }
}