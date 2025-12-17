package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioService {
    private String AUDIO_FOLDER = "recordings";
    private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private int MAX_RECORDING_TIME = 30000;

    public AudioService() {
        createAudioFolder();
    }

    public AudioService(String AUDIO_FOLDER, SimpleDateFormat DATE_FORMAT,int MAX_RECORDING_TIME) {
        this.AUDIO_FOLDER = AUDIO_FOLDER;
        this.DATE_FORMAT = DATE_FORMAT;
        this.MAX_RECORDING_TIME = MAX_RECORDING_TIME;
        createAudioFolder();
    }

    /**
     * Запись аудио
     *
     * @return File аудиофайл
     */
    public File recordAudioWithStop() throws LineUnavailableException, IOException {

        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new RuntimeException("Микрофон не поддерживается");
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        long recordingStart = System.currentTimeMillis();
        long lastProgressUpdate = System.currentTimeMillis();

        System.out.println(recordingStart + "startTime");
        System.out.println("🔴 ЗАПИСЬ... Нажмите Enter для остановки");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        AtomicBoolean isRecording = new AtomicBoolean(true);

        Thread stopThread = new Thread(() -> {
            try {
                System.in.read();
                isRecording.set(false);
                System.out.println("\n⏹️  Остановка записи...");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stopThread.setDaemon(true);
        stopThread.start();

        int totalBytes = 0;

        int progressBars = 0;

        try {
            while (isRecording.get()) {
                long now = System.currentTimeMillis();
                long totalElapsed = now - recordingStart; // общее время записи
                long timeSinceLastUpdate = now - lastProgressUpdate; // с последнего обновления
                if (totalElapsed > MAX_RECORDING_TIME) {
                    isRecording.set(false);
                }

                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                    totalBytes += count;
                }
                // Обновление прогресса раз в ~1 секунду
                if (timeSinceLastUpdate > 1000) {
                    int seconds = (int) (totalElapsed / 1000);
                    if (seconds > progressBars && seconds <= 10) {
                        progressBars = seconds;
                        updateProgress(progressBars, seconds, totalBytes);
                    }
                    lastProgressUpdate = now;
                }
            }
        } finally {
            line.stop();
            line.close();
            stopThread.interrupt();
        }

        if (totalBytes == 0) {
            System.out.println("Запись пуста");
            return null;
        }

        String timestamp = DATE_FORMAT.format(new Date());
        String fileName = String.format("audio_%s.wav", timestamp);
        Path filePath = Paths.get(AUDIO_FOLDER, fileName);

        try (AudioInputStream audioStream = new AudioInputStream(
                new ByteArrayInputStream(out.toByteArray()), format, out.size() / format.getFrameSize())) {
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, filePath.toFile());
        }

        File audioFile = filePath.toFile();
        double duration = (double) totalBytes / (format.getSampleRate() * format.getSampleSizeInBits() / 8);

        System.out.println("\n✅ Запись сохранена: " + fileName);
        System.out.printf("📊 Длительность: %.1f секунд\n", duration);
        System.out.printf("💾 Размер файла: %.1f KB\n", audioFile.length() / 1024.0);
        System.out.println("📁 Путь: " + audioFile.getAbsolutePath());
        return audioFile;
    }

    /**
     * Поиск последнего аудиофайла
     *
     * @return File аудиофайл
     */
    public File findLatestAudioFile() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists() || !folder.isDirectory()) return null;

        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".mp3"));
        if (files == null || files.length == 0) return null;

        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }
        return latest;
    }

    private void updateProgress(int bars, long seconds, int bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            sb.append(i < bars ? "█" : "░");
        }
        sb.append("] ").append(bars * 10).append("% | ").append(seconds).append("с | ").append(bytes / 1024).append("KB");
        System.out.print("\r" + sb);
    }

    private void createAudioFolder() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("❌ Не удалось создать папку для записей");
        }
    }

    public String getFolder() {
        return AUDIO_FOLDER;
    }
}
