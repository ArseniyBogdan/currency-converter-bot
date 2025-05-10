package ru.spbstu.hsai.alert.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "alerts")
public class AlertDBO {
    @Id
    private ObjectId id;
    private Long chatId;
    private String baseCurrency;
    private String targetCurrency;
    private String expr;
}
