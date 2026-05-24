package ai.javaclaw.errorreporting;

public interface PasteService {
    String publish(String content);

    String publish(Throwable t);
}
