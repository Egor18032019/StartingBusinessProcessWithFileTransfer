package org.example;


import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class Main {


    public static void main(String[] args) throws Exception {
        AudioService audioService = new AudioService();
        AuthenticateService authenticateService = new AuthenticateService();
        SendingService sendingService = new SendingService();
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.println("🎙️ Аудио рекордер для RunaWFE");
        System.out.println("================================");
        System.out.println("Команды:");
        System.out.println("  Enter - начать/остановить запись");
        System.out.println("  'q' + Enter - выйти из программы");
        System.out.println("  's' + Enter - пропустить запись и запустить последний файл");
        System.out.println("\nНажмите Enter, чтобы начать запись...");

        File audioFile;

        while (true) {
            String input = scanner.nextLine().trim().toLowerCase();

            if ("q".equals(input)) {
                System.out.println("Выход из программы...");
                System.out.print("Очистить все записи в папке '" + audioService.getFolder() + "'? (y/n): ");
                String confirmation = scanner.nextLine().trim().toLowerCase();
                if ("y".equals(confirmation) || "yes".equals(confirmation)) {
                    int deletedFiles = clearRecordingsFolder(audioService.getFolder());
                    System.out.println("🗑️  Удалено " + deletedFiles + " файлов записей.");
                } else {
                    System.out.println("Записи сохранены.");
                }
                break;
            }

            if ("s".equals(input)) {
                audioFile = audioService.findLatestAudioFile();
                if (audioFile != null) {
                    System.out.println("Найден последний файл: " + audioFile.getName());
                } else {
                    System.out.println("Файлы не найдены в папке " + audioService.getFolder());
                    System.out.println(" Нажмите Enter для новой записи, 'q' и + Enter для выхода.");
                    continue;
                }
            } else {
                audioFile = audioService.recordAudioWithStop();
                if (audioFile == null) {
                    System.out.println("Запись отменена или не удалась");
                    continue;
                }
            }

            try {
                System.out.println("\n⏳ Авторизация в RunaWFE...");
                String jwtToken = authenticateService.authenticate();
                System.out.println("✅ Авторизация успешна");

                System.out.println("🚀 Запуск процесса отправки файла...");
                sendingService.launchProcessWithVariables(jwtToken, audioFile);
                System.out.println("✅ Отправка файла успешна!");

                System.out.println("\nНажмите Enter для новой записи, 's' для повторного запуска, 'q' для выхода...");
            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
                e.printStackTrace();
                System.out.println("\nПопробовать еще раз? (Enter - записать новый файл, 's' отправить последний файл, 'q' - выход)");
            }
        }

        scanner.close();
        System.out.println("Программа завершена.");
        System.exit(0);
    }


    /**
     * Очищает все аудиофайлы из папки записей
     *
     * @param folderPath путь к папке с записями
     * @return количество удаленных файлов
     */
    private static int clearRecordingsFolder(String folderPath) {
        int deletedCount = 0;
        try {
            Path recordingsPath = Paths.get(folderPath);

            if (!Files.exists(recordingsPath) || !Files.isDirectory(recordingsPath)) {
                System.out.println("Папка '" + folderPath + "' не существует.");
                return 0;
            }

            // Используем Files.walk для рекурсивного поиска файлов
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(recordingsPath, "*.wav")) {
                for (Path filePath : stream) {
                    try {
                        Files.delete(filePath);
                        System.out.println("Удален: " + filePath.getFileName());
                        deletedCount++;
                    } catch (IOException e) {
                        System.err.println("Не удалось удалить файл " + filePath.getFileName() + ": " + e.getMessage());
                    }
                }
            }

            // Также попробуем удалить .mp3 файлы, если они есть
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(recordingsPath, "*.mp3")) {
                for (Path filePath : stream) {
                    try {
                        Files.delete(filePath);
                        System.out.println("Удален: " + filePath.getFileName());
                        deletedCount++;
                    } catch (IOException e) {
                        System.err.println("Не удалось удалить файл " + filePath.getFileName() + ": " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Ошибка при очистке папки записей: " + e.getMessage());
        }

        return deletedCount;
    }
}