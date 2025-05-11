package ru.spbstu.hsai.history.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "history")
public class HistoryDBO {
    @Id
    ObjectId id;
    Long chatId;
    String commandType;
    String currencyCode;
    Map<String,Object> payload;
    LocalDateTime created;
}
