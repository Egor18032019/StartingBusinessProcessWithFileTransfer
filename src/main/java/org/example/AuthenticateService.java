package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

public class AuthenticateService {
    private String SERVER_RUNA_URL = "http://localhost:8080";
    private String AUTH_BASIC_URL = "/restapi/auth/basic";
    private String LOGIN = "Administrator";
    private String PASSWORD = "wf";

    public AuthenticateService() {
    }

    public AuthenticateService(String SERVER_RUNA_URL, String AUTH_BASIC_URL, String LOGIN, String PASSWORD) {
        this.SERVER_RUNA_URL = SERVER_RUNA_URL;
        this.AUTH_BASIC_URL = AUTH_BASIC_URL;
        this.LOGIN = LOGIN;
        this.PASSWORD = PASSWORD;
    }

    /**
     * Авторизация: получение JWT
     *
     * @return JWT-токен
     */
    public String authenticate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode credentials = mapper.createObjectNode()
                .put("login", LOGIN)
                .put("password", PASSWORD);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(SERVER_RUNA_URL + AUTH_BASIC_URL);
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


}
