package ai.javaclaw.channels.telegram;


import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.ChannelRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.util.List;
import java.util.ArrayList;

@AutoConfiguration
public class TelegramChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent.channels.telegram", name = "token")
    public TelegramChannel telegramChannel(@Value("${agent.channels.telegram.token:null}") String botToken,
                                           @Value("${agent.channels.telegram.usernames:}") List<String> allowedUsernames,
                                           Agent agent,
                                           ChannelRegistry channelRegistry) {
        return new TelegramChannel(botToken, allowedUsernames, agent, channelRegistry);
    }
}
