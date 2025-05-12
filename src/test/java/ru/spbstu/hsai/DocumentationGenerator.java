package ru.spbstu.hsai;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DocumentationGenerator {

    public static void generateCommandDoc(String command, String description, String example) {
        try {
            Path path = Path.of("build/docs/telegram-commands.adoc");
            String content = """
                == %s
                
                *Описание*: %s
                
                *Пример использования*:
                ```
                %s
                ```
                
                """.formatted(command, description, example);

            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write(content);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate documentation", e);
        }
    }
}