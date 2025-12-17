package org.example;


import java.io.*;
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
                audioFile = audioService.findLatestAudioFile();
                if (audioFile != null) {
                    System.out.println("Найден последний файл: " + audioFile.getName());
                } else {
                    System.out.println("Файлы не найдены в папке " + audioService.getFolder());
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
                System.out.println("\nПопробовать еще раз? (Enter - да, 'q' - выход)");
            }
        }

        scanner.close();
        System.out.println("Программа завершена.");
        System.exit(0);
    }

}