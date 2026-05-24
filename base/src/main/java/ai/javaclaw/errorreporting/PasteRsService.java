package ai.javaclaw.errorreporting;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Service
public class PasteRsService implements PasteService{

    private final RestClient restClient;

    public PasteRsService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://paste.rs")
                .build();
    }

    @Override
    public String publish(Throwable t) {
        try{
            String content = ExceptionUtils.getStackTrace(t);
            return publish(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String publish(String content) {
        try {
            return restClient.post().body(content).retrieve().body(String.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
