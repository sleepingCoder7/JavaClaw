package ai.javaclaw.channels.telegram;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Optional.ofNullable;

public class TelegramChannel implements Channel, SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramChannel.class);

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .extensions(List.of(StrikethroughExtension.create()))
            .build();

    private final String botToken;
    private final List<String> allowedUsernames;
    private final TelegramClient telegramClient;
    private final Agent agent;
    private final ChannelRegistry channelRegistry;
    private Long chatId;
    private String botUsername;

    public TelegramChannel(String botToken, List<String> allowedUsernames, Agent agent, ChannelRegistry channelRegistry) {
        this(botToken, allowedUsernames, new OkHttpTelegramClient(botToken), agent, channelRegistry);
    }

    TelegramChannel(String botToken, List<String> allowedUsernames, TelegramClient telegramClient, Agent agent, ChannelRegistry channelRegistry) {
        this.botToken = botToken;
        this.allowedUsernames = normalizeUsernames(allowedUsernames);
        this.telegramClient = telegramClient;
        this.agent = agent;
        this.channelRegistry = channelRegistry;
        channelRegistry.registerChannel(this);
        setBotUsername();
        LOGGER.info("Started Telegram integration");
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    private void setBotUsername() {
        if (this.botUsername == null) {
            try {
                this.botUsername = telegramClient.execute(new GetMe()).getUserName();
            } catch (TelegramApiException e) {
                LOGGER.error("Failed to get bot username", e);
            }
        }
    }

    private String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            LOGGER.warn("Ignoring Telegram message from non-text update");
            return;
        }

        Message requestMessage = update.getMessage();
        // Only require the bot to be mentioned if it's a group chat.
        if (requestMessage.getChat() != null && (requestMessage.getChat().isGroupChat() || requestMessage.getChat().isSuperGroupChat()) && (getBotUsername() != null && !requestMessage.getText().contains("@" + getBotUsername()))) {
            LOGGER.warn("Ignoring Telegram message from group chat without bot mention");
            return;
        }

        String userName = requestMessage.getFrom() == null ? null : requestMessage.getFrom().getUserName();
        if (!isAllowedUser(userName)) {
            LOGGER.warn("Ignoring Telegram message from unauthorized username '{}'. Allowed usernames: {}", userName, allowedUsernames);
            sendMessage("I'm sorry, I don't accept instructions from you.");
            return;
        }

        String messageText = requestMessage.getText();
        this.chatId = requestMessage.getChatId();
        Integer messageThreadId = requestMessage.getMessageThreadId();
        channelRegistry.publishMessageReceivedEvent(new TelegramChannelMessageReceivedEvent(getName(), messageText, chatId, messageThreadId));
        reactToMessage(requestMessage, chatId, messageThreadId);
        Thread typingThread = startTyping(chatId, messageThreadId);
        Flux<String> flux;
        try {
            flux = agent.respondToStream(getConversationId(chatId, messageThreadId), messageText);
        } catch (Exception e) {
            LOGGER.error("Failed to process Telegram message", e);
            sendMessage(chatId, messageThreadId, "I'm sorry, I encountered an error while processing your message.");
            typingThread.interrupt();
            return;
        }
        
        streamToTelegram(chatId, messageThreadId, flux, typingThread);
    }

    public void streamToTelegram(Long chatId, Integer messageThreadId, Flux<String> flux, Thread typingThread) {
        Integer messageId = sendInitialMessage(chatId, messageThreadId, "⏳ Thinking...");
        StringBuilder buffer = new StringBuilder();
        AtomicReference<String> lastSentText = new AtomicReference<>("");
        
        flux
        .onBackpressureLatest()
        .bufferTimeout(20, java.time.Duration.ofSeconds(1))
        .doOnNext(chunks -> {
            for (String chunk : chunks) {
                buffer.append(chunk);
            }

            String text = buffer.toString();

            if (text.length() > 4000) {
                text = text.substring(text.length() - 4000);
            }
            String formatted = convertMarkdownToTelegramHtml(text);
            if(!formatted.equals(lastSentText.get())){
                editMessage(chatId, messageId, formatted);
                lastSentText.set(formatted);
            }
        })
        .doOnError(e -> {
            LOGGER.error("Streaming error", e);
            editMessage(chatId, messageId, "⚠️ Error occurred.");
            typingThread.interrupt();
        })
        .doOnComplete(() -> {
            typingThread.interrupt();   // ✅ STOP typing here
        })
        .subscribe(
            null,
            e -> LOGGER.error("Streaming error", e),
            () -> LOGGER.info("Streaming completed")
        );
    }

    public Integer sendInitialMessage(Long chatId, Integer threadId, String text) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .messageThreadId(threadId)
                .text(text)
                .build();

            return telegramClient.execute(msg).getMessageId();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void editMessage(Long chatId, Integer messageId, String text) {
        try {
            EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();

            telegramClient.execute(edit);

        } catch (Exception e) {
            LOGGER.warn("Edit failed", e);
        }
    }

    private Thread startTyping(long chatId, Integer messageThreadId) {
        Thread thread = new Thread(() -> {
            try{
                while(!Thread.currentThread().isInterrupted()){
                    telegramClient.execute(SendChatAction.builder().chatId(chatId).messageThreadId(messageThreadId).action("typing").build());
                    Thread.sleep(4000);
                }
            }catch(TelegramApiException e){
                LOGGER.warn("Failed to send chat action", e);
            }catch(InterruptedException ignored){
            }
        }, "telegram-typing-thread");
        thread.start();
        return thread;
    }

    private void reactToMessage(Message message, long chatId, Integer messageThreadId) {
        try {
            telegramClient.execute(SetMessageReaction.builder().chatId(chatId).messageId(message.getMessageId()).reactionTypes(List.of(new ReactionTypeEmoji("👀"))).build());
        } catch (TelegramApiException e) {
            LOGGER.warn("Failed to send reaction", e);
        }
    }

    @Override
    public void sendMessage(String message) {
        if (chatId == null) {
            LOGGER.error("No known chatId, cannot send message '{}'", message);
            return;
        }
        sendMessage(chatId, null, message);
    }

    public void sendMessage(long chatId, Integer messageThreadId, String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Empty response from agent.";
        }

        String formattedHtmlMessage = convertMarkdownToTelegramHtml(message);

        if (formattedHtmlMessage == null || formattedHtmlMessage.trim().isEmpty()) {
            formattedHtmlMessage = message;
        }

        SendMessage htmlMessage = SendMessage.builder()
                .chatId(chatId)
                .messageThreadId(messageThreadId)
                .text(formattedHtmlMessage)
                .parseMode(ParseMode.HTML)
                .build();

        try {
            telegramClient.execute(htmlMessage);
        } catch (TelegramApiException e) {
            LOGGER.warn("Failed to send HTML parsed message, falling back to raw text.", e);

            SendMessage fallbackMessage = SendMessage.builder()
                    .chatId(chatId)
                    .messageThreadId(messageThreadId)
                    .text(message)
                    .build();

            try {
                telegramClient.execute(fallbackMessage);
            } catch (TelegramApiException fallbackEx) {
                throw new RuntimeException("Failed to send both HTML and fallback messages", fallbackEx);
            }
        }
    }

    private String convertMarkdownToTelegramHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";

        // 🔥 Replace headings → bold text
        markdown = markdown.replaceAll("(?m)^#{1,6}\\s*(.*)", "<b>$1</b>\n");

        // 🔥 Replace bold (**text**)
        markdown = markdown.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

        // 🔥 Replace italic (*text*)
        markdown = markdown.replaceAll("\\*(.*?)\\*", "<i>$1</i>");

        // 🔥 Replace lists
        markdown = markdown.replaceAll("(?m)^-\\s+(.*)", "• $1");

        // 🔥 Replace line breaks safely
        markdown = markdown.replace("\n", "\n");

        // 🔥 Remove any leftover HTML tags COMPLETELY
        markdown = markdown.replaceAll("<[^>]+>", "");

        return markdown.trim();
    }

    private boolean isAllowedUser(String userName) {
        String normalizedUserName = normalizeUsername(userName);
        return normalizedUserName != null && allowedUsernames.contains(normalizedUserName);
    }

    private List<String> normalizeUsernames(List<String> userNames) {
        if (userNames == null || userNames.isEmpty()) {
            return Collections.emptyList();
        }

        return userNames.stream()
                .map(TelegramChannel::normalizeUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static String normalizeUsername(String userName) {
        if (userName == null) {
            return null;
        }

        String normalizedUserName = userName.trim();
        if (normalizedUserName.startsWith("@")) {
            normalizedUserName = normalizedUserName.substring(1);
        }

        return normalizedUserName.isBlank() ? null : normalizedUserName;
    }

    private String getConversationId(Long chatId, Integer messageThreadId) {
        return "telegram-" + chatId + ofNullable(messageThreadId).map(i -> "-" + i).orElse("");
    }

    static class TelegramChannelMessageReceivedEvent extends ChannelMessageReceivedEvent {

        private final long chatId;
        private final Integer messageThreadId;

        public TelegramChannelMessageReceivedEvent(String channel, String message, long chatId, Integer messageThreadId) {
            super(channel, message);
            this.chatId = chatId;
            this.messageThreadId = messageThreadId;
        }

        public long getChatId() {
            return chatId;
        }

        public Integer getMessageThreadId() {
            return messageThreadId;
        }
    }
}