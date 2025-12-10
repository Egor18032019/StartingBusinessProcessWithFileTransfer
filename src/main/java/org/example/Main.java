package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


import javax.sound.sampled.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final String RUNA_URL = "http://localhost:8080";
    private static final String LOGIN = "Administrator";
    private static final String PASSWORD = "wf";
    private static final String PROCESS_NAME = "4446 Простой процесс с выводом полученного файла";
    private static final String AUDIO_VAR_NAME = "аудиофайл";
    private static final String AUDIO_FOLDER = "recordings";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in, "UTF-8");
        createAudioFolder();

        System.out.println("🎙️ Аудио рекордер для RunaWFE");
        System.out.println("================================");
        System.out.println("Команды:");
        System.out.println("  Enter - начать/остановить запись");
        System.out.println("  'q'   - выйти из программы");
        System.out.println("  's'   - пропустить запись и запустить последний файл");
        System.out.println("\nНажмите Enter, чтобы начать запись...");

        File audioFile;

        while (true) {
            String input = scanner.nextLine().trim().toLowerCase();

            if ("q".equals(input)) {
                System.out.println("Выход из программы...");
                break;
            }

            if ("s".equals(input)) {
                audioFile = findLatestAudioFile();
                if (audioFile != null) {
                    System.out.println("Найден последний файл: " + audioFile.getName());
                } else {
                    System.out.println("Файлы не найдены в папке " + AUDIO_FOLDER);
                    continue;
                }
            } else {
                audioFile = recordAudioWithStop();
                if (audioFile == null) {
                    System.out.println("Запись отменена или не удалась");
                    continue;
                }
            }

            try {
                System.out.println("\n⏳ Авторизация в RunaWFE...");
                String jwtToken = authenticate();
                System.out.println("✅ Авторизация успешна");

                System.out.println("🚀 Запуск процесса с файлом...");
                launchProcessWithFileBase64(jwtToken, audioFile);
                System.out.println("✅ Процесс успешно запущен!");

                System.out.println("\nНажмите Enter для новой записи, 's' для повторного запуска, 'q' для выхода...");
            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
                e.printStackTrace();
                System.out.println("\nПопробовать еще раз? (Enter - да, 'q' - выход)");
            }
        }

        scanner.close();
        System.out.println("Программа завершена.");
    }

    private static void launchProcessWithFileBase64(String jwtToken, File audioFile) throws Exception {
        String encodedProcessName = URLEncoder.encode(PROCESS_NAME, "UTF-8");

        // Кодируем файл в Base64
        byte[] fileBytes = Files.readAllBytes(audioFile.toPath());
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

        // Формируем URL с параметрами
        String url = String.format("%s/restapi/process/start?name=%s&%s=%s",
                RUNA_URL,
                encodedProcessName,
                URLEncoder.encode(AUDIO_VAR_NAME, "UTF-8"),
                URLEncoder.encode(base64Data, "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPut put = new HttpPut(url);
            put.setHeader("Authorization", "Bearer " + jwtToken);

            HttpResponse response = client.execute(put);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            System.out.println("Статус: " + response.getStatusLine().getStatusCode());
            System.out.println("Ответ: " + responseBody);
        }
    }

    /**
     * Авторизация: получение JWT
     *
     * @return JWT-токен
     */
    private static String authenticate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode credentials = mapper.createObjectNode()
                .put("login", LOGIN)
                .put("password", PASSWORD);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(RUNA_URL + "/restapi/auth/basic");
            post.setEntity(new StringEntity(
                    mapper.writeValueAsString(credentials),
                    ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)
            ));

            HttpResponse response = client.execute(post);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Ошибка авторизации: " + response.getStatusLine());
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim();
        }
    }

    /**
     * Запись аудио
     * @return File аудиофайл
     */
    private static File recordAudioWithStop() throws LineUnavailableException, IOException {

        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new RuntimeException("Микрофон не поддерживается");
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

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
        long startTime = System.currentTimeMillis();
        int progressBars = 0;

        try {
            while (isRecording.get()) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                    totalBytes += count;

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime > 500) {
                        int newProgressBars = (int) (elapsedTime / 1000);
                        if (newProgressBars > progressBars && newProgressBars <= 10) {
                            progressBars = newProgressBars;
                            updateProgress(progressBars, elapsedTime / 1000, totalBytes);
                        }
                        startTime = System.currentTimeMillis();
                    }
                }

                if (System.currentTimeMillis() - startTime > 60000) {
                    System.out.println("\n⏰ Достигнут лимит времени (60 секунд)");
                    isRecording.set(false);
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
    private static File findLatestAudioFile() {
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

    private static void updateProgress(int bars, long seconds, int bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            sb.append(i < bars ? "█" : "░");
        }
        sb.append("] ").append(bars * 10).append("% | ").append(seconds).append("с | ").append(bytes / 1024).append("KB");
        System.out.print("\r" + sb);
    }

    private static void createAudioFolder() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("❌ Не удалось создать папку для записей");
        }
    }

}