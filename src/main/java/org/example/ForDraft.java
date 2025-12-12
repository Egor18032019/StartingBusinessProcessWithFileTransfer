package org.example;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ForDraft {

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

                System.out.println("🚀 Запуск процесса и выполнение задачи с файлом...");
                launchProcessAndCompleteTask(jwtToken, audioFile);
                System.out.println("✅ Процесс и задача успешно завершены!");

                System.out.println("\nНажмите Enter для новой записи, 's' для повторного запуска, 'q' для выхода...");
            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
                e.printStackTrace();
                System.out.println("\nПопробовать еще раз? (Enter - да, 'q' - выход)");
                if ("q".equals(scanner.nextLine().trim().toLowerCase())) break;
            }
        }

        scanner.close();
        System.out.println("Программа завершена.");
    }

    // =============== Методы ===============

    private static String authenticate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode credentials = mapper.createObjectNode()
                .put("login", LOGIN)
                .put("password", PASSWORD);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(RUNA_URL + "/restapi/auth/basic");
            post.setEntity(new StringEntity(mapper.writeValueAsString(credentials), ContentType.APPLICATION_JSON));
            HttpResponse response = client.execute(post);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Ошибка авторизации");
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim();
        }
    }

    public static void launchProcessAndCompleteTask(String jwtToken, File audioFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // === ШАГ 1: Запуск процесса БЕЗ переменных ===
        String encodedName = java.net.URLEncoder.encode(PROCESS_NAME, "UTF-8");
        String startUrl = RUNA_URL + "/restapi/process/start?name=" + encodedName;

        HttpPut startPut = new HttpPut(startUrl);
        startPut.setHeader("Authorization", "Bearer " + jwtToken);

        String processId;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpResponse response = client.execute(startPut);
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Ошибка запуска: " + body);
            }
            processId = body.trim();
            System.out.println("✅ Процесс запущен, ID: " + processId);
        }

        // === ШАГ 2: Выполнить задачу с файлом ===
        byte[] fileBytes = Files.readAllBytes(audioFile.toPath());
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

        ObjectNode fileValue = mapper.createObjectNode()
                .put("name", audioFile.getName())
                .put("contentType", "audio/wav")
                .put("data", base64Data)
                .put("stringValue", audioFile.getName());

        ObjectNode payload = mapper.createObjectNode();
        payload.set(AUDIO_VAR_NAME, fileValue);

        String jsonBody = mapper.writeValueAsString(payload);
        System.out.println("📤 Отправка файла в задачу...");

        HttpPost completePost = new HttpPost(RUNA_URL + "/restapi/task/" + processId + "/complete");
        completePost.setHeader("Authorization", "Bearer " + jwtToken);
        completePost.setHeader("Content-Type", "application/json; charset=UTF-8");
        completePost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpResponse response = client.execute(completePost);
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Ошибка выполнения задачи: " + body);
            }
            System.out.println("✅ Задача успешно выполнена!");
        }
    }

    // =============== Вспомогательные методы записи ===============

    private static File recordAudioWithStop() throws LineUnavailableException, IOException {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) throw new RuntimeException("Микрофон не поддерживается");

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
            } catch (IOException ignored) {}
        });
        stopThread.setDaemon(true);
        stopThread.start();

        while (isRecording.get()) {
            int count = line.read(buffer, 0, buffer.length);
            if (count > 0) out.write(buffer, 0, count);
        }

        line.stop();
        line.close();
        stopThread.interrupt();

        if (out.size() == 0) return null;

        String timestamp = DATE_FORMAT.format(new Date());
        String fileName = "audio_" + timestamp + ".wav";
        File file = new File(AUDIO_FOLDER, fileName);

        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(out.toByteArray()), format, out.size() / format.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }

        System.out.printf("✅ Запись сохранена: %s (%.1f KB)\n", fileName, file.length() / 1024.0);
        return file;
    }

    private static File findLatestAudioFile() {
        File folder = new File(AUDIO_FOLDER);
        if (!folder.exists()) return null;
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
        if (files == null || files.length == 0) return null;
        File latest = files[0];
        for (File f : files) if (f.lastModified() > latest.lastModified()) latest = f;
        return latest;
    }

    private static void createAudioFolder() {
        new File(AUDIO_FOLDER).mkdirs();
    }
}