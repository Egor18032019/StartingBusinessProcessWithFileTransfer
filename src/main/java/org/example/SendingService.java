package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class SendingService {

    private String SERVER_RUNA_URL = "http://localhost:8080";
    private String START_PROCESS_URL = "/restapi/process/start?name=";

    private String PROCESS_NAME = "4446 Простой процесс с выводом полученного файла";
    private String AUDIO_VAR_NAME = "аудиофайл";

    public SendingService() {
    }

    public SendingService(String SERVER_RUNA_URL, String START_PROCESS_URL, String PROCESS_NAME, String AUDIO_VAR_NAME) {
        this.SERVER_RUNA_URL = SERVER_RUNA_URL;
        this.START_PROCESS_URL = START_PROCESS_URL;
        this.PROCESS_NAME = PROCESS_NAME;
        this.AUDIO_VAR_NAME = AUDIO_VAR_NAME;
    }

    public void launchProcessWithVariables(String jwtToken, File audioFile) throws Exception {
        // 1. Подготавливаем файловую переменную
        byte[] fileBytes = Files.readAllBytes(audioFile.toPath());


        WfeFileVariable fileVar = new WfeFileVariable(
                audioFile.getName(),
                "audio/wav",
                fileBytes,
                audioFile.getName()
        );


        Map<String, Object> variables = new HashMap<>();
        variables.put(AUDIO_VAR_NAME, fileVar);

        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(variables);
        String encodedName = java.net.URLEncoder.encode(PROCESS_NAME, "UTF-8");
        String url = SERVER_RUNA_URL + START_PROCESS_URL + encodedName;


        HttpPut put = new HttpPut(url);
        put.setHeader("Authorization", "Bearer " + jwtToken);
        put.setHeader("Content-Type", "application/json; charset=UTF-8");
        put.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpResponse response = client.execute(put);
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            System.out.println("Статус: " + status);
            System.out.println("Ответ: " + body);
            if (status != 200) {
                throw new RuntimeException("Ошибка: " + body);
            }
        }
    }


}
