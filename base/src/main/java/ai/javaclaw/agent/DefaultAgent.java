package ai.javaclaw.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

@Component
public class DefaultAgent implements Agent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgent.class);
    private final ChatClient chatClient;

    public DefaultAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String respondTo(String conversationId, String question) {
        var response = chatClient
                .prompt(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
                
        String content = response.getResults()!=null 
            ? response.getResults().stream()
            .map(r -> r.getOutput()!=null ? r.getOutput().getText() : "")
            .filter(s -> s!=null && !s.isBlank())
            .reduce("", (a, b) -> a + "\n" + b)
            : null;
        if (content == null || content.isBlank()) {
            LOGGER.warn("Agent returned empty response for input: {}", question);
        }

        return content;
    }

    @Override
    public Flux<String> respondToStream(String conversationId, String question) {
        return chatClient
            .prompt(question)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .stream()
            .content();
    }

    @Override
    public <T> T prompt(String conversationId, String input, Class<T> result) {
        return chatClient
                .prompt(input)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(result);
    }
}
